package dev.yoanndev90.statusbarhider.overlay.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState

@Composable
internal fun BatteryWidget(
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
