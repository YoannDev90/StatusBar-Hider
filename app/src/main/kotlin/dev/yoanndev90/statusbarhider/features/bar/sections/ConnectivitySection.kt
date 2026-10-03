package dev.yoanndev90.statusbarhider.features.bar.sections

import androidx.compose.runtime.Composable
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.bar.BarViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch

/** Connectivity indicators: radios, radios-adjacent toggles and system states. */
@Composable
fun ConnectivitySection(
	prefs: OverlayPrefs,
	vm: BarViewModel
) {
	SettingGroup(R.string.group_connectivity) {
		SettingSwitch(R.string.switch_wifi, prefs.showWifi) { vm.updatePrefs { copy(showWifi = it) } }
		SettingSwitch(R.string.switch_mobile_data, prefs.showMobileData) { vm.updatePrefs { copy(showMobileData = it) } }
		SettingSwitch(R.string.switch_bluetooth, prefs.showBluetooth) { vm.updatePrefs { copy(showBluetooth = it) } }
		SettingSwitch(R.string.switch_airplane, prefs.showAirplane) { vm.updatePrefs { copy(showAirplane = it) } }
		SettingSwitch(R.string.switch_usb, prefs.showUsb) { vm.updatePrefs { copy(showUsb = it) } }
		SettingSwitch(R.string.switch_nfc, prefs.showNfc) { vm.updatePrefs { copy(showNfc = it) } }
		SettingSwitch(R.string.switch_gps, prefs.showGps) { vm.updatePrefs { copy(showGps = it) } }
		SettingSwitch(R.string.switch_dnd, prefs.showDnd) { vm.updatePrefs { copy(showDnd = it) } }
		SettingSwitch(R.string.switch_data_saver, prefs.showDataSaver) { vm.updatePrefs { copy(showDataSaver = it) } }
		SettingSwitch(R.string.switch_auto_rotate, prefs.showRotate) { vm.updatePrefs { copy(showRotate = it) } }
		SettingSwitch(R.string.switch_flashlight, prefs.showTorch) { vm.updatePrefs { copy(showTorch = it) } }
	}
}
