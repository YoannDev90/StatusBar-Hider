package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.overlay.BarFontWeight
import dev.yoanndev90.statusbarhider.overlay.OverlayBackground
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPrefsCodecTest {
	@Test
	fun `json round trip preserves every field`() {
		val prefs = OverlayPrefs().copy(enabled = true, showSeconds = true, use24h = false, maxNotifs = 8)
		assertEquals(prefs, OverlayPrefsCodec.fromJson(prefs.toJson()))
	}

	@Test
	fun `default json decodes to defaults`() {
		assertEquals(OverlayPrefs(), OverlayPrefsCodec.fromJson("{}"))
	}

	@Test
	fun `malformed blob returns null so the caller can keep the raw copy`() {
		assertNull(OverlayPrefsCodec.fromJson("not json"))
		assertNull(OverlayPrefsCodec.fromJson("{"))
		assertNull(OverlayPrefsCodec.fromJson("42"))
	}

	@Test
	fun `unknown keys are ignored instead of failing the load`() {
		val prefs = requireNotNull(OverlayPrefsCodec.fromJson("""{"time_format":"h:mm a","some_future_pref":1}"""))
		// The old raw pattern key is not ours to interpret: the toggle keeps its default.
		assertTrue(prefs.use24h)
	}

	@Test
	fun `per-device bluetooth icons default to on`() {
		assertTrue(OverlayPrefs().showBtIcons)
		assertTrue(OverlayPrefsCodec.fromJson("{}")!!.showBtIcons)
	}

	@Test
	fun `bt icon overrides survive the round trip`() {
		val prefs = OverlayPrefs().copy(showBtIcons = false, btIconOverrides = mapOf("AA:BB:CC:DD:EE:FF" to "SPEAKER"))
		assertEquals(prefs, OverlayPrefsCodec.fromJson(prefs.toJson()))
	}

	@Test
	fun `strict decode clamps out-of-range values like a normal load`() {
		val element =
			Json.encodeToJsonElement(
				OverlayPrefs.serializer(),
				OverlayPrefs().copy(updateIntervalSec = 999, fontSizeSp = 999, maxNotifs = 0)
			)
		val prefs = OverlayPrefsCodec.decodeStrict(element)
		assertEquals(60, prefs.updateIntervalSec)
		assertEquals(20, prefs.fontSizeSp)
		assertEquals(1, prefs.maxNotifs)
		assertEquals(BarFontWeight.NORMAL, prefs.fontWeightName)
	}

	@Test
	fun `strict decode coerces an unknown font weight to the default`() {
		// The model is typed now: an unknown weight only exists on the wire.
		val element = Json.parseToJsonElement("""{"font_weight":"NOPE"}""")
		assertEquals(BarFontWeight.NORMAL, OverlayPrefsCodec.decodeStrict(element).fontWeightName)
	}

	@Test
	fun `strict decode throws on an element of the wrong shape`() {
		assertThrows(Exception::class.java) { OverlayPrefsCodec.decodeStrict(Json.parseToJsonElement("[1,2]")) }
	}

	@Test
	fun `unknown enum value coerces to the default instead of failing the load`() {
		assertEquals(OverlayBackground.SEMI, requireNotNull(OverlayPrefsCodec.fromJson("""{"background":"NOPE"}""")).background)
	}

	@Test
	fun `blank date format falls back to the default pattern`() {
		assertEquals(
			OverlayPrefs.DEFAULT_DATE_FORMAT,
			requireNotNull(OverlayPrefsCodec.fromJson("""{"date_format":""}""")).dateFormat
		)
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
		val prefs = requireNotNull(OverlayPrefsCodec.fromJson(raw))
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
		assertEquals(0, requireNotNull(OverlayPrefsCodec.fromJson("""{"burn_in_min":-1}""")).burnInMin)
	}

	@Test
	fun `ring color keeps only plain RRGGBB`() {
		assertEquals("#AABBCC", requireNotNull(OverlayPrefsCodec.fromJson("""{"camera_ring_color":"#AABBCC"}""")).cameraRingColor)
		assertEquals("#aabbcc", requireNotNull(OverlayPrefsCodec.fromJson("""{"camera_ring_color":"#aabbcc"}""")).cameraRingColor)
		for (bad in listOf("", "AABBCC", "#GGGGGG", "#12345", "#1234567", "red", "#FFFFFF ")) {
			val prefs = requireNotNull(OverlayPrefsCodec.fromJson("""{"camera_ring_color":"$bad"}"""))
			assertEquals("'$bad' must be rejected", "", prefs.cameraRingColor)
		}
	}

	@Test
	fun `hidden apps csv round trip trims dedupes and drops blanks`() {
		val prefs = OverlayPrefs().copy(hiddenApps = listOf("com.b", "com.a"))
		assertEquals(prefs, OverlayPrefsCodec.fromJson(prefs.toJson()))
		val decoded = requireNotNull(OverlayPrefsCodec.fromJson("""{"hidden_apps":"com.a, com.b ,com.a, ,"}"""))
		assertEquals(listOf("com.a", "com.b"), decoded.hiddenApps)
		assertTrue(requireNotNull(OverlayPrefsCodec.fromJson("""{"hidden_apps":""}""")).hiddenApps.isEmpty())
	}

	@Test
	fun `widget order survives the round trip as a legacy csv`() {
		val prefs = OverlayPrefs().copy(widgetOrder = listOf(WidgetId.NOTIFS, WidgetId.CLOCK))
		val root = Json.parseToJsonElement(prefs.toJson()).jsonObject
		assertEquals("notifs,clock", (root["widget_order"] as JsonPrimitive).content)
		// Decode repairs a partial order to the full set, stored ids first.
		val decoded = requireNotNull(OverlayPrefsCodec.fromJson(prefs.toJson()))
		assertEquals(listOf(WidgetId.NOTIFS, WidgetId.CLOCK), decoded.widgetOrder.take(2))
		assertEquals(WidgetId.ALL.toSet(), decoded.widgetOrder.toSet())
	}

	@Test
	fun `garbage widget order is repaired to the full set on decode`() {
		val decoded = requireNotNull(OverlayPrefsCodec.fromJson("""{"widget_order":"bogus,clock"}"""))
		assertEquals(WidgetId.CLOCK, decoded.widgetOrder.first())
		assertEquals(WidgetId.ALL.toSet(), decoded.widgetOrder.toSet())
	}
}
