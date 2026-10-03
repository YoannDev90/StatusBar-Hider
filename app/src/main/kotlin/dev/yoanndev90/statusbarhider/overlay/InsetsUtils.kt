package dev.yoanndev90.statusbarhider.overlay

import android.graphics.Rect
import android.os.Build
import android.view.View

/**
 * Notch (display cutout) helpers for the overlay bar.
 *
 * All framework reads are null-safe and version-guarded (cutout needs API 28+).
 */
object InsetsUtils {
	/** Bounding rects of the display cutout for the attached view (empty if none). */
	fun cutoutRects(view: View): List<Rect> {
		if (Build.VERSION.SDK_INT < 28) return emptyList()
		return try {
			view.rootWindowInsets
				?.displayCutout
				?.boundingRects
				?.toList() ?: emptyList()
		} catch (_: Exception) {
			emptyList()
		}
	}

	/**
	 * Width of a cutout intruding from the left or right screen edge.
	 * Centered notches / punch holes return 0 (they sit in the middle
	 * spacer, no padding needed).
	 */
	fun sideCutoutWidthPx(
		view: View,
		screenWidthPx: Int
	): Int {
		if (screenWidthPx <= 0) return 0
		val edge =
			cutoutRects(view).filter {
				it.top <= 0 && (it.left <= 0 || it.right >= screenWidthPx)
			}
		if (edge.isEmpty()) return 0
		return edge.maxOf { it.width() }.coerceAtLeast(0)
	}
}
