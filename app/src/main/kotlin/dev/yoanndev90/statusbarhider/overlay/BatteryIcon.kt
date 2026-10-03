package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Battery level silhouette: outline + proportional fill + bolt when charging.
 * Geometry is the port of the former BatteryView (22x12dp, 3px stroke at
 * xxhdpi), expressed in dp so it scales with density instead of staying a
 * fixed number of pixels.
 */
@Composable
fun BatteryIcon(
	level: Int,
	charging: Boolean,
	color: Color,
	modifier: Modifier = Modifier
) {
	Canvas(modifier = modifier.size(22.dp, 12.dp)) {
		val w = size.width
		val h = size.height
		if (w <= 0f || h <= 0f) return@Canvas

		// 1dp == 3px at xxhdpi, i.e. the original stroke width.
		val px = 1.dp.toPx()
		val inset = px * 0.5f
		val tipW = w * 0.08f
		val body = Rect(inset, inset, w - tipW - px / 3f, h - inset)
		drawRoundRect(
			color = color,
			topLeft = body.topLeft,
			size = body.size,
			cornerRadius = CornerRadius(px, px),
			style = Stroke(width = px)
		)
		drawRect(
			color = color,
			topLeft = Offset(w - tipW, h * 0.32f),
			size = Size(tipW - px / 3f, h * 0.36f)
		)
		val frac = level.coerceIn(0, 100) / 100f
		if (frac > 0f) {
			val pad = px * 4f / 3f
			val fillW = (body.width - pad * 2) * frac
			drawRect(
				color = color,
				topLeft = Offset(body.left + pad, body.top + pad),
				size = Size(fillW, body.height - pad * 2)
			)
		}
		if (charging) {
			val cx = body.center.x
			val cy = body.center.y
			val bolt =
				Path().apply {
					moveTo(cx + px, cy - 2 * px)
					lineTo(cx - px, cy + px / 3f)
					lineTo(cx, cy + px / 3f)
					lineTo(cx - px, cy + 2 * px)
					lineTo(cx + px, cy - px / 3f)
					lineTo(cx, cy - px / 3f)
					close()
				}
			drawPath(bolt, color)
		}
	}
}
