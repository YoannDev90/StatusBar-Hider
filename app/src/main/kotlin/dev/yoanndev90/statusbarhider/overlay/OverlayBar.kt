package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yoanndev90.statusbarhider.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * System-wide custom status bar, rendered with Compose inside a
 * TYPE_APPLICATION_OVERLAY window. Layout mirrors the former
 * view_custom_status_bar.xml (sizes, paddings, order).
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
	val sideCutoutDp =
		with(density) { state.sideCutoutPx.toDp() }
	val padStart = maxOf(prefs.padStartDp.dp, sideCutoutDp)
	val padEnd = maxOf(prefs.padEndDp.dp, sideCutoutDp)

	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.background(Color(prefs.backgroundColor()))
				.padding(
					start = padStart,
					top = prefs.padTopDp.dp,
					end = padEnd,
					bottom = prefs.padBottomDp.dp
				),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(prefs.widgetSpacingDp.dp)
	) {
		for (id in prefs.widgetOrder) {
			key(id) {
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
		while (true) {
			val t = System.currentTimeMillis()
			delay(((t / intervalMs + 1) * intervalMs - t).coerceAtLeast(0))
			now = System.currentTimeMillis()
		}
	}
	return now
}

private val tnum = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun ClockWidget(
	prefs: OverlayPrefs,
	screenOn: Boolean,
	fg: Color,
	onClick: () -> Unit
) {
	val tick = rememberTick(if (prefs.hasSeconds()) 1000L else 60_000L, screenOn)
	val text =
		remember(tick, prefs.effectiveTimeFormat()) {
			try {
				SimpleDateFormat(prefs.effectiveTimeFormat(), Locale.getDefault()).format(Date(tick))
			} catch (_: Exception) {
				""
			}
		}
	Text(
		text = text,
		fontSize = prefs.textSp(),
		fontWeight = prefs.fontWeightValue,
		color = fg,
		style = tnum,
		modifier =
			Modifier.then(
				if (prefs.interactive) Modifier.clickable(onClick = onClick) else Modifier
			)
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
	val text =
		remember(tick, prefs.dateFormat) {
			try {
				SimpleDateFormat(prefs.dateFormat, Locale.getDefault()).format(Date(tick))
			} catch (_: Exception) {
				""
			}
		}
	Text(
		text = text,
		fontSize = prefs.textSp(-1),
		fontWeight = prefs.fontWeightValue,
		color = fg,
		modifier =
			Modifier.then(
				if (prefs.interactive) Modifier.clickable(onClick = onClick) else Modifier
			)
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
		iconDesc = "Next event",
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
		iconDesc = "Now playing",
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
			IconImage(R.drawable.ic_swap, "Bandwidth", fg, 14)
		} else {
			IconImage(R.drawable.ic_arrow_down, "Download", fg, 14)
			IconImage(R.drawable.ic_arrow_up, "Upload", fg, 14)
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
		iconDesc = "Next alarm",
		text = text,
		fg = fg,
		fontSize = prefs.textSp(-2),
		fontWeight = prefs.fontWeightValue,
		startPadDp = 2
	)
}

@Composable
private fun ConnectivityWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	val showRest = !state.airplane
	val items = mutableListOf<@Composable () -> Unit>()
	if (state.airplane && prefs.showAirplane) {
		items += { IconImage(R.drawable.ic_plane, "Airplane mode", fg) }
	}
	if (showRest && prefs.showWifi && state.wifi) {
		items += { IconImage(R.drawable.ic_wifi, "WiFi", fg) }
	}
	if (showRest && prefs.showMobileData && state.mobile) {
		items += { IconImage(R.drawable.ic_signal, "Mobile data", fg) }
		items += {
			Text(
				text = state.mobileType.ifEmpty { "4G" },
				fontSize = prefs.textSp(-3),
				color = fg,
				modifier = Modifier.padding(start = 1.dp, end = 2.dp)
			)
		}
	}
	if (showRest && prefs.showBluetooth && state.bluetooth) {
		items += { IconImage(R.drawable.ic_bt, "Bluetooth", fg) }
	}
	if (showRest && prefs.showVpn && state.vpn) {
		items += { IconImage(R.drawable.ic_vpn, "VPN", fg) }
	}
	if (showRest && prefs.showHotspot && state.hotspot) {
		items += { IconImage(R.drawable.ic_hotspot, "Hotspot", fg) }
	}
	if (prefs.showUsb && state.usbConnected) {
		items += { IconImage(R.drawable.ic_usb, "USB", fg) }
	}
	if (showRest && prefs.showNfc && state.nfc) {
		items += { IconImage(R.drawable.ic_nfc, "NFC", fg) }
	}
	if (prefs.showGps && state.gps) {
		items += { IconImage(R.drawable.ic_gps, "GPS", fg) }
	}
	// Unaffected by airplane mode: DND, data saver, auto-rotate and torch are
	// device-wide states that stay meaningful while offline.
	if (prefs.showDnd && state.dnd) {
		items += { IconImage(R.drawable.ic_dnd, "Do not disturb", fg) }
	}
	if (prefs.showDataSaver && state.dataSaver) {
		items += { IconImage(R.drawable.ic_data_saver, "Data saver", fg) }
	}
	if (prefs.showRotate && state.autoRotate) {
		items += { IconImage(R.drawable.ic_rotation, "Auto-rotate", fg) }
	}
	if (prefs.showTorch && state.torch) {
		items += { IconImage(R.drawable.ic_torch, "Flashlight", fg) }
	}
	if (items.isEmpty()) return
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(2.dp)
	) {
		items.forEach { it() }
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
