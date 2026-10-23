package dev.yoanndev90.statusbarhider.core.settings.sections

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.core.settings.specs.ClockSpecs
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.ui.components.SettingSwitch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clock, date, alarm and calendar widgets: the specs run in display order
 * with the two rows no spec can describe kept inline — the date-format draft
 * (validated against SimpleDateFormat before it is ever written) and the
 * calendar toggle that asks for READ_CALENDAR on its off -> on edge.
 */
object ClockSection :
	SettingSection<OverlayPrefs>(
		id = "clock",
		icon = R.drawable.ic_schedule,
		title = R.string.cat_clock,
		subtitle =
			Subtitle.Live { prefs ->
				stringResource(R.string.summary_date_format, prefs.dateFormat)
			},
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("time", "seconds", "24h", "date", "format", "alarm", "calendar"),
		rows =
			listOf(
				SettingRow.OfSpec(ClockSpecs.showSeconds),
				SettingRow.OfSpec(ClockSpecs.use24h),
				SettingRow.OfSpec(ClockSpecs.showDate),
				SettingRow.Custom { prefs, vm -> DateFormatRow(prefs, vm) },
				SettingRow.OfSpec(ClockSpecs.showAlarm),
				SettingRow.Custom { prefs, vm -> CalendarToggleRow(prefs, vm) }
			)
	)

/**
 * Date-format draft: saveable local state so a rotation does not revert an
 * edit, a SimpleDateFormat probe before writing, and a toast on rejection —
 * the live feedback the generic text row deliberately does not own.
 */
@Composable
private fun DateFormatRow(prefs: OverlayPrefs, vm: PrefsViewModel) {
	val context = LocalContext.current
	val invalidDateFormatToast = stringResource(R.string.toast_invalid_date_format)
	var draft by rememberSaveable(prefs.dateFormat) { mutableStateOf(prefs.dateFormat) }
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		OutlinedTextField(
			value = draft,
			onValueChange = { draft = it },
			label = { Text(stringResource(R.string.label_date_format)) },
			leadingIcon = {
				Icon(
					painter = painterResource(R.drawable.ic_edit),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			},
			singleLine = true,
			modifier = Modifier.weight(1f).padding(end = 8.dp)
		)
		Button(
			onClick = {
				val fmt = draft.ifEmpty { OverlayPrefs.DEFAULT_DATE_FORMAT }
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
}

/** Calendar switch that asks for READ_CALENDAR on the off -> on edge; a denial leaves it unchecked. */
@Composable
private fun CalendarToggleRow(prefs: OverlayPrefs, vm: PrefsViewModel) {
	val context = LocalContext.current
	val calendarPermission =
		rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
			if (granted) {
				vm.setShowCalendar(true)
			} else {
				Toast.makeText(context, R.string.toast_calendar_permission_denied, Toast.LENGTH_SHORT).show()
			}
		}
	SettingSwitch(
		icon = R.drawable.ic_calendar,
		label = R.string.switch_next_calendar_event,
		checked = prefs.showCalendar,
		subtitle = R.string.switch_next_calendar_event_sub
	) { checked ->
		if (checked && context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
			calendarPermission.launch(Manifest.permission.READ_CALENDAR)
		} else {
			vm.setShowCalendar(checked)
		}
	}
}
