package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.permission.PermissionGrants
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
import dev.yoanndev90.statusbarhider.core.settings.sections.missingGrantCount
import dev.yoanndev90.statusbarhider.ui.screens.settings.ResolvedSection
import dev.yoanndev90.statusbarhider.ui.screens.settings.searchSections
import dev.yoanndev90.statusbarhider.ui.shell.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigation guard for phase 4: the root listing, the top-bar titles and
 * the search all derive from [SettingsRegistry.sections] — these tests pin
 * that derivation against the NavHost's route table and the search contract.
 */
class SettingsNavigationTest {
	private val sectionsByRoute =
		mapOf(
			Routes.GENERAL to GeneralSection,
			Routes.APPEARANCE to AppearanceSection,
			Routes.CUTOUT to CutoutSection,
			Routes.CLOCK to ClockSection,
			Routes.BATTERY to BatterySection,
			Routes.NOTIFICATIONS to NotificationSection,
			Routes.CONNECTIVITY to ConnectivitySection,
			Routes.HIDDEN_APPS to HiddenAppsSection,
			Routes.LOCK to LockSection,
			Routes.LOCK_APPEARANCE to LockAppearanceSection,
			Routes.LOCK_BEHAVIOR to LockBehaviorSection,
			Routes.PROFILES to ProfilesSection,
			Routes.OEM to OemSection,
			Routes.DEBUG to DebugSection,
			Routes.DEBUG_DIFF to DebugDiffSection,
			Routes.DEBUG_INFO to DebugInfoSection,
			Routes.DEBUG_PERMISSIONS to DebugPermissionsSection,
			Routes.LOGS to LogsSection,
			Routes.BACKUP to BackupSection,
			Routes.ABOUT to AboutSection
		)

	@Test
	fun `every NavHost settings constant still has its section`() {
		sectionsByRoute.forEach { (route, section) ->
			assertEquals(route, section.route)
		}
	}

	@Test
	fun `the registry lists exactly the NavHost settings routes`() {
		assertEquals(sectionsByRoute.keys, SettingsRegistry.sections.map { it.route }.toSet())
	}

	@Test
	fun `section ids are unique`() {
		val ids = SettingsRegistry.sections.map { it.id }
		assertEquals(ids.size, ids.distinct().size)
	}

	@Test
	fun `bar settings come before app ones at the root`() {
		val groups = SettingsRegistry.sections.map { it.group.ordinal }
		assertEquals(groups.sorted(), groups)
	}

	@Test
	fun `only sub-screens are unlisted`() {
		assertEquals(
			setOf("lock/appearance", "lock/behavior", "debug/info", "debug/permissions", "debug/diff"),
			SettingsRegistry.sections
				.filterNot { it.listed }
				.map { it.id }
				.toSet()
		)
	}

	@Test
	fun `the top-bar title comes from the section`() {
		assertEquals(LockSection.title, Routes.titleOf(Routes.LOCK))
		assertEquals(DebugInfoSection.title, Routes.titleOf(Routes.DEBUG_INFO))
		assertEquals(R.string.app_name, Routes.titleOf(null))
	}

	@Test
	fun `a keyguard query surfaces the lock hub without its unlisted sub-screens`() {
		val hub = entry(LockSection, "lock screen appearance and behavior when locked keyguard locked")
		val sub = entry(LockAppearanceSection, "appearance lock keyguard layout full clock only")
		assertEquals(listOf(hub), searchSections(listOf(hub, sub), "keyguard"))
	}

	@Test
	fun `a sub-screen alone matches and takes over the listing`() {
		val hub = entry(LockSection, "lock screen appearance and behavior when locked keyguard locked")
		val sub = entry(LockAppearanceSection, "appearance lock keyguard layout full clock only")
		assertEquals(listOf(sub), searchSections(listOf(hub, sub), "layout"))
	}

	@Test
	fun `an unknown query shows the no-results list`() {
		assertEquals(emptyList<ResolvedSection>(), searchSections(listOf(entry(LockSection, "lock screen keyguard")), "zzz"))
	}

	@Test
	fun `an empty query lists the listed sections only`() {
		val hub = entry(LockSection, "lock screen keyguard")
		val sub = entry(LockAppearanceSection, "layout")
		assertEquals(listOf(hub), searchSections(listOf(hub, sub), ""))
	}

	@Test
	fun `the words the search needs are declared in the keywords`() {
		assertTrue("keyguard" in LockSection.keywords)
		assertTrue(LockAppearanceSection.keywords.containsAll(listOf("keyguard", "layout")))
		assertTrue(LockBehaviorSection.keywords.containsAll(listOf("keyguard", "visibility")))
		assertTrue("permissions" in DebugSection.keywords)
		assertTrue("permission" in DebugPermissionsSection.keywords)
	}

	@Test
	fun `the debug badge counts zero with every grant`() {
		val all =
			PermissionGrants(
				overlay = true,
				notifListener = true,
				calendar = true,
				postNotifs = true,
				bluetooth = true,
				usage = true,
				lockOverlay = true
			)
		assertEquals(0, missingGrantCount(shizukuReady = true, grants = all))
		assertEquals(1, missingGrantCount(shizukuReady = false, grants = all))
	}

	private fun entry(
		section: SettingSection<OverlayPrefs>,
		haystack: String
	): ResolvedSection = ResolvedSection(section, section.id, null, haystack)
}
