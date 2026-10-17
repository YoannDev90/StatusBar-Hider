package dev.yoanndev90.statusbarhider.core.profiles

import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilesStoreTest {
	@Test
	fun `round trip preserves profiles and active id`() {
		val prefs = OverlayPrefs().copy(enabled = true, showSeconds = true)
		val state =
			ProfilesState(
				profiles = listOf(Profile("id-1", "Day", 123L, prefs)),
				activeId = "id-1"
			)
		assertEquals(state, ProfilesStore.parse(ProfilesStore.encode(state)))
	}

	@Test
	fun `malformed input returns null instead of defaults`() {
		assertNull(ProfilesStore.parse("not json"))
	}

	@Test
	fun `empty object decodes to the default state`() {
		assertEquals(ProfilesState(), ProfilesStore.parse("{}"))
	}

	@Test
	fun `unknown keys are ignored for forward compatibility`() {
		val raw = """{"profiles":[],"activeId":null,"future":42}"""
		assertEquals(ProfilesState(), ProfilesStore.parse(raw))
	}

	@Test
	fun `name is trimmed and capped`() {
		assertEquals("Day", ProfilesStore.sanitizeName("  Day  "))
		assertEquals(ProfilesStore.MAX_NAME_LENGTH, ProfilesStore.sanitizeName("x".repeat(100)).length)
		assertEquals("", ProfilesStore.sanitizeName("   "))
	}

	@Test
	fun `out-of-range snapshot prefs are clamped like a normal load`() {
		val raw =
			"""{"profiles":[{"id":"a","name":"N","createdAt":1,"prefs":{"update_interval_sec":999,"font_size_sp":1}}]}"""
		val prefs = ProfilesStore
			.parse(raw)!!
			.profiles
			.single()
			.prefs
		assertEquals(60, prefs.updateIntervalSec)
		assertEquals(10, prefs.fontSizeSp)
	}

	@Test
	fun `blank names are rejected by the repository contract`() {
		// The store only trims; a blank result is the caller's rejection signal.
		assertEquals("", ProfilesStore.sanitizeName("\t\n "))
	}
}
