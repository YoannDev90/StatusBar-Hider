package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Keyguard visibility, the accessibility service behind it and notification privacy. */
@Composable
fun LockBehaviorScreen(vm: StatusViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup {
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
				SettingSwitch(R.drawable.ic_notifications_off, R.string.switch_hide_notifs_on_lock, prefs.hideNotifsOnLock) {
					vm.updatePrefs { copy(hideNotifsOnLock = it) }
				}
			}
		}
	}
}
