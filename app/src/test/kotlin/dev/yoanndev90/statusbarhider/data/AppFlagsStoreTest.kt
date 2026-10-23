package dev.yoanndev90.statusbarhider.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app flags' registry contract: [AppFlags.setupDone] is described so the
 * debug screen can list and reset it, and no app-level key can ever reach a
 * settings export — least of all the control token, which is not even a spec.
 */
class AppFlagsStoreTest {
	@Test
	fun `the app flags model is described by the one spec`() {
		assertEquals(listOf("setup_done"), AppFlagSpecs.all.map { it.key })
	}

	@Test
	fun `setup_done is never exported`() {
		assertFalse(AppFlagSpecs.setupDone.exportable)
		assertTrue(AppFlagSpecs.all.none { it.exportable })
	}

	@Test
	fun `the control token is not described anywhere`() {
		assertTrue(AppFlagSpecs.all.none { it.key.contains("token") })
	}

	@Test
	fun `the setup flag round-trips through its spec`() {
		val spec = AppFlagSpecs.setupDone
		assertEquals(false, spec.default)
		assertFalse(spec.isModifiedIn(AppFlags()))
		assertTrue(spec.isModifiedIn(AppFlags(setupDone = true)))
		assertEquals(AppFlags(), spec.resetIn(AppFlags(setupDone = true)))
	}
}
