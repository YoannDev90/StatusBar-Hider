package dev.yoanndev90.statusbarhider.core.settings

/**
 * One-shot navigation payload: the key of the setting the Debug index wants
 * on screen. Written right before navigating and consumed by the target
 * section screen on its first composition, so the jump needs no route
 * change — a route stays exactly "settings/" + section id.
 */
object PendingFocus {
	/** Spec key the next section screen should scroll to, null for a plain navigation. */
	var key: String? = null

	/** Reads and clears the pending key, so a rotation or a back-forward never re-scrolls. */
	fun consume(): String? = key.also { key = null }
}
