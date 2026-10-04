package dev.yoanndev90.statusbarhider.overlay

/** Which screen edge the cutout hugs; `Center` means a top punch-hole / island. */
enum class CameraAnchor {
	Center,
	Start,
	End
}

/**
 * Resolved geometry of the display cutout ("camera") in screen coordinates.
 *
 * [radius] already folds in the configured gap, so a single value drives both
 * the hole left in the bar ([slotLeft] .. [slotRight]) and the progress ring
 * drawn around the cutout:
 *
 *     radius = max(rect.w, rect.h) / 2 + gap
 *
 * That guarantees the ring never crosses a square punch-hole (circumscribes
 * it) nor a wide island (clears its long axis).
 */
data class CameraGeometry(
	val centerX: Float,
	val centerY: Float,
	val radius: Float,
	val screenWidth: Int,
	val screenHeight: Int,
	val anchor: CameraAnchor
) {
	/** Left edge of the region the bar must leave free (px). */
	val slotLeft: Float
		get() = centerX - radius

	/** Right edge of the region the bar must leave free (px). */
	val slotRight: Float
		get() = centerX + radius
}
