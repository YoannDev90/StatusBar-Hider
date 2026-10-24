package dev.yoanndev90.statusbarhider.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.WidgetId

/**
 * Places the widget row: alone under the lock screen's clock-only mode, split
 * around a centred cutout, or as one row that edge-anchored notches get pushed
 * clear of.
 */
@Composable
internal fun BoxScope.BarLayout(
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
