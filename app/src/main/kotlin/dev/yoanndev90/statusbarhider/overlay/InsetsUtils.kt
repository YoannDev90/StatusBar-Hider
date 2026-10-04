package dev.yoanndev90.statusbarhider.overlay

import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager

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

	/** Physical screen size in px ([WindowManager.maximumWindowMetrics] on API 30+). */
	@Suppress("DEPRECATION")
	fun screenSize(
		wm: WindowManager,
		resources: Resources
	): Pair<Int, Int> =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val bounds = wm.maximumWindowMetrics.bounds
			bounds.width() to bounds.height()
		} else {
			resources.displayMetrics.let { it.widthPixels to it.heightPixels }
		}

	/**
	 * Resolves the cutout geometry for [prefs], in screen coordinates.
	 *
	 * Auto mode reads the top cutout and applies the user's correction
	 * (nudge / optional size override); manual mode positions a virtual camera
	 * from the stored offsets. Returns null when there is nothing to draw:
	 * no cutout detected while auto-detect is on (API < 28 always returns null).
	 */
	fun resolveCamera(
		view: View,
		screenWidth: Int,
		screenHeight: Int,
		prefs: OverlayPrefs
	): CameraGeometry? {
		val density = view.resources.displayMetrics.density
		val gapPx = prefs.cameraGapDp * density
		val centerX: Float
		val centerY: Float
		val diameter: Float
		if (prefs.cameraAutoDetect) {
			val rect = topCutoutRects(view, screenWidth).maxByOrNull { maxOf(it.width(), it.height()) } ?: return null
			centerX = rect.exactCenterX() + prefs.cameraNudgeXDp * density
			centerY = rect.exactCenterY() + prefs.cameraNudgeYDp * density
			diameter =
				if (prefs.cameraSizeDp > 0) {
					prefs.cameraSizeDp * density
				} else {
					maxOf(rect.width(), rect.height()).toFloat()
				}
		} else {
			centerX = prefs.cameraOffsetXPct / 100f * screenWidth
			centerY = prefs.cameraOffsetYDp * density
			diameter = (if (prefs.cameraSizeDp > 0) prefs.cameraSizeDp else MANUAL_SIZE_DP_FALLBACK) * density
		}
		val radius = diameter / 2f + gapPx
		val anchor =
			when {
				centerX - radius <= 0f -> CameraAnchor.Start
				centerX + radius >= screenWidth -> CameraAnchor.End
				else -> CameraAnchor.Center
			}
		return CameraGeometry(centerX, centerY, radius, screenWidth, screenHeight, anchor)
	}

	/** Cutouts touching the top edge of the screen (punch-holes, islands and side notches). */
	private fun topCutoutRects(
		view: View,
		screenWidth: Int
	): List<Rect> =
		cutoutRects(view).filter {
			it.top <= 0 && it.width() > 0 && it.height() > 0 && it.left < screenWidth && it.right > 0
		}

	/** Diameter used in manual mode when the stored size is 0 ("auto"). */
	private const val MANUAL_SIZE_DP_FALLBACK = 12
}
