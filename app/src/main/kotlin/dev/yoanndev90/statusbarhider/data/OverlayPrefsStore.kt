package dev.yoanndev90.statusbarhider.data

import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SettingStore
import dev.yoanndev90.statusbarhider.core.settings.specs.OverlaySpecs
import kotlinx.coroutines.flow.StateFlow

/**
 * Registry-facing facade over [OverlayPrefsRepository]: the repository stays
 * the single writer (its debounce and rollback untouched), this adds the spec
 * metadata and the generated normalizer the rest of the settings stack reads.
 */
class OverlayPrefsStore(
	private val repository: OverlayPrefsRepository
) : SettingStore<OverlayPrefs>() {
	override val state: StateFlow<OverlayPrefs> get() = repository.state

	override val specs = OverlaySpecs.all

	override val default: OverlayPrefs = OverlayPrefs()

	override fun update(transform: OverlayPrefs.() -> OverlayPrefs) {
		repository.update(transform)
	}

	override fun replace(value: OverlayPrefs) = repository.replace(value)
}
