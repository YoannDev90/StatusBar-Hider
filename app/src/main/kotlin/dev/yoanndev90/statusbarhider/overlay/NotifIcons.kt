package dev.yoanndev90.statusbarhider.overlay

import android.graphics.drawable.Drawable

/**
 * Process-local snapshot of notification icons for the overlay bar.
 * Written by [NotifListenerService], read by [StatusBarOverlayService].
 */
object NotifIcons {
	data class Entry(
		val pkg: String,
		val icon: Drawable?
	)

	private var entries: List<Entry> = emptyList()
	private val listeners = mutableSetOf<() -> Unit>()

	@Synchronized
	fun update(all: List<Entry>) {
		entries = all
		listeners.toList().forEach { it() }
	}

	@Synchronized
	fun snapshot(): List<Entry> = entries

	@Synchronized
	fun addListener(l: () -> Unit) {
		listeners += l
	}

	@Synchronized
	fun removeListener(l: () -> Unit) {
		listeners -= l
	}
}
