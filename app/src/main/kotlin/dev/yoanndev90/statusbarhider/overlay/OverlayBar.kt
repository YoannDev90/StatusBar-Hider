package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.core.settings.LockScreenMode
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState
import dev.yoanndev90.statusbarhider.overlay.model.OverlayProgress

/**
 * System-wide custom status bar, rendered with Compose inside a
 * TYPE_APPLICATION_OVERLAY window. Layout mirrors the former
 * view_custom_status_bar.xml (sizes, paddings, order), except that a detected
 * cutout splits the widget row around the camera and can carry a progress ring.
 */
@Composable
fun OverlayBar(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	onClockClick: () -> Unit,
	onDateClick: () -> Unit
) {
	val fg = Color(prefs.textColor())
	val density = LocalDensity.current
	val camera = state.camera
	val progress = state.progress
	val padStart = prefs.padStartDp.dp
	val padEnd = prefs.padEndDp.dp
	val padTop = prefs.padTopDp.dp
	val padBottom = prefs.padBottomDp.dp
	val spacing = prefs.widgetSpacingDp.dp
	val showRing = camera != null && prefs.cameraRing && progress != null
	val ringProgress: OverlayProgress? = if (showRing) progress else null
	val ringStrokePx = with(density) { prefs.cameraRingStrokeDp.dp.toPx() }
	// Widgets keep clear of the ring too, not just of the cutout slot.
	val ringClearancePx = if (showRing) ringStrokePx else 0f

	// The bar grows so the camera (and its ring) always fit inside it:
	// one window, no touch dead strip below the bar.
	val minHeight =
		if (camera != null) {
			with(density) { (camera.centerY + camera.radius + ringClearancePx).toDp() }
		} else {
			0.dp
		}

	val cell: @Composable RowScope.(String) -> Unit = { id ->
		key(id) { WidgetSlot(id, prefs, state, fg, onClockClick, onDateClick) }
	}

	Box(
		modifier =
			Modifier
				.fillMaxWidth()
				.defaultMinSize(minHeight = minHeight)
				.background(Color(prefs.backgroundColor()))
	) {
		val clockOnly = state.locked && prefs.lockScreenMode == LockScreenMode.CLOCK_ONLY
		BarLayout(
			prefs = prefs,
			clockOnly = clockOnly,
			camera = camera,
			cell = cell,
			spacing = spacing,
			padStart = padStart,
			padTop = padTop,
			padEnd = padEnd,
			padBottom = padBottom,
			ringClearancePx = ringClearancePx
		)
		if (showRing && !clockOnly) {
			CameraRing(camera, ringProgress, prefs, fg)
		}
	}
}

/** Dispatches one widget id to the widget that draws it. */
@Composable
private fun RowScope.WidgetSlot(
	id: String,
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color,
	onClockClick: () -> Unit,
	onDateClick: () -> Unit
) {
	when (id) {
		WidgetId.CLOCK -> ClockWidget(prefs, state.screenOn, fg, onClockClick)
		WidgetId.DATE -> DateWidget(prefs, state.screenOn, fg, onDateClick)
		WidgetId.CALENDAR -> state.calendarText?.let { CalendarWidget(prefs, it, fg) }
		WidgetId.NOTIFS -> NotifWidget(prefs, state, fg)
		WidgetId.MEDIA -> state.mediaText?.let { MediaWidget(prefs, it, fg) }
		WidgetId.SPACER -> Spacer(Modifier.weight(1f))
		WidgetId.CONNECTIVITY -> ConnectivityWidget(prefs, state, fg)
		WidgetId.BATTERY -> BatteryWidget(prefs, state, fg)
		WidgetId.ALARM -> state.alarmText?.let { AlarmWidget(prefs, it, fg) }
		WidgetId.BANDWIDTH -> BandwidthWidget(prefs, state, fg)
	}
}
