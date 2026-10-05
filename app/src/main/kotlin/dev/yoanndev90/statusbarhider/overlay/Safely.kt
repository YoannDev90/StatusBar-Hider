package dev.yoanndev90.statusbarhider.overlay

/**
 * Runs [block], swallowing any platform error. Used on paths where a failure
 * only costs a refresh (unregistering a receiver, removing a window view…)
 * instead of crashing the overlay service.
 */
internal inline fun runSafely(block: () -> Unit) {
	try {
		block()
	} catch (_: Exception) {
	}
}
