package dev.yoanndev90.statusbarhider.overlay.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.model.CameraGeometry
import dev.yoanndev90.statusbarhider.overlay.model.OverlayProgress
import kotlin.math.roundToInt

/**
 * Camera-clearance track plus the notification progress arc, aligned on the
 * cutout. A preview run spins a full circle so the ring can be positioned
 * without a real progress bar.
 */
@Composable
internal fun BoxScope.CameraRing(
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
