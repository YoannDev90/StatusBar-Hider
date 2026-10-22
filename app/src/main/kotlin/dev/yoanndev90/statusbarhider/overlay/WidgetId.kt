package dev.yoanndev90.statusbarhider.overlay

import dev.yoanndev90.statusbarhider.R

/** Stable widget ids used for ordering. */
object WidgetId {
	const val CLOCK = "clock"
	const val DATE = "date"
	const val CALENDAR = "calendar"
	const val NOTIFS = "notifs"
	const val MEDIA = "media"
	const val SPACER = "spacer"
	const val CONNECTIVITY = "connectivity"
	const val BATTERY = "battery"
	const val ALARM = "alarm"
	const val BANDWIDTH = "bandwidth"
	val ALL = listOf(CLOCK, DATE, CALENDAR, NOTIFS, MEDIA, SPACER, CONNECTIVITY, BATTERY, ALARM, BANDWIDTH)
	val DEFAULT_ORDER = listOf(CLOCK, DATE, CALENDAR, NOTIFS, MEDIA, SPACER, CONNECTIVITY, BATTERY, ALARM, BANDWIDTH)

	/** String resource for the display label of [id]; unknown ids fall back to a generic label. */
	fun labelRes(id: String): Int =
		when (id) {
			CLOCK -> R.string.widget_clock
			DATE -> R.string.widget_date
			CALENDAR -> R.string.widget_calendar
			NOTIFS -> R.string.widget_notifications
			MEDIA -> R.string.widget_media
			SPACER -> R.string.widget_spacer
			CONNECTIVITY -> R.string.widget_connectivity
			BATTERY -> R.string.widget_battery
			ALARM -> R.string.widget_alarm
			BANDWIDTH -> R.string.widget_bandwidth
			else -> R.string.widget_unknown
		}

	/** Parses a stored order, dropping unknown ids and appending missing ones. */
	fun parseOrder(raw: String?): List<String> {
		val ids = raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
		val known = ids.filter { it in ALL }.distinct()
		return known + (ALL - known.toSet())
	}
}
