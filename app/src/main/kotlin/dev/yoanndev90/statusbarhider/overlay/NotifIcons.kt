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

	/** The winning notification progress: ongoing first, otherwise most recent. */
	data class Progress(
		val pkg: String,
		val fraction: Float,
		val indeterminate: Boolean
	)

	private var entries: List<Entry> = emptyList()
	private var currentProgress: Progress? = null
	private val listeners = mutableSetOf<() -> Unit>()

	@Synchronized
	fun update(
		all: List<Entry>,
		progress: Progress?
	) {
		entries = all
		currentProgress = progress
		listeners.toList().forEach { it() }
	}

	@Synchronized
	fun snapshot(): List<Entry> = entries

	@Synchronized
	fun progress(): Progress? = currentProgress

	@Synchronized
	fun addListener(l: () -> Unit) {
		listeners += l
	}

	@Synchronized
	fun removeListener(l: () -> Unit) {
		listeners -= l
	}
}
