package dev.yoanndev90.statusbarhider

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.yoanndev90.statusbarhider.ui.MainScreen
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.theme.StatusBarHiderTheme

class MainActivity : ComponentActivity() {
	companion object {
		private const val REQ_SHIZUKU = 1001
		private const val REQ_CALENDAR = 1002
	}

	private val vm: MainViewModel by viewModels()

	private var pendingCalendarToggle = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			StatusBarHiderTheme {
				MainScreen(
					vm = vm,
					shizukuRequestCode = REQ_SHIZUKU,
					onCalendarToggle = ::onCalendarToggle,
					onShowBar = ::onShowBar,
					onHideFromLauncher = ::onHideFromLauncher
				)
			}
		}

		vm.refreshShizuku()
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		// Shizuku results are delivered via ShizukuRepository's listener, not here.
		if (requestCode == REQ_CALENDAR && pendingCalendarToggle) {
			pendingCalendarToggle = false
			val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
			if (granted) {
				vm.updatePrefs { copy(showCalendar = true) }
			} else {
				Toast.makeText(this, getString(R.string.toast_calendar_permission_denied), Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun onCalendarToggle(checked: Boolean) {
		// Calendar needs a runtime permission: only save the toggle once granted.
		// The switch state comes from prefs, so a denial leaves it unchecked.
		if (checked && !hasCalendarPermission()) {
			pendingCalendarToggle = true
			requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
		} else {
			vm.updatePrefs { copy(showCalendar = checked) }
		}
	}

	private fun onShowBar() {
		if (!Settings.canDrawOverlays(this)) {
			Toast.makeText(this, getString(R.string.toast_grant_overlay_permission), Toast.LENGTH_LONG).show()
			startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
			return
		}
		vm.setOverlayEnabled(true)
	}

	private fun onHideFromLauncher() {
		val cn = ComponentName(this, MainActivity::class.java)
		packageManager.setComponentEnabledSetting(
			cn,
			PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
			PackageManager.DONT_KILL_APP
		)
		Toast
			.makeText(
				this,
				getString(R.string.toast_hidden_from_launcher),
				Toast.LENGTH_LONG
			).show()
	}

	private fun hasCalendarPermission(): Boolean =
		checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
