package dev.yoanndev90.statusbarhider.features.bar.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.LockScreenMode
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingRadioRow
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Keyguard visibility, the layout used while locked and the notification privacy filter. */
@Composable
fun LockScreenSection(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
	SettingGroup(R.string.group_lock_screen) {
		SettingSwitch(R.drawable.ic_lock, R.string.switch_show_on_lock_screen, prefs.showOnLockScreen) {
			vm.updatePrefs { copy(showOnLockScreen = it) }
		}
		if (prefs.showOnLockScreen) {
			SettingAction(R.drawable.ic_key, R.string.action_enable_lock_overlay) {
				vm.enableLockScreenOverlay()
			}
		}
		// Reachable even with the toggle off: the service can still be on from
		// an earlier session, and the app must not be why it stays that way.
		SettingAction(R.drawable.ic_close, R.string.action_disable_lock_overlay) {
			vm.disableLockScreenOverlay()
		}
		SettingRadioRow(
			icon = R.drawable.ic_visibility,
			options =
				listOf(
					stringResource(R.string.lock_mode_full),
					stringResource(R.string.lock_mode_clock_only)
				),
			selected = if (prefs.lockScreenMode == LockScreenMode.CLOCK_ONLY) 1 else 0,
			onSelect = { index ->
				val mode = if (index == 1) LockScreenMode.CLOCK_ONLY else LockScreenMode.FULL
				vm.updatePrefs { copy(lockScreenMode = mode) }
			}
		)
		SettingSwitch(R.drawable.ic_notifications_off, R.string.switch_hide_notifs_on_lock, prefs.hideNotifsOnLock) {
			vm.updatePrefs { copy(hideNotifsOnLock = it) }
		}
	}
}
