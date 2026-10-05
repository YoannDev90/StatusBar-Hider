package dev.yoanndev90.statusbarhider.overlay

import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local snapshot of notification icons for the overlay bar.
 * Written by [NotifListenerService], collected by [StatusBarOverlayService].
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

	/** One immutable snapshot: the icon list plus the winning progress. */
	data class Snapshot(
		val entries: List<Entry>,
		val progress: Progress?
	)

	private val _snapshots = MutableStateFlow(Snapshot(emptyList(), null))

	/** Emits on every [update]; collect instead of registering listeners. */
	val snapshots: StateFlow<Snapshot> = _snapshots.asStateFlow()

	fun update(
		all: List<Entry>,
		progress: Progress?
	) {
		_snapshots.value = Snapshot(all, progress)
	}

	/** Latest icons (pull side of [snapshots]). */
	fun snapshot(): List<Entry> = _snapshots.value.entries

	/** Latest winning progress (pull side of [snapshots]). */
	fun progress(): Progress? = _snapshots.value.progress
}
