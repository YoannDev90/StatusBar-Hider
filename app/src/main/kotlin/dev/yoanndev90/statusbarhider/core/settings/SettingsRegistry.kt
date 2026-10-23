package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.core.settings.specs.OverlaySpecs

/**
 * Every setting of every file, described once. UI, backup policy and resets
 * read this registry instead of hard-coding field lists.
 *
 * Live store instances need a Context (their repository holds it); they join
 * once the generic renderer asks for them. The specs are context-free and
 * live here now.
 */
object SettingsRegistry {
	/** Every described setting, across every registered file. */
	val allSpecs: List<SettingSpec<*, *>> = OverlaySpecs.all
}
