package dev.yoanndev90.statusbarhider.features.bar.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.LockScreenMode
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
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
