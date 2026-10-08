package dev.yoanndev90.statusbarhider.overlay

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "OverlayBar"

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
	// The Style-page preview draws a full circle regardless of notifications,
	// so the ring placement can be tuned without waiting for a progress bar.
	val preview = prefs.cameraRingPreview
	val showRing = camera != null && (preview || (prefs.cameraRing && progress != null))
	// Preview wins: a static full circle is what you want to judge alignment.
	val ringProgress: OverlayProgress? =
		when {
			!showRing -> null
			preview -> OverlayProgress(1f, false, "preview")
			else -> progress
		}
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

/**
 * Places the widget row: alone under the lock screen's clock-only mode, split
 * around a centred cutout, or as one row that edge-anchored notches get pushed
 * clear of.
 */
@Composable
private fun BoxScope.BarLayout(
	prefs: OverlayPrefs,
	clockOnly: Boolean,
	camera: CameraGeometry?,
	cell: @Composable RowScope.(String) -> Unit,
	spacing: Dp,
	padStart: Dp,
	padTop: Dp,
	padEnd: Dp,
	padBottom: Dp,
	ringClearancePx: Float
) {
	val density = LocalDensity.current
	val split = camera != null && camera.anchor == CameraAnchor.Center && WidgetId.SPACER in prefs.widgetOrder
	if (clockOnly) {
		// Lock screen, minimal layout: the clock alone, clear of the cutout slot.
		Row(
			modifier =
				Modifier
					.align(Alignment.Center)
					.fillMaxWidth()
					.padding(start = padStart, top = padTop, end = padEnd, bottom = padBottom),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(spacing)
		) {
			cell(WidgetId.CLOCK)
		}
	} else if (split) {
		// Two clusters: content on each side of the camera, bounded by its slot.
		val leftIds = prefs.widgetOrder.takeWhile { it != WidgetId.SPACER }
		val rightIds = prefs.widgetOrder.dropWhile { it != WidgetId.SPACER }.drop(1)
		val leftMax =
			with(density) { (camera.slotLeft - ringClearancePx).toDp() }.coerceAtLeast(0.dp)
		val rightMax =
			with(density) { (camera.screenWidth - camera.slotRight - ringClearancePx).toDp() }.coerceAtLeast(0.dp)
		Row(
			modifier =
				Modifier
					.align(Alignment.CenterStart)
					.widthIn(max = leftMax)
					// Children that still overflow must not bleed over the camera.
					.clipToBounds()
					.padding(start = padStart, top = padTop, bottom = padBottom),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(spacing)
		) {
			leftIds.forEach { cell(it) }
		}
		Row(
			modifier =
				Modifier
					.align(Alignment.CenterEnd)
					.widthIn(max = rightMax)
					.clipToBounds()
					.padding(end = padEnd, top = padTop, bottom = padBottom),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(spacing)
		) {
			rightIds.forEach { cell(it) }
		}
	} else {
		// No cutout, side-hugging notch (edge anchor) or no split marker:
		// a single row; edge anchors push the content clear of the slot.
		val clearance = edgeClearance(camera, density, ringClearancePx)
		Row(
			modifier =
				Modifier
					.align(Alignment.Center)
					.fillMaxWidth()
					.padding(
						start = maxOf(padStart, clearance.start),
						top = padTop,
						end = maxOf(padEnd, clearance.end),
						bottom = padBottom
					),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(spacing)
		) {
			prefs.widgetOrder.forEach { cell(it) }
		}
	}
}

/** How far the single row must stay clear of a side-hugging notch; zero elsewhere. */
private data class EdgeClearance(
	val start: Dp,
	val end: Dp
)

private fun edgeClearance(
	camera: CameraGeometry?,
	density: Density,
	ringClearancePx: Float
): EdgeClearance =
	EdgeClearance(
		start =
			if (camera != null && camera.anchor == CameraAnchor.Start) {
				with(density) { (camera.slotRight + ringClearancePx).toDp() }
			} else {
				0.dp
			},
		end =
			if (camera != null && camera.anchor == CameraAnchor.End) {
				with(density) { (camera.screenWidth - camera.slotLeft + ringClearancePx).toDp() }
			} else {
				0.dp
			}
	)

/**
 * Camera-clearance track plus the notification progress arc, aligned on the
 * cutout. A preview run spins a full circle so the ring can be positioned
 * without a real progress bar.
 */
@Composable
private fun BoxScope.CameraRing(
	camera: CameraGeometry?,
	ringProgress: OverlayProgress?,
	prefs: OverlayPrefs,
	fg: Color
) {
	if (camera == null || ringProgress == null) return
	val ringColor =
		remember(prefs.cameraRingColor, fg) {
			if (prefs.cameraRingColor.isEmpty()) {
				fg
			} else {
				runCatching { Color(android.graphics.Color.parseColor(prefs.cameraRingColor)) }.getOrElse { fg }
			}
		}
	val stroke = prefs.cameraRingStrokeDp.dp
	if (ringProgress.indeterminate) {
		val infinite = rememberInfiniteTransition(label = "ringSpin")
		val spin by
			infinite.animateFloat(
				initialValue = 0f,
				targetValue = 360f,
				animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
				label = "ringSpin"
			)
		RingCanvas(camera, stroke, ringColor, spin - 90f, 90f, Modifier.align(Alignment.TopStart))
	} else {
		val fraction by
			animateFloatAsState(
				targetValue = ringProgress.fraction,
				animationSpec = tween(400),
				label = "ringFraction"
			)
		RingCanvas(camera, stroke, ringColor, -90f, 360f * fraction, Modifier.align(Alignment.TopStart))
	}
}

/**
 * Draws the camera-clearance track and the progress arc around the cutout.
 * Positioned so the circle is centred on the camera, whatever the window origin.
 */
@Composable
private fun RingCanvas(
	camera: CameraGeometry,
	stroke: Dp,
	color: Color,
	startAngle: Float,
	sweep: Float,
	modifier: Modifier
) {
	val density = LocalDensity.current
	val strokePx = with(density) { stroke.toPx() }
	val sizePx = camera.radius * 2 + strokePx * 2
	val track = color.copy(alpha = color.alpha * 0.2f)
	Canvas(
		modifier =
			modifier
				.offset {
					IntOffset(
						(camera.centerX - camera.radius - strokePx).roundToInt(),
						(camera.centerY - camera.radius - strokePx).roundToInt()
					)
				}.size(with(density) { sizePx.toDp() })
	) {
		drawArc(track, 0f, 360f, useCenter = false, style = Stroke(strokePx))
		if (sweep > 0f) {
			drawArc(color, startAngle, sweep.coerceAtMost(360f), useCenter = false, style = Stroke(strokePx))
		}
	}
}

/** Base text size with a relative offset, in sp (e.g. clock = base, date = base - 1). */
private fun OverlayPrefs.textSp(delta: Int = 0): TextUnit = (fontSizeSp + delta).coerceIn(8, 28).sp

private val OverlayPrefs.fontWeightValue: FontWeight
	get() =
		when (fontWeightName) {
			"BOLD" -> FontWeight.Bold
			"MEDIUM" -> FontWeight.Medium
			else -> FontWeight.Normal
		}

/** Ticks aligned on [intervalMs] boundaries while [enabled]; frozen otherwise. */
@Composable
private fun rememberTick(
	intervalMs: Long,
	enabled: Boolean
): Long {
	var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
	LaunchedEffect(intervalMs, enabled) {
		if (!enabled) return@LaunchedEffect
		// Refresh before the first wait: `now` still holds the pre-sleep value,
		// and the first boundary can be up to a full interval away.
		now = System.currentTimeMillis()
		while (true) {
			val t = System.currentTimeMillis()
			delay(((t / intervalMs + 1) * intervalMs - t).coerceAtLeast(0))
			now = System.currentTimeMillis()
		}
	}
	return now
}

private val tnum = TextStyle(fontFeatureSettings = "tnum")

/**
 * Renders [pattern] at [tick], logging a bad pattern once instead of letting
 * the frame crash on every recomposition.
 */
@Composable
private fun formattedText(
	tick: Long,
	pattern: String,
	what: String
): String {
	val context = LocalContext.current
	val errorFormat = stringResource(R.string.log_error)
	return remember(tick, pattern) {
		try {
			SimpleDateFormat(pattern, Locale.getDefault()).format(Date(tick))
		} catch (e: Exception) {
			Log.w(TAG, "$what format", e)
			LogStore.appendOnce(
				context,
				"$TAG#${what}Format#$pattern",
				String.format(errorFormat, "bad $what format: ${e.message}")
			)
			""
		}
	}
}

/** Shared clock/date typography: one line, optional click, tabular figures when asked. */
@Composable
private fun WidgetText(
	prefs: OverlayPrefs,
	text: String,
	fontSize: TextUnit,
	fg: Color,
	numeric: Boolean,
	onClick: () -> Unit
) {
	Text(
		text = text,
		fontSize = fontSize,
		fontWeight = prefs.fontWeightValue,
		color = fg,
		style = if (numeric) tnum else LocalTextStyle.current,
		maxLines = 1,
		overflow = TextOverflow.Ellipsis,
		modifier =
			Modifier.then(
				if (prefs.interactive) Modifier.clickable(onClick = onClick) else Modifier
			)
	)
}

@Composable
private fun ClockWidget(
	prefs: OverlayPrefs,
	screenOn: Boolean,
	fg: Color,
	onClick: () -> Unit
) {
	val tick = rememberTick(if (prefs.hasSeconds()) 1000L else 60_000L, screenOn)
	WidgetText(
		prefs = prefs,
		text = formattedText(tick, prefs.effectiveTimeFormat(), "time"),
		fontSize = prefs.textSp(),
		fg = fg,
		numeric = true,
		onClick = onClick
	)
}

@Composable
private fun DateWidget(
	prefs: OverlayPrefs,
	screenOn: Boolean,
	fg: Color,
	onClick: () -> Unit
) {
	if (!prefs.showDate) return
	val tick = rememberTick(60_000L, screenOn)
	WidgetText(
		prefs = prefs,
		text = formattedText(tick, prefs.dateFormat, "date"),
		fontSize = prefs.textSp(-1),
		fg = fg,
		numeric = false,
		onClick = onClick
	)
}

@Composable
private fun IconImage(
	res: Int,
	desc: String,
	tint: Color,
	sizeDp: Int = 16
) {
	Image(
		painter = painterResource(res),
		contentDescription = desc,
		colorFilter = ColorFilter.tint(tint),
		modifier = Modifier.size(sizeDp.dp)
	)
}

@Composable
private fun NotifWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	if (!prefs.showNotifs || !state.notifsEnabled || state.notifs.isEmpty()) return
	val icons = state.notifs.take(prefs.maxNotifs.coerceIn(1, 8))
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(3.dp)
	) {
		for (entry in icons) {
			key(entry.pkg) {
				if (entry.bitmap != null) {
					Image(
						bitmap = entry.bitmap,
						contentDescription = entry.pkg,
						modifier = Modifier.size(16.dp)
					)
				} else {
					IconImage(R.drawable.ic_signal, entry.pkg, fg)
				}
			}
		}
	}
}

@Composable
private fun LabeledIcon(
	iconRes: Int,
	iconDesc: String,
	text: String,
	fg: Color,
	fontSize: TextUnit,
	fontWeight: FontWeight,
	maxWidthDp: Int? = null,
	startPadDp: Int = 4
) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		IconImage(iconRes, iconDesc, fg, 14)
		Text(
			text = text,
			fontSize = fontSize,
			fontWeight = fontWeight,
			color = fg,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier =
				Modifier
					.padding(start = startPadDp.dp)
					.then(if (maxWidthDp != null) Modifier.widthIn(max = maxWidthDp.dp) else Modifier)
		)
	}
}

@Composable
private fun CalendarWidget(
	prefs: OverlayPrefs,
	text: String,
	fg: Color
) {
	LabeledIcon(
		iconRes = R.drawable.ic_calendar,
		iconDesc = stringResource(R.string.cd_next_event),
		text = text,
		fg = fg,
		fontSize = prefs.textSp(-1),
		fontWeight = prefs.fontWeightValue,
		maxWidthDp = 160
	)
}

@Composable
private fun MediaWidget(
	prefs: OverlayPrefs,
	text: String,
	fg: Color
) {
	LabeledIcon(
		iconRes = R.drawable.ic_media,
		iconDesc = stringResource(R.string.cd_now_playing),
		text = text,
		fg = fg,
		fontSize = prefs.textSp(-2),
		fontWeight = prefs.fontWeightValue,
		maxWidthDp = 180,
		startPadDp = 3
	)
}

@Composable
private fun BandwidthWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	if (!prefs.showBandwidth || state.bandwidthText.isEmpty()) return
	Row(verticalAlignment = Alignment.CenterVertically) {
		if (prefs.bandwidthMerged) {
			IconImage(R.drawable.ic_swap, stringResource(R.string.cd_bandwidth), fg, 14)
		} else {
			IconImage(R.drawable.ic_arrow_down, stringResource(R.string.cd_download), fg, 14)
			IconImage(R.drawable.ic_arrow_up, stringResource(R.string.cd_upload), fg, 14)
		}
		Text(
			text = state.bandwidthText,
			fontSize = prefs.textSp(-2),
			fontWeight = prefs.fontWeightValue,
			color = fg,
			modifier = Modifier.padding(start = 2.dp)
		)
	}
}

@Composable
private fun AlarmWidget(
	prefs: OverlayPrefs,
	text: String,
	fg: Color
) {
	LabeledIcon(
		iconRes = R.drawable.ic_alarm,
		iconDesc = stringResource(R.string.cd_next_alarm),
		text = text,
		fg = fg,
		fontSize = prefs.textSp(-2),
		fontWeight = prefs.fontWeightValue,
		startPadDp = 2
	)
}

/**
 * One connectivity icon: visible while its toggle is on, the underlying state
 * applies, and airplane mode has not silenced its radio.
 */
private data class Indicator(
	val icon: Int,
	val label: Int,
	val shown: Boolean,
	val active: Boolean,
	val radio: Boolean = false,
	val typeLabel: Boolean = false
) {
	fun isVisible(airplaneOff: Boolean): Boolean = shown && active && (!radio || airplaneOff)
}

/** Every icon the connectivity widget can draw, in bar order. */
private fun connectivityIndicators(prefs: OverlayPrefs, state: OverlayBarState): List<Indicator> =
	listOf(
		Indicator(R.drawable.ic_plane, R.string.cd_airplane_mode, prefs.showAirplane, state.airplane),
		Indicator(R.drawable.ic_wifi, R.string.cd_wifi, prefs.showWifi, state.wifi, radio = true),
		Indicator(R.drawable.ic_signal, R.string.cd_mobile_data, prefs.showMobileData, state.mobile, radio = true, typeLabel = true),
		Indicator(R.drawable.ic_bt, R.string.cd_bluetooth, prefs.showBluetooth, state.bluetooth, radio = true),
		Indicator(R.drawable.ic_vpn, R.string.cd_vpn, prefs.showVpn, state.vpn, radio = true),
		Indicator(R.drawable.ic_hotspot, R.string.cd_hotspot, prefs.showHotspot, state.hotspot, radio = true),
		Indicator(R.drawable.ic_usb, R.string.cd_usb, prefs.showUsb, state.usbConnected),
		Indicator(R.drawable.ic_nfc, R.string.cd_nfc, prefs.showNfc, state.nfc, radio = true),
		Indicator(R.drawable.ic_gps, R.string.cd_gps, prefs.showGps, state.gps),
		// Unaffected by airplane mode: DND, data saver, auto-rotate, torch and USB are
		// device-wide states that stay meaningful while offline.
		Indicator(R.drawable.ic_dnd, R.string.cd_do_not_disturb, prefs.showDnd, state.dnd),
		Indicator(R.drawable.ic_data_saver, R.string.cd_data_saver, prefs.showDataSaver, state.dataSaver),
		Indicator(R.drawable.ic_rotation, R.string.cd_auto_rotate, prefs.showRotate, state.autoRotate),
		Indicator(R.drawable.ic_torch, R.string.cd_flashlight, prefs.showTorch, state.torch)
	)

@Composable
private fun ConnectivityWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	val visible = connectivityIndicators(prefs, state).filter { it.isVisible(airplaneOff = !state.airplane) }
	if (visible.isEmpty()) return
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(2.dp)
	) {
		visible.forEach { indicator ->
			if (indicator.typeLabel) {
				// Cellular: the bars track the signal level, the label the real
				// network type; a level the platform never reported (-1) keeps
				// the static drawable so the icon never turns into a blank.
				val level = state.signalLevel
				if (level >= 0) {
					SignalIcon(level, fg, stringResource(R.string.cd_signal_level, level))
				} else {
					IconImage(indicator.icon, stringResource(indicator.label), fg)
				}
				Text(
					text = state.mobileType.ifEmpty { "4G" },
					fontSize = prefs.textSp(-3),
					color = fg,
					modifier = Modifier.padding(start = 1.dp, end = 2.dp)
				)
			} else {
				IconImage(indicator.icon, stringResource(indicator.label), fg)
			}
		}
	}
}

@Composable
private fun BatteryWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	if (!prefs.showBattery && !prefs.showBatteryIcon) return
	Row(verticalAlignment = Alignment.CenterVertically) {
		if (prefs.showBatteryIcon) {
			BatteryIcon(
				level = state.batteryPct.coerceAtLeast(0),
				charging = state.batteryCharging,
				color = fg
			)
		}
		if (prefs.showBattery && prefs.showBatteryPct && state.batteryPct >= 0) {
			Text(
				text = "${state.batteryPct}%",
				fontSize = prefs.textSp(),
				fontWeight = prefs.fontWeightValue,
				color = fg,
				style = tnum,
				modifier = Modifier.padding(start = 3.dp)
			)
		}
	}
}
