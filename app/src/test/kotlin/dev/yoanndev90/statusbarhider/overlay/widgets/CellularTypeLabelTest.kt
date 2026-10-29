package dev.yoanndev90.statusbarhider.overlay.widgets

import dev.yoanndev90.statusbarhider.overlay.model.SimStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins which SIM's generation label the cellular block draws. */
class CellularTypeLabelTest {
	@Test
	fun `the data SIM carries the label`() {
		val sims =
			listOf(
				SimStatus(slot = 1, type = "3G", level = 2),
				SimStatus(slot = 2, type = "5G", level = 3, isData = true)
			)
		assertEquals("5G", cellularTypeLabel(sims))
	}

	@Test
	fun `the first SIM carries it when no data SIM is known`() {
		val sims =
			listOf(
				SimStatus(slot = 1, type = "LTE", level = 4),
				SimStatus(slot = 2, type = "3G", level = 1)
			)
		assertEquals("LTE", cellularTypeLabel(sims))
	}

	@Test
	fun `a single SIM is unchanged`() {
		assertEquals("LTE", cellularTypeLabel(listOf(SimStatus(slot = 1, type = "LTE", level = 3, isData = true))))
	}

	@Test
	fun `no row at all yields empty so the widget can apply its own 4G default`() {
		assertEquals("", cellularTypeLabel(emptyList()))
	}
}
