package dev.yoanndev90.statusbarhider.ui.screens.settings

import dev.yoanndev90.statusbarhider.core.settings.OverlayBackground
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Debug index side of the modified-settings feature: which sections the
 * index groups and which specs it lists, derived from the registry alone.
 */
class DebugDiffScreenTest {
	@Test
	fun `default settings leave the index empty`() {
		assertEquals(emptyList<Pair<Any, List<Any>>>(), modifiedSections(OverlayPrefs()))
	}

	@Test
	fun `a modified setting surfaces under its own section`() {
		val modified = modifiedSections(OverlayPrefs().copy(background = OverlayBackground.TRANSPARENT))
		assertEquals(listOf("appearance"), modified.map { it.first.id })
		assertTrue(modified.single().second.any { it.key == "background" })
	}

	@Test
	fun `a section lists every setting it owns that moved off its default`() {
		val modified = modifiedSections(OverlayPrefs().copy(showSeconds = true, use24h = false))
		assertEquals(listOf("clock"), modified.map { it.first.id })
		assertEquals(
			setOf("show_seconds", "use_24h"),
			modified
				.single()
				.second
				.map { it.key }
				.toSet()
		)
	}
}
