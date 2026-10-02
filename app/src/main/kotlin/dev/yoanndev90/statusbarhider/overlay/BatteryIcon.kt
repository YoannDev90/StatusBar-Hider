package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Battery level silhouette: outline + proportional fill + bolt when charging.
 * Pixel-faithful port of the former BatteryView (22x12dp, 3px stroke).
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
		if (w <= 0 || h <= 0) return@Canvas
		val tipW = w * 0.08f
		val body = Rect(1.5f, 1.5f, w - tipW - 1f, h - 1.5f)
		drawRoundRect(
			color = color,
			topLeft = body.topLeft,
			size = body.size,
			cornerRadius = CornerRadius(3f, 3f),
			style = Stroke(width = 3f)
		)
		drawRect(
			color = color,
			topLeft = Offset(w - tipW, h * 0.32f),
			size = androidx.compose.ui.geometry
				.Size(tipW - 1f, h * 0.36f)
		)
		val frac = level.coerceIn(0, 100) / 100f
		if (frac > 0f) {
			val pad = 4f
			val fillW = (body.width - pad * 2) * frac
			drawRect(
				color = color,
				topLeft = Offset(body.left + pad, body.top + pad),
				size = androidx.compose.ui.geometry
					.Size(fillW, body.height - pad * 2)
			)
		}
		if (charging) {
			val cx = body.center.x
			val cy = body.center.y
			val bolt =
				Path().apply {
					moveTo(cx + 3f, cy - 6f)
					lineTo(cx - 3f, cy + 1f)
					lineTo(cx, cy + 1f)
					lineTo(cx - 3f, cy + 6f)
					lineTo(cx + 3f, cy - 1f)
					lineTo(cx, cy - 1f)
					close()
				}
			drawPath(bolt, color)
		}
	}
}
