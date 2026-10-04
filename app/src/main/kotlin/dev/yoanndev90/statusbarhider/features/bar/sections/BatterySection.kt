package dev.yoanndev90.statusbarhider.features.bar.sections

import androidx.compose.runtime.Composable
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.bar.BarViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Battery level, percentage and icon. */
@Composable
fun BatterySection(
	prefs: OverlayPrefs,
	vm: BarViewModel
) {
	SettingGroup(R.string.group_battery) {
		SettingSwitch(R.drawable.ic_battery_std, R.string.switch_battery, prefs.showBattery) { vm.updatePrefs { copy(showBattery = it) } }
		SettingSwitch(R.drawable.ic_percent, R.string.switch_battery_pct, prefs.showBatteryPct) { vm.updatePrefs { copy(showBatteryPct = it) } }
		SettingSwitch(R.drawable.ic_battery_full, R.string.switch_battery_icon, prefs.showBatteryIcon) {
			vm.updatePrefs { copy(showBatteryIcon = it) }
		}
	}
}
