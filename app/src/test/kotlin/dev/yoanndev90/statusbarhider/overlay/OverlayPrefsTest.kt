package dev.yoanndev90.statusbarhider.overlay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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

	@Test
	fun `background color follows the enum`() {
		assertEquals(android.graphics.Color.TRANSPARENT, OverlayPrefs().copy(background = OverlayBackground.TRANSPARENT).backgroundColor())
		assertEquals(0xCC000000.toInt(), OverlayPrefs().copy(background = OverlayBackground.SEMI).backgroundColor())
		assertEquals(android.graphics.Color.BLACK, OverlayPrefs().copy(background = OverlayBackground.BLACK).backgroundColor())
	}

	@Test
	fun `text color flips with the dark-text toggle`() {
		assertEquals(android.graphics.Color.WHITE, OverlayPrefs().copy(darkText = false).textColor())
		assertEquals(android.graphics.Color.BLACK, OverlayPrefs().copy(darkText = true).textColor())
	}

	@Test
	fun `json keeps the legacy time_format key in sync with the toggles`() {
		for ((use24h, showSeconds) in listOf(true to true, true to false, false to true, false to false)) {
			val prefs = OverlayPrefs().copy(use24h = use24h, showSeconds = showSeconds)
			val root = Json.parseToJsonElement(prefs.toJson()).jsonObject
			assertEquals(prefs.effectiveTimeFormat(), (root["time_format"] as JsonPrimitive).content)
		}
	}

	@Test
	fun `unknown enum value coerces to the default instead of failing the load`() {
		assertEquals(OverlayBackground.SEMI, requireNotNull(OverlayPrefs.fromJson("""{"background":"NOPE"}""")).background)
	}

	@Test
	fun `blank date format falls back to the default pattern`() {
		assertEquals(OverlayPrefs.DEFAULT_DATE_FORMAT, requireNotNull(OverlayPrefs.fromJson("""{"date_format":""}""")).dateFormat)
	}

	@Test
	fun `normalize clamps every free-form field`() {
		val raw =
			"""
			{"pad_start_dp":99,"pad_top_dp":-5,"pad_end_dp":99,"pad_bottom_dp":-5,
			 "burn_in_min":99,"widget_spacing_dp":99,"camera_gap_dp":99,
			 "camera_nudge_x_dp":999,"camera_nudge_y_dp":-999,"camera_offset_x_pct":200,
			 "camera_offset_y_dp":99,"camera_size_dp":99,"camera_ring_stroke_dp":0}
			""".trimIndent()
		val prefs = requireNotNull(OverlayPrefs.fromJson(raw))
		assertEquals(32, prefs.padStartDp)
		assertEquals(0, prefs.padTopDp)
		assertEquals(32, prefs.padEndDp)
		assertEquals(0, prefs.padBottomDp)
		assertEquals(30, prefs.burnInMin)
		assertEquals(12, prefs.widgetSpacingDp)
		assertEquals(16, prefs.cameraGapDp)
		assertEquals(48, prefs.cameraNudgeXDp)
		assertEquals(-48, prefs.cameraNudgeYDp)
		assertEquals(100, prefs.cameraOffsetXPct)
		assertEquals(60, prefs.cameraOffsetYDp)
		assertEquals(40, prefs.cameraSizeDp)
		assertEquals(1, prefs.cameraRingStrokeDp)
	}

	@Test
	fun `burn-in below zero clamps to zero`() {
		assertEquals(0, requireNotNull(OverlayPrefs.fromJson("""{"burn_in_min":-1}""")).burnInMin)
	}

	@Test
	fun `ring color keeps only plain RRGGBB`() {
		assertEquals("#AABBCC", requireNotNull(OverlayPrefs.fromJson("""{"camera_ring_color":"#AABBCC"}""")).cameraRingColor)
		assertEquals("#aabbcc", requireNotNull(OverlayPrefs.fromJson("""{"camera_ring_color":"#aabbcc"}""")).cameraRingColor)
		for (bad in listOf("", "AABBCC", "#GGGGGG", "#12345", "#1234567", "red", "#FFFFFF ")) {
			val prefs = requireNotNull(OverlayPrefs.fromJson("""{"camera_ring_color":"$bad"}"""))
			assertEquals("'$bad' must be rejected", "", prefs.cameraRingColor)
		}
	}

	@Test
	fun `hidden apps csv round trip trims dedupes and drops blanks`() {
		val prefs = OverlayPrefs().copy(hiddenApps = listOf("com.b", "com.a"))
		assertEquals(prefs, OverlayPrefs.fromJson(prefs.toJson()))
		val decoded = requireNotNull(OverlayPrefs.fromJson("""{"hidden_apps":"com.a, com.b ,com.a, ,"}"""))
		assertEquals(listOf("com.a", "com.b"), decoded.hiddenApps)
		assertTrue(requireNotNull(OverlayPrefs.fromJson("""{"hidden_apps":""}""")).hiddenApps.isEmpty())
	}

	@Test
	fun `widget order survives the round trip as a legacy csv`() {
		val prefs = OverlayPrefs().copy(widgetOrder = listOf(WidgetId.NOTIFS, WidgetId.CLOCK))
		val root = Json.parseToJsonElement(prefs.toJson()).jsonObject
		assertEquals("notifs,clock", (root["widget_order"] as JsonPrimitive).content)
		// Decode repairs a partial order to the full set, stored ids first.
		val decoded = requireNotNull(OverlayPrefs.fromJson(prefs.toJson()))
		assertEquals(listOf(WidgetId.NOTIFS, WidgetId.CLOCK), decoded.widgetOrder.take(2))
		assertEquals(WidgetId.ALL.toSet(), decoded.widgetOrder.toSet())
	}

	@Test
	fun `garbage widget order is repaired to the full set on decode`() {
		val decoded = requireNotNull(OverlayPrefs.fromJson("""{"widget_order":"bogus,clock"}"""))
		assertEquals(WidgetId.CLOCK, decoded.widgetOrder.first())
		assertEquals(WidgetId.ALL.toSet(), decoded.widgetOrder.toSet())
	}
}
