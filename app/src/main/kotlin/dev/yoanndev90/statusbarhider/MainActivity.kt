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

	private val bluetoothPermission =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
			// Same contract as notifications: a denial keeps the generic
			// Bluetooth glyph, the grant enables per-device icons.
			if (!granted) Log.d(TAG, "BLUETOOTH_CONNECT denied - generic Bluetooth icon kept")
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
		requestBluetoothPermissionIfNeeded()
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

	/**
	 * Per-device Bluetooth icons need the connected device list, which
	 * Android 12 gates behind BLUETOOTH_CONNECT. A denial is harmless: the
	 * overlay keeps the generic Bluetooth glyph.
	 */
	private fun requestBluetoothPermissionIfNeeded() {
		if (Build.VERSION.SDK_INT < 31) return
		if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) return
		bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
	}
}
