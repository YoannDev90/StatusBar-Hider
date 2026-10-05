package dev.yoanndev90.statusbarhider.features.bar.sections

import androidx.compose.runtime.Composable
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Polling, touch handling and burn-in protection. */
@Composable
fun BehaviorSection(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
	SettingGroup(R.string.group_behavior) {
		SettingSwitch(R.drawable.ic_data_usage, R.string.switch_bandwidth, prefs.showBandwidth) { vm.updatePrefs { copy(showBandwidth = it) } }
		SettingSlider(R.drawable.ic_timer, R.string.slider_update_interval, prefs.updateIntervalSec, 5, 60, R.string.suffix_seconds) {
			vm.updatePrefs { copy(updateIntervalSec = it) }
		}
		SettingSwitch(R.drawable.ic_swap, R.string.switch_merged_speed, prefs.bandwidthMerged) { vm.updatePrefs { copy(bandwidthMerged = it) } }
		SettingSwitch(R.drawable.ic_touch_app, R.string.switch_touchable_bar, prefs.interactive) { vm.updatePrefs { copy(interactive = it) } }
		SettingSwitch(R.drawable.ic_power_settings_new, R.string.switch_auto_hide_boot, prefs.autoHideBoot) { vm.updatePrefs { copy(autoHideBoot = it) } }
		SettingSwitch(R.drawable.ic_play_arrow, R.string.switch_auto_start_bar, prefs.autoStartBar) { vm.updatePrefs { copy(autoStartBar = it) } }
		SettingSlider(R.drawable.ic_sync, R.string.slider_burn_in_shift, prefs.burnInMin, 0, 30, R.string.suffix_minutes) {
			vm.updatePrefs { copy(burnInMin = it) }
		}
	}
}
