package dev.yoanndev90.statusbarhider

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.ui.shell.MainScreen
import dev.yoanndev90.statusbarhider.ui.theme.StatusBarHiderTheme

class MainActivity : ComponentActivity() {
	companion object {
		private const val REQ_NOTIFICATIONS = 1003

		/**
		 * Disables this activity's launcher component so the app disappears from
		 * the app drawer. Re-access via `mise run show-in-drawer`, Settings >
		 * Apps > StatusBar Hider, or [ShowInDrawerReceiver].
		 */
		fun hideFromLauncher(context: Context) {
			val app = context.applicationContext
			app.packageManager.setComponentEnabledSetting(
				ComponentName(app, MainActivity::class.java),
				PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
				PackageManager.DONT_KILL_APP
			)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		// Persisted lines (from a previous run, or a boot auto-hide) are shown too.
		LogStore.ensureLoaded(this)
		ShizukuRepository.getInstance().start()
		setContent {
			StatusBarHiderTheme {
				MainScreen()
			}
		}
		requestNotificationPermissionIfNeeded()
	}

	override fun onDestroy() {
		// The listeners belong to the process, not the Activity: only detach when
		// the app is really going away, not on a configuration change.
		if (isFinishing) ShizukuRepository.getInstance().stop()
		super.onDestroy()
	}

	/**
	 * The overlay runs as a foreground service: without POST_NOTIFICATIONS its
	 * "custom bar active" notification is silently suppressed on API 33+.
	 * A denial is fine (the service still runs), so no result handling.
	 */
	private fun requestNotificationPermissionIfNeeded() {
		if (Build.VERSION.SDK_INT < 33) return
		if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
		requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
	}
}
