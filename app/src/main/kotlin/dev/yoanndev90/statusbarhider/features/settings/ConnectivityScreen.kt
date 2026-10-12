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

/** Connectivity indicators: radios, radios-adjacent toggles, system states and bandwidth. */
@Composable
fun ConnectivityScreen(vm: StatusViewModel) {
	val prefs by vm.prefs.collectAsStateWithLifecycle()
	HandlePrefsEvents(vm)

	SettingsScreen {
		item {
			SettingGroup {
				SettingSwitch(R.drawable.ic_wifi, R.string.switch_wifi, prefs.showWifi) { vm.updatePrefs { copy(showWifi = it) } }
				SettingSwitch(R.drawable.ic_signal, R.string.switch_mobile_data, prefs.showMobileData) { vm.updatePrefs { copy(showMobileData = it) } }
				SettingSwitch(R.drawable.ic_bt, R.string.switch_bluetooth, prefs.showBluetooth) { vm.updatePrefs { copy(showBluetooth = it) } }
				SettingSwitch(R.drawable.ic_plane, R.string.switch_airplane, prefs.showAirplane) { vm.updatePrefs { copy(showAirplane = it) } }
				SettingSwitch(R.drawable.ic_usb, R.string.switch_usb, prefs.showUsb) { vm.updatePrefs { copy(showUsb = it) } }
				SettingSwitch(R.drawable.ic_nfc, R.string.switch_nfc, prefs.showNfc) { vm.updatePrefs { copy(showNfc = it) } }
				SettingSwitch(R.drawable.ic_gps, R.string.switch_gps, prefs.showGps) { vm.updatePrefs { copy(showGps = it) } }
				SettingSwitch(R.drawable.ic_dnd, R.string.switch_dnd, prefs.showDnd) { vm.updatePrefs { copy(showDnd = it) } }
				SettingSwitch(R.drawable.ic_data_saver, R.string.switch_data_saver, prefs.showDataSaver) { vm.updatePrefs { copy(showDataSaver = it) } }
				SettingSwitch(R.drawable.ic_rotation, R.string.switch_auto_rotate, prefs.showRotate) { vm.updatePrefs { copy(showRotate = it) } }
				SettingSwitch(R.drawable.ic_torch, R.string.switch_flashlight, prefs.showTorch) { vm.updatePrefs { copy(showTorch = it) } }
				SettingSwitch(R.drawable.ic_vpn, R.string.switch_vpn, prefs.showVpn) { vm.updatePrefs { copy(showVpn = it) } }
				SettingSwitch(R.drawable.ic_hotspot, R.string.switch_hotspot, prefs.showHotspot) { vm.updatePrefs { copy(showHotspot = it) } }
				SettingSwitch(R.drawable.ic_data_usage, R.string.switch_bandwidth, prefs.showBandwidth) { vm.updatePrefs { copy(showBandwidth = it) } }
				SettingSwitch(R.drawable.ic_swap, R.string.switch_merged_speed, prefs.bandwidthMerged) { vm.updatePrefs { copy(bandwidthMerged = it) } }
			}
		}
	}
}
