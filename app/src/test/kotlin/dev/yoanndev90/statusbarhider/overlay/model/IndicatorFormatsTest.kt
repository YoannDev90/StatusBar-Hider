package dev.yoanndev90.statusbarhider.overlay.model

import org.junit.Assert.assertEquals
import org.junit.Test

class IndicatorFormatsTest {
	@Test
	fun `battery percent maps the sticky extras pair`() {
		assertEquals(50, IndicatorFormats.batteryPct(50, 100))
		assertEquals(100, IndicatorFormats.batteryPct(100, 100))
		assertEquals(33, IndicatorFormats.batteryPct(1, 3))
		assertEquals(-1, IndicatorFormats.batteryPct(-1, -1))
		assertEquals(-1, IndicatorFormats.batteryPct(50, 0))
	}

	@Test
	fun `speed parts pick the unit and stay non-negative`() {
		assertEquals("0" to "B", partsOf(0))
		assertEquals("512" to "B", partsOf(512))
		assertEquals("1023" to "B", partsOf(1023))
		assertEquals("1" to "K", partsOf(1024))
		assertEquals("12" to "K", partsOf(12 * 1024))
		assertEquals("3.4" to "M", partsOf((3.4 * 1048576).toLong()))
		assertEquals("0" to "B", partsOf(-5))
	}

	private fun partsOf(bytesPerSecond: Long): Pair<String, String> = IndicatorFormats.speedParts(bytesPerSecond).let { it.value to it.unit }
}
