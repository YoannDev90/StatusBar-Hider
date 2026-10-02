package dev.yoanndev90.statusbarhider.ui.sections

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.ui.MainViewModel
import dev.yoanndev90.statusbarhider.ui.components.SectionHeader
import dev.yoanndev90.statusbarhider.ui.components.SettingAction
import dev.yoanndev90.statusbarhider.ui.components.SettingSlider
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverlayTogglesSection(
	vm: MainViewModel,
	onCalendarToggle: (Boolean) -> Unit
) {
	val state by vm.uiState.collectAsStateWithLifecycle()
	val prefs = state.prefs
	val context = LocalContext.current

	SectionHeader("CUSTOM BAR")
	SettingSwitch("Show seconds (extra battery)", prefs.showSeconds) {
		vm.updatePrefs { copy(showSeconds = it) }
	}
	SettingSwitch("Battery", prefs.showBattery) { vm.updatePrefs { copy(showBattery = it) } }
	SettingSwitch("Battery %", prefs.showBatteryPct) { vm.updatePrefs { copy(showBatteryPct = it) } }
	SettingSwitch("Battery icon", prefs.showBatteryIcon) { vm.updatePrefs { copy(showBatteryIcon = it) } }
	SettingSwitch("Date", prefs.showDate) { vm.updatePrefs { copy(showDate = it) } }

	var dateFormat by remember(prefs.dateFormat) { mutableStateOf(prefs.dateFormat) }
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		OutlinedTextField(
			value = dateFormat,
			onValueChange = { dateFormat = it },
			label = { Text("Date format (EEE dd MMM)") },
			singleLine = true,
			modifier = Modifier.weight(1f).padding(end = 8.dp)
		)
		Button(
			onClick = {
				val fmt = dateFormat.ifEmpty { "EEE dd MMM" }
				try {
					SimpleDateFormat(fmt, Locale.getDefault()).format(Date())
				} catch (_: Exception) {
					Toast.makeText(context, "Invalid date format", Toast.LENGTH_SHORT).show()
					return@Button
				}
				vm.updatePrefs { copy(dateFormat = fmt) }
				vm.appendLog("Date format: $fmt")
			}
		) {
			Text("Set")
		}
	}

	SettingSwitch("Notification icons", prefs.showNotifs) { vm.updatePrefs { copy(showNotifs = it) } }
	SettingAction("Enable notification access") {
		try {
			context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
		} catch (_: Exception) {
			Toast.makeText(context, "Cannot open notification settings", Toast.LENGTH_SHORT).show()
		}
	}
	SettingSlider("Max notification icons", prefs.maxNotifs, 1, 8) {
		vm.updatePrefs { copy(maxNotifs = it) }
	}
	SettingSwitch("WiFi", prefs.showWifi) { vm.updatePrefs { copy(showWifi = it) } }
	SettingSwitch("Mobile data", prefs.showMobileData) { vm.updatePrefs { copy(showMobileData = it) } }
	SettingSwitch("Bluetooth", prefs.showBluetooth) { vm.updatePrefs { copy(showBluetooth = it) } }
	SettingSwitch("Airplane mode", prefs.showAirplane) { vm.updatePrefs { copy(showAirplane = it) } }
	SettingSwitch("USB", prefs.showUsb) { vm.updatePrefs { copy(showUsb = it) } }
	SettingSwitch("Next alarm", prefs.showAlarm) { vm.updatePrefs { copy(showAlarm = it) } }
	SettingSwitch("Next calendar event (asks calendar permission)", prefs.showCalendar, onCalendarToggle)
	SettingSwitch("Now playing (needs notification access)", prefs.showMedia) {
		vm.updatePrefs { copy(showMedia = it) }
	}
	SettingSwitch("NFC indicator", prefs.showNfc) { vm.updatePrefs { copy(showNfc = it) } }
	SettingSwitch("GPS indicator", prefs.showGps) { vm.updatePrefs { copy(showGps = it) } }
	SettingSwitch("Do Not Disturb", prefs.showDnd) { vm.updatePrefs { copy(showDnd = it) } }
	SettingSwitch("Data saver", prefs.showDataSaver) { vm.updatePrefs { copy(showDataSaver = it) } }
	SettingSwitch("Auto-rotate", prefs.showRotate) { vm.updatePrefs { copy(showRotate = it) } }
	SettingSwitch("Flashlight (shown while on)", prefs.showTorch) {
		vm.updatePrefs { copy(showTorch = it) }
	}
	SettingSwitch("Bandwidth (1s polling)", prefs.showBandwidth) {
		vm.updatePrefs { copy(showBandwidth = it) }
	}
	SettingSwitch("Merged up/down speed", prefs.bandwidthMerged) {
		vm.updatePrefs { copy(bandwidthMerged = it) }
	}
	SettingSwitch("Touchable bar (tap clock/date, may block swipe)", prefs.interactive) {
		vm.updatePrefs { copy(interactive = it) }
	}
	SettingSlider("Burn-in shift: every", prefs.burnInMin, 0, 30, " min (0 = off)") {
		vm.updatePrefs { copy(burnInMin = it) }
	}
}
