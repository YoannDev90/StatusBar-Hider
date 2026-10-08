package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Cellular signal strength: [BAR_COUNT] ascending bars, [level] of them solid
 * and the rest dimmed so the icon still reads when the level is 0 or unknown.
 *
 * The glyph keeps the footprint of the drawable it replaces (Material
 * "signal_cellular_alt" inside a 16dp box): same width, same height, same
 * bottom-aligned bars, so the row layout does not move.
 */
@Composable
fun SignalIcon(
	level: Int,
	color: Color,
	contentDescription: String,
	modifier: Modifier = Modifier
) {
	val desc = contentDescription
	Canvas(
		modifier =
			modifier
				.size(BOX_DP.dp)
				.semantics { this.contentDescription = desc }
	) {
		val w = size.width
		val h = size.height
		if (w <= 0f || h <= 0f) return@Canvas

		val glyphW = w * GLYPH_W_FRACTION
		val glyphH = h * GLYPH_H_FRACTION
		// Bars and gaps share one unit: 4 bars + 3 gaps, as in the drawable.
		val unit = glyphW / (BAR_COUNT * 2 - 1)
		val left = (w - glyphW) / 2f
		val bottom = (h + glyphH) / 2f
		val filled = level.coerceIn(0, BAR_COUNT)
		for (i in 0 until BAR_COUNT) {
			val barH = glyphH * (i + 1) / BAR_COUNT
			drawRect(
				color = if (i < filled) color else color.copy(alpha = color.alpha * EMPTY_ALPHA),
				topLeft = Offset(left + i * unit * 2f, bottom - barH),
				size = Size(unit, barH)
			)
		}
	}
}

private const val BOX_DP = 16
private const val BAR_COUNT = 4
private const val GLYPH_W_FRACTION = 0.625f
private const val GLYPH_H_FRACTION = 0.667f
private const val EMPTY_ALPHA = 0.3f
