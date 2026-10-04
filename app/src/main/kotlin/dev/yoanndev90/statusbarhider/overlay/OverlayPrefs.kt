package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.content.SharedPreferences
import dev.yoanndev90.statusbarhider.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal const val PREFS = "overlay_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_SHOW_SECONDS = "show_seconds"
private const val KEY_USE_24H = "use_24h"
private const val KEY_SHOW_ON_LOCK_SCREEN = "show_on_lock_screen"
private const val KEY_LOCK_SCREEN_MODE = "lock_screen_mode"
private const val KEY_HIDE_NOTIFS_ON_LOCK = "hide_notifs_on_lock"
private const val KEY_HIDE_BAR_IN_APPS = "hide_bar_in_apps"
private const val KEY_HIDDEN_APPS = "hidden_apps"
private const val KEY_AUTO_HIDE_BOOT = "auto_hide_boot"
private const val KEY_AUTO_START_BAR = "auto_start_bar"
private const val KEY_CAMERA_RING_PREVIEW = "camera_ring_preview"
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
private const val KEY_CAMERA_AUTO = "camera_auto_detect"
private const val KEY_CAMERA_GAP = "camera_gap_dp"
private const val KEY_CAMERA_NUDGE_X = "camera_nudge_x_dp"
private const val KEY_CAMERA_NUDGE_Y = "camera_nudge_y_dp"
private const val KEY_CAMERA_OFFSET_X = "camera_offset_x_pct"
private const val KEY_CAMERA_OFFSET_Y = "camera_offset_y_dp"
private const val KEY_CAMERA_SIZE = "camera_size_dp"
private const val KEY_CAMERA_RING = "camera_ring"
private const val KEY_CAMERA_RING_STROKE = "camera_ring_stroke_dp"
private const val KEY_CAMERA_RING_COLOR = "camera_ring_color"

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

@Serializable
enum class OverlayBackground {
	TRANSPARENT,
	SEMI,
	BLACK
}

/** Widget layout used while the device is locked (see [OverlayPrefs.lockScreenMode]). */
@Serializable
enum class LockScreenMode {
	/** Same widgets as when unlocked. */
	FULL,

	/** Clock widget only: nothing else is drawn above the keyguard. */
	CLOCK_ONLY
}

/**
 * Keeps [OverlayPrefs.widgetOrder] stored as the legacy comma-separated string
 * instead of a JSON array, so older builds can still read it.
 */
private object WidgetOrderSerializer : KSerializer<List<String>> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("widgetOrder", PrimitiveKind.STRING)

	override fun serialize(
		encoder: Encoder,
		value: List<String>
	) {
		encoder.encodeString(value.joinToString(","))
	}

	override fun deserialize(decoder: Decoder): List<String> = WidgetId.parseOrder(decoder.decodeString())
}

/**
 * Same storage choice as [WidgetOrderSerializer] for the app blacklist:
 * package names never contain a comma, and the CSV form stays readable.
 */
private object CsvListSerializer : KSerializer<List<String>> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("csvList", PrimitiveKind.STRING)

	override fun serialize(
		encoder: Encoder,
		value: List<String>
	) {
		encoder.encodeString(value.joinToString(","))
	}

	override fun deserialize(decoder: Decoder): List<String> =
		decoder
			.decodeString()
			.split(",")
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.distinct()
}

/**
 * Persisted config for the overlay bar. Backed by SharedPreferences so
 * BootReceiver can re-apply it without UI.
 */
@Serializable
data class OverlayPrefs(
	@SerialName(KEY_ENABLED)
	val enabled: Boolean = false,
	@SerialName(KEY_SHOW_SECONDS)
	val showSeconds: Boolean = false,
	@SerialName(KEY_USE_24H)
	val use24h: Boolean = true,
	@SerialName(KEY_SHOW_BATTERY)
	val showBattery: Boolean = true,
	@SerialName(KEY_SHOW_BATTERY_PCT)
	val showBatteryPct: Boolean = true,
	@SerialName(KEY_SHOW_WIFI)
	val showWifi: Boolean = true,
	@SerialName(KEY_SHOW_MOBILE)
	val showMobileData: Boolean = true,
	@SerialName(KEY_SHOW_BLUETOOTH)
	val showBluetooth: Boolean = true,
	@SerialName(KEY_SHOW_AIRPLANE)
	val showAirplane: Boolean = true,
	@SerialName(KEY_SHOW_VPN)
	val showVpn: Boolean = false,
	@SerialName(KEY_SHOW_HOTSPOT)
	val showHotspot: Boolean = false,
	@SerialName(KEY_SHOW_ALARM)
	val showAlarm: Boolean = true,
	@SerialName(KEY_SHOW_BANDWIDTH)
	val showBandwidth: Boolean = false,
	@SerialName(KEY_BANDWIDTH_MERGED)
	val bandwidthMerged: Boolean = false,
	@SerialName(KEY_UPDATE_INTERVAL_SEC)
	val updateIntervalSec: Int = 15,
	@SerialName(KEY_BACKGROUND)
	val background: OverlayBackground = OverlayBackground.SEMI,
	@SerialName(KEY_DARK_TEXT)
	val darkText: Boolean = false,
	@SerialName(KEY_SHOW_USB)
	val showUsb: Boolean = true,
	@SerialName(KEY_PAD_START)
	val padStartDp: Int = 12,
	@SerialName(KEY_PAD_TOP)
	val padTopDp: Int = 4,
	@SerialName(KEY_PAD_END)
	val padEndDp: Int = 12,
	@SerialName(KEY_PAD_BOTTOM)
	val padBottomDp: Int = 4,
	@SerialName(KEY_SHOW_DATE)
	val showDate: Boolean = true,
	@SerialName(KEY_DATE_FORMAT)
	val dateFormat: String = "EEE dd MMM",
	@SerialName(KEY_SHOW_BATTERY_ICON)
	val showBatteryIcon: Boolean = true,
	@SerialName(KEY_SHOW_NOTIFS)
	val showNotifs: Boolean = true,
	@SerialName(KEY_MAX_NOTIFS)
	val maxNotifs: Int = 5,
	@SerialName(KEY_WIDGET_ORDER)
	@Serializable(with = WidgetOrderSerializer::class)
	val widgetOrder: List<String> = WidgetId.DEFAULT_ORDER,
	@SerialName(KEY_BURN_IN_MIN)
	val burnInMin: Int = 5,
	@SerialName(KEY_INTERACTIVE)
	val interactive: Boolean = false,
	@SerialName(KEY_SHOW_ON_LOCK_SCREEN)
	val showOnLockScreen: Boolean = true,
	@SerialName(KEY_LOCK_SCREEN_MODE)
	val lockScreenMode: LockScreenMode = LockScreenMode.FULL,
	@SerialName(KEY_HIDE_NOTIFS_ON_LOCK)
	val hideNotifsOnLock: Boolean = false,
	@SerialName(KEY_HIDE_BAR_IN_APPS)
	val hideBarInApps: Boolean = false,
	@SerialName(KEY_HIDDEN_APPS)
	@Serializable(with = CsvListSerializer::class)
	val hiddenApps: List<String> = emptyList(),
	@SerialName(KEY_AUTO_HIDE_BOOT)
	val autoHideBoot: Boolean = true,
	@SerialName(KEY_AUTO_START_BAR)
	val autoStartBar: Boolean = false,
	@SerialName(KEY_SHOW_NFC)
	val showNfc: Boolean = false,
	@SerialName(KEY_SHOW_GPS)
	val showGps: Boolean = false,
	@SerialName(KEY_SHOW_CALENDAR)
	val showCalendar: Boolean = false,
	@SerialName(KEY_SHOW_MEDIA)
	val showMedia: Boolean = false,
	@SerialName(KEY_SHOW_DND)
	val showDnd: Boolean = false,
	@SerialName(KEY_SHOW_DATA_SAVER)
	val showDataSaver: Boolean = false,
	@SerialName(KEY_SHOW_ROTATE)
	val showRotate: Boolean = false,
	@SerialName(KEY_SHOW_TORCH)
	val showTorch: Boolean = false,
	@SerialName(KEY_FONT_SIZE)
	val fontSizeSp: Int = 13,
	@SerialName(KEY_FONT_WEIGHT)
	val fontWeightName: String = "NORMAL",
	@SerialName(KEY_WIDGET_SPACING)
	val widgetSpacingDp: Int = 6,
	@SerialName(KEY_CAMERA_AUTO)
	val cameraAutoDetect: Boolean = true,
	@SerialName(KEY_CAMERA_GAP)
	val cameraGapDp: Int = 4,
	@SerialName(KEY_CAMERA_NUDGE_X)
	val cameraNudgeXDp: Int = 0,
	@SerialName(KEY_CAMERA_NUDGE_Y)
	val cameraNudgeYDp: Int = 0,
	@SerialName(KEY_CAMERA_OFFSET_X)
	val cameraOffsetXPct: Int = 50,
	@SerialName(KEY_CAMERA_OFFSET_Y)
	val cameraOffsetYDp: Int = 12,
	@SerialName(KEY_CAMERA_SIZE)
	val cameraSizeDp: Int = 0,
	@SerialName(KEY_CAMERA_RING)
	val cameraRing: Boolean = true,
	@SerialName(KEY_CAMERA_RING_STROKE)
	val cameraRingStrokeDp: Int = 3,
	@SerialName(KEY_CAMERA_RING_COLOR)
	val cameraRingColor: String = "",
	/**
	 * Style-page test toggle: draw a full ring around the cutout regardless of
	 * notification progress. Deliberately transient - it must not survive a
	 * restart, or the preview would come back with the bar and look broken.
	 */
	@Transient
	val cameraRingPreview: Boolean = false
) {
	/** Effective clock pattern honoring the seconds and 12 / 24-hour toggles. */
	fun effectiveTimeFormat(): String =
		when {
			use24h && showSeconds -> DEFAULT_FORMAT_WITH_SECONDS
			use24h -> DEFAULT_FORMAT_NO_SECONDS
			showSeconds -> FORMAT_12H_WITH_SECONDS
			else -> FORMAT_12H
		}

	fun hasSeconds(): Boolean = showSeconds

	/** ARGB background for the overlay root. */
	fun backgroundColor(): Int =
		when (background) {
			OverlayBackground.TRANSPARENT -> android.graphics.Color.TRANSPARENT
			OverlayBackground.SEMI -> 0xCC000000.toInt()
			OverlayBackground.BLACK -> android.graphics.Color.BLACK
		}

	/** Text color honoring the dark-text toggle (for transparent bar over light apps). */
	fun textColor(): Int = if (darkText) android.graphics.Color.BLACK else android.graphics.Color.WHITE

	fun toJson(): String {
		val encoded = json.encodeToJsonElement(OverlayPrefs.serializer(), this).jsonObject
		// Legacy key kept for downgrade compatibility; mirrors the effective pattern.
		val withLegacy = JsonObject(encoded + (KEY_TIME_FORMAT to JsonPrimitive(effectiveTimeFormat())))
		return json.encodeToString(JsonObject.serializer(), withLegacy)
	}

	companion object {
		/** SharedPreferences file name, shared with [dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository]. */
		const val PREFS_NAME = PREFS
		const val DEFAULT_FORMAT_WITH_SECONDS = "HH:mm:ss"
		const val DEFAULT_FORMAT_NO_SECONDS = "HH:mm"
		const val FORMAT_12H = "h:mm a"
		const val FORMAT_12H_WITH_SECONDS = "h:mm:ss a"

		/** Accepted values of [OverlayPrefs.fontWeightName]. */
		private val FONT_WEIGHTS = setOf("NORMAL", "MEDIUM", "BOLD")

		/** Stored ring colors are plain `#RRGGBB`; anything else falls back to the text color. */
		private val RING_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

		/** Shared reader / writer for the stored blob. */
		private val json = Json {
			ignoreUnknownKeys = true
			coerceInputValues = true
			encodeDefaults = true
		}

		fun fromJson(raw: String): OverlayPrefs =
			try {
				val root = json.parseToJsonElement(raw)
				// Builds older than the 12 / 24-hour toggle stored a raw pattern
				// under KEY_TIME_FORMAT; "h:mm ..." means the user had a 12h clock.
				val input =
					if (root is JsonObject && KEY_USE_24H !in root) {
						val legacyFormat = (root[KEY_TIME_FORMAT] as? JsonPrimitive)?.content.orEmpty()
						JsonObject(root + (KEY_USE_24H to JsonPrimitive(!legacyFormat.contains("h:mm"))))
					} else {
						root
					}
				normalize(json.decodeFromJsonElement(OverlayPrefs.serializer(), input))
			} catch (_: Exception) {
				OverlayPrefs()
			}

		/**
		 * Strict parse for backups: throws on malformed input instead of
		 * silently falling back to the defaults, but normalizes like [fromJson].
		 */
		fun decodeStrict(element: kotlinx.serialization.json.JsonElement): OverlayPrefs =
			normalize(json.decodeFromJsonElement(OverlayPrefs.serializer(), element))

		/** Clamps free-form fields the way the old per-key opt* parser did. */
		private fun normalize(prefs: OverlayPrefs): OverlayPrefs =
			prefs.copy(
				updateIntervalSec = prefs.updateIntervalSec.coerceIn(5, 60),
				padStartDp = prefs.padStartDp.coerceIn(0, 32),
				padTopDp = prefs.padTopDp.coerceIn(0, 32),
				padEndDp = prefs.padEndDp.coerceIn(0, 32),
				padBottomDp = prefs.padBottomDp.coerceIn(0, 32),
				dateFormat = prefs.dateFormat.ifEmpty { "EEE dd MMM" },
				maxNotifs = prefs.maxNotifs.coerceIn(1, 8),
				burnInMin = prefs.burnInMin.coerceIn(0, 30),
				fontSizeSp = prefs.fontSizeSp.coerceIn(10, 20),
				fontWeightName = prefs.fontWeightName.takeIf { it in FONT_WEIGHTS } ?: "NORMAL",
				widgetSpacingDp = prefs.widgetSpacingDp.coerceIn(0, 12),
				cameraGapDp = prefs.cameraGapDp.coerceIn(0, 16),
				cameraNudgeXDp = prefs.cameraNudgeXDp.coerceIn(-48, 48),
				cameraNudgeYDp = prefs.cameraNudgeYDp.coerceIn(-48, 48),
				cameraOffsetXPct = prefs.cameraOffsetXPct.coerceIn(0, 100),
				cameraOffsetYDp = prefs.cameraOffsetYDp.coerceIn(0, 60),
				cameraSizeDp = prefs.cameraSizeDp.coerceIn(0, 40),
				cameraRingStrokeDp = prefs.cameraRingStrokeDp.coerceIn(1, 8),
				cameraRingColor = prefs.cameraRingColor.takeIf { RING_COLOR.matches(it) } ?: ""
			)

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
