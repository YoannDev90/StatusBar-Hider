package dev.yoanndev90.statusbarhider.overlay

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class OverlayPrefsTest {
	private var previousLocale: Locale = Locale.getDefault()

	@Before
	fun pinLocale() {
		previousLocale = Locale.getDefault()
		Locale.setDefault(Locale.US)
	}

	@After
	fun restoreLocale() {
		Locale.setDefault(previousLocale)
	}

	@Test
	fun `json round trip preserves every field`() {
		val prefs = OverlayPrefs().copy(enabled = true, showSeconds = true, use24h = false, maxNotifs = 8)
		assertEquals(prefs, OverlayPrefs.fromJson(prefs.toJson()))
	}

	@Test
	fun `default json decodes to defaults`() {
		assertEquals(OverlayPrefs(), OverlayPrefs.fromJson("{}"))
	}

	@Test
	fun `malformed blob returns null so the caller can keep the raw copy`() {
		assertNull(OverlayPrefs.fromJson("not json"))
		assertNull(OverlayPrefs.fromJson("{"))
		assertNull(OverlayPrefs.fromJson("42"))
	}

	@Test
	fun `legacy raw pattern without use_24h toggles the 12-hour clock`() {
		val prefs = OverlayPrefs.fromJson("""{"time_format":"h:mm a"}""")
		requireNotNull(prefs)
		assertFalse(prefs.use24h)
		assertEquals(OverlayPrefs.FORMAT_12H, prefs.effectiveTimeFormat())
	}

	@Test
	fun `legacy raw pattern HH mm keeps the 24-hour clock`() {
		val prefs = OverlayPrefs.fromJson("""{"time_format":"HH:mm"}""")
		requireNotNull(prefs)
		assertTrue(prefs.use24h)
	}

	@Test
	fun `stored use_24h wins over the legacy pattern`() {
		val prefs = OverlayPrefs.fromJson("""{"use_24h":false,"time_format":"HH:mm"}""")
		requireNotNull(prefs)
		assertFalse(prefs.use24h)
	}

	@Test
	fun `strict decode clamps out-of-range values like a normal load`() {
		val element =
			Json.encodeToJsonElement(
				OverlayPrefs.serializer(),
				OverlayPrefs().copy(updateIntervalSec = 999, fontSizeSp = 999, maxNotifs = 0, fontWeightName = "NOPE")
			)
		val prefs = OverlayPrefs.decodeStrict(element)
		assertEquals(60, prefs.updateIntervalSec)
		assertEquals(20, prefs.fontSizeSp)
		assertEquals(1, prefs.maxNotifs)
		assertEquals("NORMAL", prefs.fontWeightName)
	}

	@Test
	fun `strict decode throws on an element of the wrong shape`() {
		assertThrows(Exception::class.java) { OverlayPrefs.decodeStrict(Json.parseToJsonElement("[1,2]")) }
	}

	@Test
	fun `effective time format honors both toggles`() {
		assertEquals(
			OverlayPrefs.DEFAULT_FORMAT_NO_SECONDS,
			OverlayPrefs().copy(use24h = true, showSeconds = false).effectiveTimeFormat()
		)
		assertEquals(
			OverlayPrefs.DEFAULT_FORMAT_WITH_SECONDS,
			OverlayPrefs().copy(use24h = true, showSeconds = true).effectiveTimeFormat()
		)
		assertEquals(
			OverlayPrefs.FORMAT_12H,
			OverlayPrefs().copy(use24h = false, showSeconds = false).effectiveTimeFormat()
		)
		assertEquals(
			OverlayPrefs.FORMAT_12H_WITH_SECONDS,
			OverlayPrefs().copy(use24h = false, showSeconds = true).effectiveTimeFormat()
		)
	}

	@Test
	fun `battery percent maps the sticky extras pair`() {
		assertEquals(50, OverlayPrefs.batteryPct(50, 100))
		assertEquals(100, OverlayPrefs.batteryPct(100, 100))
		assertEquals(33, OverlayPrefs.batteryPct(1, 3))
		assertEquals(-1, OverlayPrefs.batteryPct(-1, -1))
		assertEquals(-1, OverlayPrefs.batteryPct(50, 0))
	}

	@Test
	fun `speed formatting picks the unit and stays non-negative`() {
		assertEquals("0B", OverlayPrefs.formatSpeed(0))
		assertEquals("512B", OverlayPrefs.formatSpeed(512))
		assertEquals("1023B", OverlayPrefs.formatSpeed(1023))
		assertEquals("1K", OverlayPrefs.formatSpeed(1024))
		assertEquals("12K", OverlayPrefs.formatSpeed(12 * 1024))
		assertEquals("3.4M", OverlayPrefs.formatSpeed((3.4 * 1048576).toLong()))
		assertEquals("0B", OverlayPrefs.formatSpeed(-5))
	}
}
