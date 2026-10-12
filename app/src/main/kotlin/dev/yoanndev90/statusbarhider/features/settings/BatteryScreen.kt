package dev.yoanndev90.statusbarhider.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.shared.HandlePrefsEvents
import dev.yoanndev90.statusbarhider.features.status.StatusViewModel
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import dev.yoanndev90.statusbarhider.ui.components.SettingsScreen

/** Battery level, percentage and icon. */
@Composable
fun BatteryScreen(vm: StatusViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup {
				SettingSwitch(R.drawable.ic_battery_std, R.string.switch_battery, prefs.showBattery) { vm.updatePrefs { copy(showBattery = it) } }
				SettingSwitch(R.drawable.ic_percent, R.string.switch_battery_pct, prefs.showBatteryPct) { vm.updatePrefs { copy(showBatteryPct = it) } }
				SettingSwitch(R.drawable.ic_battery_full, R.string.switch_battery_icon, prefs.showBatteryIcon) {
					vm.updatePrefs { copy(showBatteryIcon = it) }
				}
			}
		}
	}
}
