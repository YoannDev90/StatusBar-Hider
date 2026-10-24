package dev.yoanndev90.statusbarhider.core.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.yoanndev90.statusbarhider.core.log.LogStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

internal const val PREFS = "overlay_prefs"

/** Current blob slot. */
private const val KEY_PREFS_V1 = "prefs_v1"

/** Preserved copy of an unparsable blob, so the defaults fallback stays recoverable. */
private const val KEY_CORRUPT = "prefs_corrupt"

/** JSON field name of [OverlayPrefs.enabled] (the blob's own on/off flag). */
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
private const val KEY_SHOW_BATTERY = "show_battery"
private const val KEY_SHOW_BATTERY_PCT = "show_battery_pct"
private const val KEY_SHOW_WIFI = "show_wifi"
private const val KEY_SHOW_MOBILE = "show_mobile"
private const val KEY_SHOW_BLUETOOTH = "show_bluetooth"
private const val KEY_SHOW_BT_ICONS = "show_bt_icons"
private const val KEY_BT_ICON_OVERRIDES = "bt_icon_overrides"
private const val KEY_SHOW_AIRPLANE = "show_airplane"
private const val KEY_SHOW_VPN = "show_vpn"
private const val KEY_SHOW_HOTSPOT = "show_hotspot"
private const val KEY_SHOW_ALARM = "show_alarm"
private const val KEY_SHOW_BANDWIDTH = "show_bandwidth"
private const val KEY_BANDWIDTH_MERGED = "bandwidth_merged"
private const val KEY_UPDATE_INTERVAL_SEC = "update_interval_sec"
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

/** Font weight of the bar text; serializes by name, so the JSON keeps "NORMAL"/"MEDIUM"/"BOLD". */
@Serializable
enum class BarFontWeight {
	NORMAL,
	MEDIUM,
	BOLD
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
 * Persisted config for the overlay bar. The JSON shape lives in
 * [OverlayPrefsCodec], the accepted range of each field in
 * [dev.yoanndev90.statusbarhider.core.settings.specs.OverlaySpecs]; this class
 * is the model, its defaults and the SharedPreferences I/O
 * [dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository] drives, so
 * BootReceiver can re-apply the config without any UI.
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
	@SerialName(KEY_SHOW_BT_ICONS)
	val showBtIcons: Boolean = true,
	/** Uppercased MAC to forced kind name (e.g. "SPEAKER"); wins over every automatic signal. */
	@SerialName(KEY_BT_ICON_OVERRIDES)
	val btIconOverrides: Map<String, String> = emptyMap(),
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
	val dateFormat: String = DEFAULT_DATE_FORMAT,
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
	val fontWeightName: BarFontWeight = BarFontWeight.NORMAL,
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
	val cameraRingColor: String = ""
) {
	/** Effective clock pattern honoring the seconds and 12 / 24-hour toggles. */
	fun effectiveTimeFormat(): String =
		when {
			use24h && showSeconds -> DEFAULT_FORMAT_WITH_SECONDS
			use24h -> DEFAULT_FORMAT_NO_SECONDS
			showSeconds -> FORMAT_12H_WITH_SECONDS
			else -> FORMAT_12H
		}

	/** ARGB background for the overlay root. */
	fun backgroundColor(): Int =
		when (background) {
			OverlayBackground.TRANSPARENT -> android.graphics.Color.TRANSPARENT
			OverlayBackground.SEMI -> 0xCC000000.toInt()
			OverlayBackground.BLACK -> android.graphics.Color.BLACK
		}

	/** Text color honoring the dark-text toggle (for transparent bar over light apps). */
	fun textColor(): Int = if (darkText) android.graphics.Color.BLACK else android.graphics.Color.WHITE

	/** Stored shape of this value; see [OverlayPrefsCodec.toJson]. */
	fun toJson(): String = OverlayPrefsCodec.toJson(this)

	companion object {
		private const val TAG = "OverlayPrefs"

		/** SharedPreferences file name, shared with [dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository]. */
		const val PREFS_NAME = PREFS
		const val DEFAULT_FORMAT_WITH_SECONDS = "HH:mm:ss"
		const val DEFAULT_FORMAT_NO_SECONDS = "HH:mm"
		const val FORMAT_12H = "h:mm a"
		const val FORMAT_12H_WITH_SECONDS = "h:mm:ss a"

		/** Default pattern of [OverlayPrefs.dateFormat]; also the fallback for a blank stored value. */
		const val DEFAULT_DATE_FORMAT = "EEE dd MMM"

		/**
		 * Reads the blob through [OverlayPrefsCodec]. An unparsable blob is
		 * preserved under [KEY_CORRUPT] and reported to logcat + the app log:
		 * the defaults are returned, nothing is lost.
		 */
		fun load(context: Context): OverlayPrefs {
			val app = context.applicationContext
			val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			val raw = prefs.getString(KEY_PREFS_V1, null) ?: return OverlayPrefs()
			return OverlayPrefsCodec.fromJson(raw) ?: corrupt(app, prefs, raw)
		}

		private fun corrupt(
			app: Context,
			prefs: SharedPreferences,
			raw: String
		): OverlayPrefs {
			prefs.edit().putString(KEY_CORRUPT, raw).apply()
			val line = "Overlay prefs failed to parse - defaults loaded, corrupt copy kept"
			Log.e(TAG, line)
			LogStore.append(app, line)
			return OverlayPrefs()
		}

		fun save(
			context: Context,
			value: OverlayPrefs
		) {
			context.applicationContext
				.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit()
				.putString(KEY_PREFS_V1, value.toJson())
				.apply()
		}

		/**
		 * Strict parse for backups and profile snapshots; throws on malformed
		 * input instead of silently falling back to the defaults.
		 */
		fun decodeStrict(element: JsonElement): OverlayPrefs = OverlayPrefsCodec.decodeStrict(element)
	}
}
