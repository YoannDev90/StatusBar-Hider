package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs

@Composable
internal fun ClockWidget(
	prefs: OverlayPrefs,
	screenOn: Boolean,
	fg: Color,
	onClick: () -> Unit
) {
	val tick = rememberTick(if (prefs.showSeconds) 1000L else 60_000L, screenOn)
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
internal fun DateWidget(
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
