package dev.yoanndev90.statusbarhider.features.bar.sections

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.features.bar.BarViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingGroup
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Clock, date, alarm and calendar widgets. */
@Composable
fun ClockSection(
	prefs: OverlayPrefs,
	vm: BarViewModel
) {
	val context = LocalContext.current
	val invalidDateFormatToast = stringResource(R.string.toast_invalid_date_format)
	var dateFormat by remember(prefs.dateFormat) { mutableStateOf(prefs.dateFormat) }
	val calendarPermission =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
			if (granted) {
				vm.setShowCalendar(true)
			} else {
				// The switch state comes from prefs, so a denial leaves it unchecked.
				Toast.makeText(context, R.string.toast_calendar_permission_denied, Toast.LENGTH_SHORT).show()
			}
		}

	SettingGroup(R.string.group_clock_date) {
		SettingSwitch(R.string.switch_show_seconds, prefs.showSeconds) { vm.updatePrefs { copy(showSeconds = it) } }
		SettingSwitch(R.string.switch_use_24h, prefs.use24h) { vm.updatePrefs { copy(use24h = it) } }
		SettingSwitch(R.string.switch_show_on_lock_screen, prefs.showOnLockScreen) {
			vm.updatePrefs { copy(showOnLockScreen = it) }
		}
		SettingSwitch(R.string.switch_date, prefs.showDate) { vm.updatePrefs { copy(showDate = it) } }
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
					vm.setDateFormat(fmt)
				}
			) {
				Text(stringResource(R.string.action_set))
			}
		}
		SettingSwitch(R.string.switch_next_alarm, prefs.showAlarm) { vm.updatePrefs { copy(showAlarm = it) } }
		SettingSwitch(R.string.switch_next_calendar_event, prefs.showCalendar) { checked ->
			if (checked && context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
				calendarPermission.launch(Manifest.permission.READ_CALENDAR)
			} else {
				vm.setShowCalendar(checked)
			}
		}
	}
}
