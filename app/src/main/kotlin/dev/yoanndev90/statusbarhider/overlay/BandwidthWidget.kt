package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs

@Composable
internal fun BandwidthWidget(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color
) {
	val rx = state.bandwidthRx ?: return
	if (!prefs.showBandwidth) return
	val valueStyle =
		TextStyle(
			fontSize = prefs.textSp(-2),
			fontWeight = prefs.fontWeightValue,
			fontFeatureSettings = "tnum"
		)
	val unitStyle =
		TextStyle(
			fontSize = prefs.textSp(-6),
			fontWeight = prefs.fontWeightValue,
			fontFeatureSettings = "tnum"
		)
	// Measured once per typography change, never per sample, so the slot is constant.
	val slotWidth = speedSlotWidth(valueStyle)
	Row(verticalAlignment = Alignment.CenterVertically) {
		if (prefs.bandwidthMerged) {
			IconImage(R.drawable.ic_swap, stringResource(R.string.cd_bandwidth), fg, 14)
			SpeedColumn(rx, valueStyle, unitStyle, fg, slotWidth)
		} else {
			IconImage(R.drawable.ic_arrow_down, stringResource(R.string.cd_download), fg, 14)
			SpeedColumn(rx, valueStyle, unitStyle, fg, slotWidth)
			IconImage(R.drawable.ic_arrow_up, stringResource(R.string.cd_upload), fg, 14)
			state.bandwidthTx?.let { SpeedColumn(it, valueStyle, unitStyle, fg, slotWidth) }
		}
	}
}

/** Widest figure the sampler can emit; the digit slot is sized for this. */
private const val BANDWIDTH_SLOT_SAMPLE = "1023.9"

/** Fixed digit width, sized from [BANDWIDTH_SLOT_SAMPLE], so samples never shift the bar. */
@Composable
private fun speedSlotWidth(valueStyle: TextStyle): Dp {
	val measurer = rememberTextMeasurer()
	val density = LocalDensity.current
	return remember(measurer, density, valueStyle) {
		with(density) {
			measurer
				.measure(BANDWIDTH_SLOT_SAMPLE, style = valueStyle)
				.size
				.width
				.toDp()
		}
	}
}

/** Value over its unit (e.g. 3.4 above M/s), digits pinned to a fixed-width slot. */
@Composable
private fun SpeedColumn(
	speed: OverlaySpeed,
	valueStyle: TextStyle,
	unitStyle: TextStyle,
	fg: Color,
	slotWidth: Dp
) {
	Column(
		modifier = Modifier.padding(start = 2.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(
			text = speed.value,
			style = valueStyle,
			color = fg,
			maxLines = 1,
			textAlign = TextAlign.Center,
			modifier = Modifier.width(slotWidth)
		)
		Text(
			text = "${speed.unit}/s",
			style = unitStyle,
			color = fg,
			maxLines = 1,
			textAlign = TextAlign.Center,
			modifier = Modifier.width(slotWidth)
		)
	}
}
