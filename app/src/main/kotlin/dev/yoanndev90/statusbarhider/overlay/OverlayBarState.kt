package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.ui.graphics.ImageBitmap

/** One notification icon ready for display (converted from Drawable once per snapshot). */
data class OverlayNotifIcon(
	val bitmap: ImageBitmap?,
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
	val bluetooth: Boolean = false,
	val vpn: Boolean = false,
	val hotspot: Boolean = false,
	val nfc: Boolean = false,
	val gps: Boolean = false,
	val dnd: Boolean = false,
	val dataSaver: Boolean = false,
	val autoRotate: Boolean = false,
	val torch: Boolean = false,
	val sideCutoutPx: Int = 0
)
