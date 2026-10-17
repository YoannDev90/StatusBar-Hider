package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Polling, touch handling, boot behavior and burn-in protection. */
@Composable
fun GeneralScreen(vm: StatusViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup {
				SettingSwitch(
					icon = R.drawable.ic_touch_app,
					label = R.string.switch_touchable_bar,
					checked = prefs.interactive,
					subtitle = R.string.switch_touchable_bar_sub
				) {
					vm.updatePrefs { copy(interactive = it) }
				}
				SettingSwitch(R.drawable.ic_power_settings_new, R.string.switch_auto_hide_boot, prefs.autoHideBoot) {
					vm.updatePrefs { copy(autoHideBoot = it) }
				}
				SettingSwitch(R.drawable.ic_play_arrow, R.string.switch_auto_start_bar, prefs.autoStartBar) {
					vm.updatePrefs { copy(autoStartBar = it) }
				}
				SettingSlider(
					icon = R.drawable.ic_timer,
					label = R.string.slider_update_interval,
					value = prefs.updateIntervalSec,
					min = 5,
					max = 60,
					suffix = R.string.suffix_seconds,
					subtitle = R.string.slider_update_interval_sub
				) {
					vm.updatePrefs { copy(updateIntervalSec = it) }
				}
				SettingSlider(
					icon = R.drawable.ic_sync,
					label = R.string.slider_burn_in_shift,
					value = prefs.burnInMin,
					min = 0,
					max = 30,
					suffix = R.string.suffix_minutes,
					subtitle = R.string.slider_burn_in_shift_sub
				) {
					vm.updatePrefs { copy(burnInMin = it) }
				}
			}
		}
	}
}
