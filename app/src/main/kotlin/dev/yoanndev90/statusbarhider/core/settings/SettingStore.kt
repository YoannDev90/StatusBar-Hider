package dev.yoanndev90.statusbarhider.core.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * One settings file behind its [SettingSpec] registry: observes its state,
 * applies writes, and normalizes every value through the specs it declares.
 *
 * [normalize] is generated from the registry instead of hand-written, so a
 * range can never drift from the slider that edits it.
 */
abstract class SettingStore<S : Any> {
	/** Live state; every writer of the file funnels through one repository. */
	abstract val state: StateFlow<S>

	/** Every setting of this file, in display order. */
	abstract val specs: List<SettingSpec<*, S>>

	/** The all-defaults instance: reset target and base of the guard tests. */
	abstract val default: S

	/** Applies [transform] to the live state and schedules the write. */
	abstract fun update(transform: S.() -> S)

	/** Replaces the whole state (import, global reset). */
	abstract fun replace(value: S)

	/** Clamps every field through its own spec; the app's one normalizer. */
	fun normalize(value: S): S = specs.fold(value) { p, spec -> spec.set(p, spec.coerce(spec.get(p))) }
}
