package dev.yoanndev90.statusbarhider

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.watcher.SystemUiWatcher
import dev.yoanndev90.statusbarhider.ui.shell.MainScreen
import dev.yoanndev90.statusbarhider.ui.theme.StatusBarHiderTheme

class MainActivity : ComponentActivity() {
	companion object {
		private const val TAG = "MainActivity"
	}

	private val notificationsPermission =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
			// A denial is fine (the service still runs); the grant only stops
			// this from being asked again on the next launch.
			if (!granted) Log.d(TAG, "POST_NOTIFICATIONS denied - FGS notification suppressed")
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		// Persisted lines (from a previous run, or a boot auto-hide) are shown too.
		LogStore.ensureLoadedAsync(this)
		SystemUiWatcher.start(this, this)
		setContent {
			StatusBarHiderTheme {
				MainScreen()
			}
		}
		requestNotificationPermissionIfNeeded()
	}

	override fun onDestroy() {
		// The watcher belongs to the process, not the Activity: only detach when
		// the app is really going away, not on a configuration change.
		if (isFinishing) {
			SystemUiWatcher.stop(this)
		}
		super.onDestroy()
	}

	/**
	 * The overlay runs as a foreground service: without POST_NOTIFICATIONS its
	 * "custom bar active" notification is silently suppressed on API 33+.
	 * The result arrives through [notificationsPermission] - the old
	 * `requestPermissions` overload had no callback here, so a denial was
	 * thrown away and the dialog reappeared on every launch.
	 */
	private fun requestNotificationPermissionIfNeeded() {
		if (Build.VERSION.SDK_INT < 33) return
		if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
		notificationsPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
	}
}
