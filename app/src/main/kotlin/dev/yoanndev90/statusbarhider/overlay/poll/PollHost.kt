package dev.yoanndev90.statusbarhider.overlay.poll

import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs

/**
 * The service-side view the poll loops read and call into.
 *
 * Implemented by `StatusBarOverlayService`, so [OverlayPoller] stays free of
 * Android types and its constructor stays short. The reader methods are the
 * actions a loop performs on each tick; the two `Unit` callbacks cover the
 * foreground poll's edge cases (missing usage access, loop not running).
 */
internal interface PollHost {
	/** Live prefs the loops read (intervals, toggles, hidden apps). */
	val currentPrefs: OverlayPrefs

	/** Screen lit: every loop needs it, several also exit on it. */
	val isScreenOn: Boolean

	/** Usage access is granted; without it the foreground poll stays idle. */
	val usageAccessGranted: Boolean

	/** Alarm / calendar / connectivity refresh (general poll). */
	suspend fun refreshPolled()

	/** One bandwidth sample. */
	suspend fun sampleBandwidth()

	/** Cellular label / signal-level refresh. */
	suspend fun refreshMobile()

	/** Recompute the suppression state from the foreground app. */
	suspend fun refreshSuppressed()

	/** Apply the periodic burn-in shift. */
	suspend fun shiftForBurnIn()

	/** Usage access is missing: log the once-per-run hint. */
	fun onUsageAccessMissing()

	/** The foreground poll is not running: make sure the bar is not suppressed. */
	fun ensureNotSuppressed()
}
