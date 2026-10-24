package dev.yoanndev90.statusbarhider.core.settings.specs

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.TextSpec

/** Clock and date widgets: format, seconds, calendar and alarm lines. */
object ClockSpecs {
	val showSeconds =
		BoolSpec<OverlayPrefs>(
			key = "show_seconds",
			get = OverlayPrefs::showSeconds,
			set = { p, v -> p.copy(showSeconds = v) },
			default = false,
			icon = R.drawable.ic_timer,
			label = R.string.switch_show_seconds,
			subtitle = R.string.switch_show_seconds_sub
		)

	val use24h =
		BoolSpec<OverlayPrefs>(
			key = "use_24h",
			get = OverlayPrefs::use24h,
			set = { p, v -> p.copy(use24h = v) },
			default = true,
			icon = R.drawable.ic_schedule,
			label = R.string.switch_use_24h
		)

	val showDate =
		BoolSpec<OverlayPrefs>(
			key = "show_date",
			get = OverlayPrefs::showDate,
			set = { p, v -> p.copy(showDate = v) },
			default = true,
			icon = R.drawable.ic_event,
			label = R.string.switch_date
		)

	/**
	 * A stored pattern is kept only when it is non-empty; the screen still
	 * validates it against SimpleDateFormat before writing, so the bar never
	 * formats with a pattern the user saw rejected.
	 */
	val dateFormat =
		TextSpec<OverlayPrefs>(
			key = "date_format",
			get = OverlayPrefs::dateFormat,
			set = { p, v -> p.copy(dateFormat = v) },
			default = OverlayPrefs.DEFAULT_DATE_FORMAT,
			validate = { it.isNotEmpty() },
			icon = R.drawable.ic_edit,
			label = R.string.label_date_format
		)

	val showAlarm =
		BoolSpec<OverlayPrefs>(
			key = "show_alarm",
			get = OverlayPrefs::showAlarm,
			set = { p, v -> p.copy(showAlarm = v) },
			default = true,
			icon = R.drawable.ic_alarm,
			label = R.string.switch_next_alarm
		)

	val showCalendar =
		BoolSpec<OverlayPrefs>(
			key = "show_calendar",
			get = OverlayPrefs::showCalendar,
			set = { p, v -> p.copy(showCalendar = v) },
			default = false,
			icon = R.drawable.ic_calendar,
			label = R.string.switch_next_calendar_event,
			subtitle = R.string.switch_next_calendar_event_sub
		)

	val specs: List<SettingSpec<*, OverlayPrefs>> = listOf(showSeconds, use24h, showDate, dateFormat, showAlarm, showCalendar)
}
