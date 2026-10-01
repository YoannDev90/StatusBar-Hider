package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View

/**
 * Notch (display cutout) + rounded-corners helpers for the overlay bar.
 *
 * Pure computation lives in [horizontalPadding] so it stays unit-testable
 * without a device. All framework reads are null-safe and version-guarded
 * (rounded corners need API 31+, cutout needs API 28+).
 */
object InsetsUtils {
	/** System status bar height (0 if unknown). */
	fun statusBarHeightPx(context: Context): Int {
		val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
		return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
	}

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

	/** Width of a top-edge (notch) cutout, 0 when none. Punch-hole cameras centered in the bar also count. */
	fun topCutoutWidthPx(view: View): Int {
		val top = cutoutRects(view).filter { it.top <= 0 }
		if (top.isEmpty()) return 0
		val left = top.minOf { it.left }
		val right = top.maxOf { it.right }
		return (right - left).coerceAtLeast(0)
	}

	/** Height of the top cutout, 0 when none. */
	fun topCutoutHeightPx(view: View): Int {
		val top = cutoutRects(view).filter { it.top <= 0 }
		if (top.isEmpty()) return 0
		return top.maxOf { it.height() }.coerceAtLeast(0)
	}

	/** Max radius of the top-left / top-right rounded corners, 0 when unknown or API < 31. */
	fun roundedCornerRadiusPx(view: View): Int {
		if (Build.VERSION.SDK_INT < 31) return 0
		return try {
			val insets = view.rootWindowInsets ?: return 0
			// Reflection keeps this compilable on older compileSdk versions.
			val corners = insets.javaClass.getMethod("getRoundedCorners").invoke(insets) ?: return 0
			val cornersClass = corners.javaClass
			val rcClass = Class.forName("android.view.RoundedCorners")
			val posTopLeft = rcClass.getField("POSITION_TOP_LEFT").getInt(null)
			val posTopRight = rcClass.getField("POSITION_TOP_RIGHT").getInt(null)
			val getCorner = cornersClass.getMethod("getRoundedCorner", Int::class.javaPrimitiveType)
			val radii =
				listOf(posTopLeft, posTopRight).map { pos ->
					val corner = getCorner.invoke(corners, pos) ?: return@map 0
					try {
						corner.javaClass.getMethod("getRadius").invoke(corner) as? Int ?: 0
					} catch (_: Exception) {
						0
					}
				}
			radii.maxOrNull() ?: 0
		} catch (_: Exception) {
			0
		}
	}

	/**
	 * Horizontal padding so bar content clears rounded corners and a
	 * side-hugging cutout. Pure function, unit-tested.
	 *
	 * @param cornerRadiusPx max top corner radius
	 * @param sideCutoutWidthPx cutout width intruding from the left/right edge (0 if centered/absent)
	 * @param minPaddingPx baseline padding (e.g. 12dp in px)
	 */
	fun horizontalPadding(
		cornerRadiusPx: Int,
		sideCutoutWidthPx: Int,
		minPaddingPx: Int
	): Int = maxOf(cornerRadiusPx, sideCutoutWidthPx, minPaddingPx).coerceAtLeast(0)

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
