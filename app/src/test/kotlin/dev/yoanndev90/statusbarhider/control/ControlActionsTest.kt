package dev.yoanndev90.statusbarhider.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlActionsTest {
	private val allActions =
		listOf(
			ControlActions.ACTION_SHOW_BAR,
			ControlActions.ACTION_HIDE_BAR,
			ControlActions.ACTION_TOGGLE_BAR,
			ControlActions.ACTION_HIDE_SYSTEM_BAR,
			ControlActions.ACTION_RESTORE_SYSTEM_BAR,
			ControlActions.ACTION_TOGGLE_SYSTEM_BAR
		)

	@Test
	fun `the six documented control actions are recognized`() {
		allActions.forEach { assertTrue(it, ControlActions.isControlAction(it)) }
	}

	@Test
	fun `action constants are distinct so a copy-paste cannot shadow another`() {
		assertEquals(6, allActions.toSet().size)
	}

	@Test
	fun `every action is namespaced so the docs stay copy-pasteable`() {
		allActions.forEach { assertTrue(it, it.startsWith("dev.yoanndev90.statusbarhider.")) }
	}

	@Test
	fun `null empty and foreign actions are rejected`() {
		assertFalse(ControlActions.isControlAction(null))
		assertFalse(ControlActions.isControlAction(""))
		assertFalse(ControlActions.isControlAction("SHOW_BAR"))
		assertFalse(ControlActions.isControlAction("dev.yoanndev90.statusbarhider.SHOW_BARR"))
		assertFalse(ControlActions.isControlAction("android.intent.action.BOOT_COMPLETED"))
	}
}
