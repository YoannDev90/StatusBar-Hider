package dev.yoanndev90.statusbarhider.ui.screens.settings

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.permission.PermissionAccess
import dev.yoanndev90.statusbarhider.core.permission.PermissionGrants
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingStatusRow
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen
import dev.yoanndev90.statusbarhider.ui.screens.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.state.HandlePrefsEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Debug > Permissions: every grant the app relies on, allowed or blocked, with a shortcut to fix it. */
@Composable
fun DebugPermissionsScreen(vm: StatusViewModel) {
	val context = LocalContext.current
	val shizuku by vm.shizuku.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)
	// Bumped on every resume: the grants happen in other screens, so the rows
	// are re-read when the user comes back. The tick is the key of that read.
	var resumeTick by remember { mutableStateOf(0) }
	LifecycleResumeEffect(Unit) {
		resumeTick++
		onPauseOrDispose { }
	}

	val calendarLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
	val notificationsLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
	val bluetoothLauncher =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
	// The permission rows are binder calls (overlay, listener, AppOps, grants):
	// read them once per resume on IO instead of on every recomposition. Values
	// from the previous read stay on screen while a refresh is in flight.
	val grants by produceState<PermissionGrants?>(null, resumeTick) {
		value = withContext(Dispatchers.IO) { PermissionAccess.grants(context) }
	}

	SettingsScreen {
		item {
			SettingGroup {
				SettingStatusRow(
					label = R.string.perm_shizuku,
					granted = shizuku == ShizukuState.READY
				) { vm.requestShizukuPermission() }
				val loaded = grants ?: return@SettingGroup
				SettingStatusRow(
					label = R.string.perm_overlay,
					granted = loaded.overlay
				) { PermissionAccess.openSpecialAccess(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
				SettingStatusRow(
					label = R.string.perm_notif_access,
					granted = loaded.notifListener
				) { PermissionAccess.openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
				SettingStatusRow(
					label = R.string.perm_calendar,
					granted = loaded.calendar
				) { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
				if (Build.VERSION.SDK_INT >= 33) {
					SettingStatusRow(
						label = R.string.perm_post_notifs,
						granted = loaded.postNotifs
					) { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
				}
				if (Build.VERSION.SDK_INT >= 31) {
					SettingStatusRow(
						label = R.string.perm_bluetooth,
						granted = loaded.bluetooth
					) { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
				}
				SettingStatusRow(
					label = R.string.perm_usage,
					granted = loaded.usage
				) { PermissionAccess.openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) }
				SettingStatusRow(
					label = R.string.perm_accessibility,
					granted = loaded.lockOverlay
				) { PermissionAccess.openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
			}
		}
	}
}
