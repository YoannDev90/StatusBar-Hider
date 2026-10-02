package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

internal const val PREFS = "overlay_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_SHOW_SECONDS = "show_seconds"
private const val KEY_SHOW_BATTERY = "show_battery"
private const val KEY_SHOW_BATTERY_PCT = "show_battery_pct"
private const val KEY_SHOW_WIFI = "show_wifi"
private const val KEY_SHOW_MOBILE = "show_mobile"
private const val KEY_SHOW_BLUETOOTH = "show_bluetooth"
private const val KEY_SHOW_AIRPLANE = "show_airplane"
private const val KEY_SHOW_VPN = "show_vpn"
private const val KEY_SHOW_HOTSPOT = "show_hotspot"
private const val KEY_SHOW_ALARM = "show_alarm"
private const val KEY_SHOW_BANDWIDTH = "show_bandwidth"
private const val KEY_BANDWIDTH_MERGED = "bandwidth_merged"
private const val KEY_UPDATE_INTERVAL_SEC = "update_interval_sec"
private const val KEY_TIME_FORMAT = "time_format"
private const val KEY_BACKGROUND = "background"
private const val KEY_DARK_TEXT = "dark_text"
private const val KEY_SHOW_USB = "show_usb"
private const val KEY_PAD_START = "pad_start_dp"
private const val KEY_PAD_TOP = "pad_top_dp"
private const val KEY_PAD_END = "pad_end_dp"
private const val KEY_PAD_BOTTOM = "pad_bottom_dp"
private const val KEY_SHOW_DATE = "show_date"
private const val KEY_DATE_FORMAT = "date_format"
private const val KEY_SHOW_BATTERY_ICON = "show_battery_icon"
private const val KEY_SHOW_NOTIFS = "show_notifs"
private const val KEY_MAX_NOTIFS = "max_notifs"
private const val KEY_WIDGET_ORDER = "widget_order"
private const val KEY_BURN_IN_MIN = "burn_in_min"
private const val KEY_INTERACTIVE = "interactive"
private const val KEY_SHOW_NFC = "show_nfc"
private const val KEY_SHOW_GPS = "show_gps"
private const val KEY_SHOW_CALENDAR = "show_calendar"
private const val KEY_SHOW_MEDIA = "show_media"
private const val KEY_SHOW_DND = "show_dnd"
private const val KEY_SHOW_DATA_SAVER = "show_data_saver"
private const val KEY_SHOW_ROTATE = "show_rotate"
private const val KEY_SHOW_TORCH = "show_torch"
private const val KEY_FONT_SIZE = "font_size_sp"
private const val KEY_FONT_WEIGHT = "font_weight"
private const val KEY_WIDGET_SPACING = "widget_spacing_dp"

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

	fun label(id: String): String =
		when (id) {
			CLOCK -> "Clock"
			DATE -> "Date"
			CALENDAR -> "Next event"
			NOTIFS -> "Notifications"
			MEDIA -> "Now playing"
			SPACER -> "— Flexible space —"
			CONNECTIVITY -> "Connectivity"
			BATTERY -> "Battery"
			ALARM -> "Next alarm"
			BANDWIDTH -> "Bandwidth"
			else -> id
		}

	/** Parses a stored order, dropping unknown ids and appending missing ones. */
	fun parseOrder(raw: String?): List<String> {
		val ids = raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
		val known = ids.filter { it in ALL }.distinct()
		return known + (ALL - known.toSet())
	}
}

enum class OverlayBackground {
	TRANSPARENT,
	SEMI,
	BLACK
}

/**
 * Persisted config for the overlay bar. Backed by SharedPreferences so
 * BootReceiver can re-apply it without UI.
 */
data class OverlayPrefs(
	val enabled: Boolean = false,
	val showSeconds: Boolean = false,
	val timeFormat: String = DEFAULT_FORMAT_NO_SECONDS,
	val showBattery: Boolean = true,
	val showBatteryPct: Boolean = true,
	val showWifi: Boolean = true,
	val showMobileData: Boolean = true,
	val showBluetooth: Boolean = true,
	val showAirplane: Boolean = true,
	val showVpn: Boolean = false,
	val showHotspot: Boolean = false,
	val showAlarm: Boolean = true,
	val showBandwidth: Boolean = false,
	val bandwidthMerged: Boolean = false,
	val updateIntervalSec: Int = 15,
	val background: OverlayBackground = OverlayBackground.SEMI,
	val darkText: Boolean = false,
	val showUsb: Boolean = true,
	val padStartDp: Int = 12,
	val padTopDp: Int = 4,
	val padEndDp: Int = 12,
	val padBottomDp: Int = 4,
	val showDate: Boolean = true,
	val dateFormat: String = "EEE dd MMM",
	val showBatteryIcon: Boolean = true,
	val showNotifs: Boolean = true,
	val maxNotifs: Int = 5,
	val widgetOrder: List<String> = WidgetId.DEFAULT_ORDER,
	val burnInMin: Int = 5,
	val interactive: Boolean = false,
	val showNfc: Boolean = false,
	val showGps: Boolean = false,
	val showCalendar: Boolean = false,
	val showMedia: Boolean = false,
	val showDnd: Boolean = false,
	val showDataSaver: Boolean = false,
	val showRotate: Boolean = false,
	val showTorch: Boolean = false,
	val fontSizeSp: Int = 13,
	val fontWeightName: String = "NORMAL",
	val widgetSpacingDp: Int = 6
) {
	/** Effective format honoring the seconds toggle. */
	fun effectiveTimeFormat(): String =
		if (showSeconds) {
			if (timeFormat.contains("ss")) timeFormat else "$timeFormat:ss"
		} else {
			timeFormat
				.replace(":ss", "")
				.replace("ss", "")
				.trim()
				.ifEmpty { DEFAULT_FORMAT_NO_SECONDS }
		}

	fun hasSeconds(): Boolean = effectiveTimeFormat().contains("ss")

	/** ARGB background for the overlay root. */
	fun backgroundColor(): Int =
		when (background) {
			OverlayBackground.TRANSPARENT -> android.graphics.Color.TRANSPARENT
			OverlayBackground.SEMI -> 0xCC000000.toInt()
			OverlayBackground.BLACK -> android.graphics.Color.BLACK
		}

	/** Text color honoring the dark-text toggle (for transparent bar over light apps). */
	fun textColor(): Int = if (darkText) android.graphics.Color.BLACK else android.graphics.Color.WHITE

	fun toJson(): String =
		JSONObject()
			.put(KEY_ENABLED, enabled)
			.put(KEY_SHOW_SECONDS, showSeconds)
			.put(KEY_TIME_FORMAT, timeFormat)
			.put(KEY_SHOW_BATTERY, showBattery)
			.put(KEY_SHOW_BATTERY_PCT, showBatteryPct)
			.put(KEY_SHOW_WIFI, showWifi)
			.put(KEY_SHOW_MOBILE, showMobileData)
			.put(KEY_SHOW_BLUETOOTH, showBluetooth)
			.put(KEY_SHOW_AIRPLANE, showAirplane)
			.put(KEY_SHOW_VPN, showVpn)
			.put(KEY_SHOW_HOTSPOT, showHotspot)
			.put(KEY_SHOW_ALARM, showAlarm)
			.put(KEY_SHOW_BANDWIDTH, showBandwidth)
			.put(KEY_BANDWIDTH_MERGED, bandwidthMerged)
			.put(KEY_UPDATE_INTERVAL_SEC, updateIntervalSec)
			.put(KEY_BACKGROUND, background.name)
			.put(KEY_DARK_TEXT, darkText)
			.put(KEY_SHOW_USB, showUsb)
			.put(KEY_PAD_START, padStartDp)
			.put(KEY_PAD_TOP, padTopDp)
			.put(KEY_PAD_END, padEndDp)
			.put(KEY_PAD_BOTTOM, padBottomDp)
			.put(KEY_SHOW_DATE, showDate)
			.put(KEY_DATE_FORMAT, dateFormat)
			.put(KEY_SHOW_BATTERY_ICON, showBatteryIcon)
			.put(KEY_SHOW_NOTIFS, showNotifs)
			.put(KEY_MAX_NOTIFS, maxNotifs)
			.put(KEY_WIDGET_ORDER, widgetOrder.joinToString(","))
			.put(KEY_BURN_IN_MIN, burnInMin)
			.put(KEY_INTERACTIVE, interactive)
			.put(KEY_SHOW_NFC, showNfc)
			.put(KEY_SHOW_GPS, showGps)
			.put(KEY_SHOW_CALENDAR, showCalendar)
			.put(KEY_SHOW_MEDIA, showMedia)
			.put(KEY_SHOW_DND, showDnd)
			.put(KEY_SHOW_DATA_SAVER, showDataSaver)
			.put(KEY_SHOW_ROTATE, showRotate)
			.put(KEY_SHOW_TORCH, showTorch)
			.put(KEY_FONT_SIZE, fontSizeSp)
			.put(KEY_FONT_WEIGHT, fontWeightName)
			.put(KEY_WIDGET_SPACING, widgetSpacingDp)
			.toString()

	companion object {
		/** SharedPreferences file name, shared with [dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository]. */
		const val PREFS_NAME = PREFS
		const val DEFAULT_FORMAT_WITH_SECONDS = "HH:mm:ss"
		const val DEFAULT_FORMAT_NO_SECONDS = "HH:mm"

		/** Accepted values of [OverlayPrefs.fontWeightName]. */
		private val FONT_WEIGHTS = setOf("NORMAL", "MEDIUM", "BOLD")

		fun fromJson(raw: String): OverlayPrefs =
			try {
				val o = JSONObject(raw)
				OverlayPrefs(
					enabled = o.optBoolean(KEY_ENABLED, false),
					showSeconds = o.optBoolean(KEY_SHOW_SECONDS, false),
					timeFormat = o.optString(KEY_TIME_FORMAT, DEFAULT_FORMAT_NO_SECONDS),
					showBattery = o.optBoolean(KEY_SHOW_BATTERY, true),
					showBatteryPct = o.optBoolean(KEY_SHOW_BATTERY_PCT, true),
					showWifi = o.optBoolean(KEY_SHOW_WIFI, true),
					showMobileData = o.optBoolean(KEY_SHOW_MOBILE, true),
					showBluetooth = o.optBoolean(KEY_SHOW_BLUETOOTH, true),
					showAirplane = o.optBoolean(KEY_SHOW_AIRPLANE, true),
					showVpn = o.optBoolean(KEY_SHOW_VPN, false),
					showHotspot = o.optBoolean(KEY_SHOW_HOTSPOT, false),
					showAlarm = o.optBoolean(KEY_SHOW_ALARM, true),
					showBandwidth = o.optBoolean(KEY_SHOW_BANDWIDTH, false),
					bandwidthMerged = o.optBoolean(KEY_BANDWIDTH_MERGED, false),
					updateIntervalSec = o.optInt(KEY_UPDATE_INTERVAL_SEC, 15).coerceIn(5, 60),
					background =
						try {
							OverlayBackground.valueOf(o.optString(KEY_BACKGROUND, OverlayBackground.SEMI.name))
						} catch (_: Exception) {
							OverlayBackground.SEMI
						},
					darkText = o.optBoolean(KEY_DARK_TEXT, false),
					showUsb = o.optBoolean(KEY_SHOW_USB, true),
					padStartDp = o.optInt(KEY_PAD_START, 12).coerceIn(0, 32),
					padTopDp = o.optInt(KEY_PAD_TOP, 4).coerceIn(0, 32),
					padEndDp = o.optInt(KEY_PAD_END, 12).coerceIn(0, 32),
					padBottomDp = o.optInt(KEY_PAD_BOTTOM, 4).coerceIn(0, 32),
					showDate = o.optBoolean(KEY_SHOW_DATE, true),
					dateFormat = o.optString(KEY_DATE_FORMAT, "EEE dd MMM").ifEmpty { "EEE dd MMM" },
					showBatteryIcon = o.optBoolean(KEY_SHOW_BATTERY_ICON, true),
					showNotifs = o.optBoolean(KEY_SHOW_NOTIFS, true),
					maxNotifs = o.optInt(KEY_MAX_NOTIFS, 5).coerceIn(1, 8),
					widgetOrder = WidgetId.parseOrder(o.optString(KEY_WIDGET_ORDER, "").ifEmpty { null }),
					burnInMin = o.optInt(KEY_BURN_IN_MIN, 5).coerceIn(0, 30),
					interactive = o.optBoolean(KEY_INTERACTIVE, false),
					showNfc = o.optBoolean(KEY_SHOW_NFC, false),
					showGps = o.optBoolean(KEY_SHOW_GPS, false),
					showCalendar = o.optBoolean(KEY_SHOW_CALENDAR, false),
					showMedia = o.optBoolean(KEY_SHOW_MEDIA, false),
					showDnd = o.optBoolean(KEY_SHOW_DND, false),
					showDataSaver = o.optBoolean(KEY_SHOW_DATA_SAVER, false),
					showRotate = o.optBoolean(KEY_SHOW_ROTATE, false),
					showTorch = o.optBoolean(KEY_SHOW_TORCH, false),
					fontSizeSp = o.optInt(KEY_FONT_SIZE, 13).coerceIn(10, 20),
					fontWeightName =
						o.optString(KEY_FONT_WEIGHT, "NORMAL").let { weight ->
							if (weight in FONT_WEIGHTS) weight else "NORMAL"
						},
					widgetSpacingDp = o.optInt(KEY_WIDGET_SPACING, 6).coerceIn(0, 12)
				)
			} catch (_: Exception) {
				OverlayPrefs()
			}

		fun load(context: Context): OverlayPrefs =
			load(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

		fun load(prefs: SharedPreferences): OverlayPrefs {
			val raw = prefs.getString(KEY_ENABLED, null) ?: return OverlayPrefs()
			// Stored as full JSON under KEY_ENABLED slot for backward-compat simplicity.
			return fromJson(raw)
		}

		fun save(
			context: Context,
			prefs: OverlayPrefs
		) {
			save(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), prefs)
		}

		fun save(
			prefs: SharedPreferences,
			value: OverlayPrefs
		) {
			prefs
				.edit()
				.putString(KEY_ENABLED, value.toJson())
				.apply()
		}

		/** Human-readable throughput, e.g. 512B, 12K, 3.4M (per second). */
		fun formatSpeed(bytesPerSecond: Long): String =
			when {
				bytesPerSecond >= 1_048_576L -> String.format("%.1fM", bytesPerSecond / 1_048_576.0)
				bytesPerSecond >= 1_024L -> String.format("%.0fK", bytesPerSecond / 1_024.0)
				else -> "${bytesPerSecond.coerceAtLeast(0)}B"
			}

		/** Battery % from a sticky ACTION_BATTERY_CHANGED extras pair. */
		fun batteryPct(
			level: Int,
			scale: Int
		): Int = if (level >= 0 && scale > 0) (level * 100) / scale else -1
	}
}
