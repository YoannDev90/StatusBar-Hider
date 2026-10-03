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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yoanndev90.statusbarhider.R
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
	val invalidDateFormatToast = stringResource(R.string.toast_invalid_date_format)
	val cannotOpenNotifSettingsToast = stringResource(R.string.toast_cannot_open_notification_settings)
	val dateLogTemplate = stringResource(R.string.log_date_format)

	SectionHeader(stringResource(R.string.section_custom_bar))
	SettingSwitch(stringResource(R.string.switch_show_seconds), prefs.showSeconds) {
		vm.updatePrefs { copy(showSeconds = it) }
	}
	SettingSwitch(stringResource(R.string.switch_use_24h), prefs.use24h) {
		vm.updatePrefs { copy(use24h = it) }
	}
	SettingSwitch(stringResource(R.string.switch_show_on_lock_screen), prefs.showOnLockScreen) {
		vm.updatePrefs { copy(showOnLockScreen = it) }
	}
	SettingSwitch(stringResource(R.string.switch_battery), prefs.showBattery) {
		vm.updatePrefs { copy(showBattery = it) }
	}
	SettingSwitch(stringResource(R.string.switch_battery_pct), prefs.showBatteryPct) {
		vm.updatePrefs { copy(showBatteryPct = it) }
	}
	SettingSwitch(stringResource(R.string.switch_battery_icon), prefs.showBatteryIcon) {
		vm.updatePrefs { copy(showBatteryIcon = it) }
	}
	SettingSwitch(stringResource(R.string.switch_date), prefs.showDate) {
		vm.updatePrefs { copy(showDate = it) }
	}

	var dateFormat by remember(prefs.dateFormat) { mutableStateOf(prefs.dateFormat) }
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		OutlinedTextField(
			value = dateFormat,
			onValueChange = { dateFormat = it },
			label = { Text(stringResource(R.string.label_date_format)) },
			singleLine = true,
			modifier = Modifier.weight(1f).padding(end = 8.dp)
		)
		Button(
			onClick = {
				val fmt = dateFormat.ifEmpty { "EEE dd MMM" }
				try {
					SimpleDateFormat(fmt, Locale.getDefault()).format(Date())
				} catch (_: Exception) {
					Toast.makeText(context, invalidDateFormatToast, Toast.LENGTH_SHORT).show()
					return@Button
				}
				vm.updatePrefs { copy(dateFormat = fmt) }
				vm.appendLog(dateLogTemplate.format(fmt))
			}
		) {
			Text(stringResource(R.string.action_set))
		}
	}

	SettingSwitch(stringResource(R.string.switch_notification_icons), prefs.showNotifs) {
		vm.updatePrefs { copy(showNotifs = it) }
	}
	SettingAction(stringResource(R.string.action_enable_notification_access)) {
		try {
			context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
		} catch (_: Exception) {
			Toast
				.makeText(context, cannotOpenNotifSettingsToast, Toast.LENGTH_SHORT)
				.show()
		}
	}
	SettingSlider(stringResource(R.string.slider_max_notification_icons), prefs.maxNotifs, 1, 8) {
		vm.updatePrefs { copy(maxNotifs = it) }
	}
	SettingSwitch(stringResource(R.string.switch_wifi), prefs.showWifi) {
		vm.updatePrefs { copy(showWifi = it) }
	}
	SettingSwitch(stringResource(R.string.switch_mobile_data), prefs.showMobileData) {
		vm.updatePrefs { copy(showMobileData = it) }
	}
	SettingSwitch(stringResource(R.string.switch_bluetooth), prefs.showBluetooth) {
		vm.updatePrefs { copy(showBluetooth = it) }
	}
	SettingSwitch(stringResource(R.string.switch_airplane), prefs.showAirplane) {
		vm.updatePrefs { copy(showAirplane = it) }
	}
	SettingSwitch(stringResource(R.string.switch_usb), prefs.showUsb) {
		vm.updatePrefs { copy(showUsb = it) }
	}
	SettingSwitch(stringResource(R.string.switch_next_alarm), prefs.showAlarm) {
		vm.updatePrefs { copy(showAlarm = it) }
	}
	SettingSwitch(
		stringResource(R.string.switch_next_calendar_event),
		prefs.showCalendar,
		onCalendarToggle
	)
	SettingSwitch(stringResource(R.string.switch_now_playing), prefs.showMedia) {
		vm.updatePrefs { copy(showMedia = it) }
	}
	SettingSwitch(stringResource(R.string.switch_nfc), prefs.showNfc) {
		vm.updatePrefs { copy(showNfc = it) }
	}
	SettingSwitch(stringResource(R.string.switch_gps), prefs.showGps) {
		vm.updatePrefs { copy(showGps = it) }
	}
	SettingSwitch(stringResource(R.string.switch_dnd), prefs.showDnd) {
		vm.updatePrefs { copy(showDnd = it) }
	}
	SettingSwitch(stringResource(R.string.switch_data_saver), prefs.showDataSaver) {
		vm.updatePrefs { copy(showDataSaver = it) }
	}
	SettingSwitch(stringResource(R.string.switch_auto_rotate), prefs.showRotate) {
		vm.updatePrefs { copy(showRotate = it) }
	}
	SettingSwitch(stringResource(R.string.switch_flashlight), prefs.showTorch) {
		vm.updatePrefs { copy(showTorch = it) }
	}
	SettingSwitch(stringResource(R.string.switch_bandwidth), prefs.showBandwidth) {
		vm.updatePrefs { copy(showBandwidth = it) }
	}
	SettingSlider(
		stringResource(R.string.slider_update_interval),
		prefs.updateIntervalSec,
		5,
		60,
		stringResource(R.string.suffix_seconds)
	) {
		vm.updatePrefs { copy(updateIntervalSec = it) }
	}
	SettingSwitch(stringResource(R.string.switch_merged_speed), prefs.bandwidthMerged) {
		vm.updatePrefs { copy(bandwidthMerged = it) }
	}
	SettingSwitch(stringResource(R.string.switch_touchable_bar), prefs.interactive) {
		vm.updatePrefs { copy(interactive = it) }
	}
	SettingSwitch(stringResource(R.string.switch_auto_hide_boot), prefs.autoHideBoot) {
		vm.updatePrefs { copy(autoHideBoot = it) }
	}
	SettingSlider(
		stringResource(R.string.slider_burn_in_shift),
		prefs.burnInMin,
		0,
		30,
		stringResource(R.string.suffix_minutes)
	) {
		vm.updatePrefs { copy(burnInMin = it) }
	}
}
