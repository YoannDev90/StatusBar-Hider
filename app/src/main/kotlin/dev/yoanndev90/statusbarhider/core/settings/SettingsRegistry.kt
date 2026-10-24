package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.core.settings.sections.AboutSection
import dev.yoanndev90.statusbarhider.core.settings.sections.AppearanceSection
import dev.yoanndev90.statusbarhider.core.settings.sections.BackupSection
import dev.yoanndev90.statusbarhider.core.settings.sections.BatterySection
import dev.yoanndev90.statusbarhider.core.settings.sections.ClockSection
import dev.yoanndev90.statusbarhider.core.settings.sections.ConnectivitySection
import dev.yoanndev90.statusbarhider.core.settings.sections.CutoutSection
import dev.yoanndev90.statusbarhider.core.settings.sections.DebugDiffSection
import dev.yoanndev90.statusbarhider.core.settings.sections.DebugInfoSection
import dev.yoanndev90.statusbarhider.core.settings.sections.DebugPermissionsSection
import dev.yoanndev90.statusbarhider.core.settings.sections.DebugSection
import dev.yoanndev90.statusbarhider.core.settings.sections.GeneralSection
import dev.yoanndev90.statusbarhider.core.settings.sections.HiddenAppsSection
import dev.yoanndev90.statusbarhider.core.settings.sections.LockAppearanceSection
import dev.yoanndev90.statusbarhider.core.settings.sections.LockBehaviorSection
import dev.yoanndev90.statusbarhider.core.settings.sections.LockSection
import dev.yoanndev90.statusbarhider.core.settings.sections.LogsSection
import dev.yoanndev90.statusbarhider.core.settings.sections.NotificationSection
import dev.yoanndev90.statusbarhider.core.settings.sections.OemSection
import dev.yoanndev90.statusbarhider.core.settings.sections.ProfilesSection
import dev.yoanndev90.statusbarhider.core.settings.specs.OverlaySpecs
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs

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

	/**
	 * Every screen of the settings tree, in root-listing order: bar settings
	 * first, app-level ones after. Sections with no rows describe screens
	 * that are still hand-written (the lock hub and the app group); they
	 * still own the listing, the title and the search entry.
	 */
	val sections: List<SettingSection<OverlayPrefs>> =
		listOf(
			GeneralSection,
			AppearanceSection,
			CutoutSection,
			ClockSection,
			BatterySection,
			NotificationSection,
			ConnectivitySection,
			HiddenAppsSection,
			LockSection,
			LockAppearanceSection,
			LockBehaviorSection,
			ProfilesSection,
			OemSection,
			DebugSection,
			DebugInfoSection,
			DebugPermissionsSection,
			DebugDiffSection,
			LogsSection,
			BackupSection,
			AboutSection
		)

	/** The section whose screen lives at [route], or null for the shell's own routes. */
	fun sectionByRoute(route: String?): SettingSection<OverlayPrefs>? =
		route?.let { r -> sections.firstOrNull { it.route == r } }

	/**
	 * Puts every setting of [store] back to its default. The caller re-applies
	 * the file afterwards (service, tiles, widgets) so the overlay stack stays
	 * the one place that knows how to restart itself.
	 */
	@Suppress("UNCHECKED_CAST")
	fun resetStore(store: SettingStore<*>) {
		(store as SettingStore<Any>).replace(store.default)
	}
}
