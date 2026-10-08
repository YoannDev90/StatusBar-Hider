package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.ui.graphics.ImageBitmap

/** One notification icon ready for display (converted from Drawable once per snapshot). */
data class OverlayNotifIcon(
	val bitmap: ImageBitmap?,
	val pkg: String
)

/**
 * Active notification progress shown as a ring around the camera:
 * [fraction] is 0..1 when determinate, meaningless when [indeterminate].
 */
data class OverlayProgress(
	val fraction: Float,
	val indeterminate: Boolean,
	val pkg: String
)

/**
 * Runtime state of the overlay bar, written by the service receivers and
 * read by [OverlayBar]. Preferences ([OverlayPrefs]) arrive separately.
 * The clock and date tick locally inside their widgets, never here, so a
 * 1s tick does not recompose the whole bar.
 */
data class OverlayBarState(
	val batteryPct: Int = -1,
	val batteryCharging: Boolean = false,
	val usbConnected: Boolean = false,
	val screenOn: Boolean = true,
	/** Keyguard is up: drives the lock screen layout and the privacy filter. */
	val locked: Boolean = false,
	val notifsEnabled: Boolean = false,
	val notifs: List<OverlayNotifIcon> = emptyList(),
	val bandwidthText: String = "",
	val alarmText: String? = null,
	val calendarText: String? = null,
	val mediaText: String? = null,
	val airplane: Boolean = false,
	val wifi: Boolean = false,
	val mobile: Boolean = false,
	val mobileType: String = "",
	/** Cellular signal level 0..4 (-1 = unknown, drawn with the static icon). */
	val signalLevel: Int = -1,
	val bluetooth: Boolean = false,
	val vpn: Boolean = false,
	val hotspot: Boolean = false,
	val nfc: Boolean = false,
	val gps: Boolean = false,
	val dnd: Boolean = false,
	val dataSaver: Boolean = false,
	val autoRotate: Boolean = false,
	val torch: Boolean = false,
	/** Resolved cutout geometry (null = nothing detected in auto mode). */
	val camera: CameraGeometry? = null,
	/** Current notification progress to draw around the camera (null = none). */
	val progress: OverlayProgress? = null
)
