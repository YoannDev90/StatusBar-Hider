package dev.yoanndev90.statusbarhider.overlay

import java.util.Locale

/**
 * Formatting of the figures the bar reads from the system: throughput and
 * battery level. Nothing here is a setting, so it lives apart from
 * [OverlayPrefs] and its codec.
 */
object IndicatorFormats {
	/** Same figure split as value + scale unit, for the stacked bar layout. */
	fun speedParts(bytesPerSecond: Long): OverlaySpeed =
		when {
			bytesPerSecond >= 1_048_576L ->
				OverlaySpeed(String.format(Locale.ROOT, "%.1f", bytesPerSecond / 1_048_576.0), "M")
			bytesPerSecond >= 1_024L ->
				OverlaySpeed(String.format(Locale.ROOT, "%.0f", bytesPerSecond / 1_024.0), "K")
			else -> OverlaySpeed(bytesPerSecond.coerceAtLeast(0).toString(), "B")
		}

	/** Battery % from a sticky ACTION_BATTERY_CHANGED extras pair. */
	fun batteryPct(
		level: Int,
		scale: Int
	): Int = if (level >= 0 && scale > 0) (level * 100) / scale else -1
}
