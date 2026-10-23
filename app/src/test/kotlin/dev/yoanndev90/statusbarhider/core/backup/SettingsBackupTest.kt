package dev.yoanndev90.statusbarhider.core.backup

import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefsCodec
import dev.yoanndev90.statusbarhider.core.settings.specs.OverlaySpecs
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class SettingsBackupTest {
	@Test
	fun `non-json input is rejected with a clear message`() {
		val e = assertThrows(SettingsBackupException::class.java) { SettingsBackup.parse("not json") }
		assertTrue(e.message.orEmpty().startsWith("Not a valid JSON file"))
	}

	@Test
	fun `json that is not an object is rejected`() {
		assertThrows(SettingsBackupException::class.java) { SettingsBackup.parse("[1,2,3]") }
	}

	@Test
	fun `newer schema version is refused instead of half-applied`() {
		val raw = """{"schema_version":2,"oem_id":"x","overlay":{}}"""
		val e = assertThrows(SettingsBackupException::class.java) { SettingsBackup.parse(raw) }
		assertTrue(e.message.orEmpty().contains("newer"))
	}

	@Test
	fun `missing overlay block is refused`() {
		val e =
			assertThrows(SettingsBackupException::class.java) { SettingsBackup.parse("""{"schema_version":1,"oem_id":"x"}""") }
		assertTrue(e.message.orEmpty().contains("overlay"))
	}

	@Test
	fun `unreadable overlay block is refused`() {
		val raw = """{"schema_version":1,"overlay":42}"""
		val e = assertThrows(SettingsBackupException::class.java) { SettingsBackup.parse(raw) }
		assertTrue(e.message.orEmpty().startsWith("Unreadable overlay block"))
	}

	@Test
	fun `round trip preserves prefs and oem id`() {
		val prefs = OverlayPrefs().copy(enabled = true, showSeconds = true, fontSizeSp = 16)
		val raw = """{"schema_version":1,"oem_id":"lineage","overlay":${prefs.toJson()}}"""
		val backup = SettingsBackup.parse(raw)
		assertEquals("lineage", backup.oemId)
		assertEquals(prefs, backup.prefs)
	}

	@Test
	fun `backup without schema version is accepted as legacy`() {
		val raw = """{"oem_id":"","overlay":${OverlayPrefs().toJson()}}"""
		assertEquals(OverlayPrefs(), SettingsBackup.parse(raw).prefs)
	}

	@Test
	fun `overlay block is normalized like a normal load`() {
		val element = Json.parseToJsonElement("""{"update_interval_sec":999,"font_size_sp":1}""")
		val raw = """{"schema_version":1,"oem_id":"t","overlay":$element}"""
		val prefs = SettingsBackup.parse(raw).prefs
		assertEquals(60, prefs.updateIntervalSec)
		assertEquals(10, prefs.fontSizeSp)
	}

	@Test
	fun `small blob is read in full`() {
		val raw = """{"schema_version":1}"""
		assertEquals(raw, SettingsBackup.readCapped(raw.byteInputStream()))
	}

	@Test
	fun `stream past the byte limit is refused before it is buffered`() {
		val data = ByteArray(SettingsBackup.MAX_BYTES * 2) { ' '.code.toByte() }
		val counting = CountingInputStream(data)
		val e = assertThrows(SettingsBackupException::class.java) { SettingsBackup.readCapped(counting) }
		assertTrue(e.message.orEmpty().contains("limit"))
		assertTrue(counting.consumed < data.size)
	}

	@Test
	fun `oversized string is refused by the parser before json runs`() {
		val raw = "not json ".repeat(SettingsBackup.MAX_BYTES / 9 + 1)
		val e = assertThrows(SettingsBackupException::class.java) { SettingsBackup.parse(raw) }
		assertTrue(e.message.orEmpty().contains("byte limit"))
	}

	@Test
	fun `unknown keys in the envelope and the overlay block are ignored`() {
		val raw =
			"""{"schema_version":1,"oem_id":"x","future_key":1,"overlay":{"future_pref":true,"show_battery":false}}"""
		val backup = SettingsBackup.parse(raw)
		assertEquals("x", backup.oemId)
		assertFalse(backup.prefs.showBattery)
		assertTrue(backup.prefs.showWifi)
	}

	@Test
	fun `a frozen v1 fixture still imports`() {
		// Written by hand before the registry drove the export keys: the wire format must not move.
		val order = "clock,battery,date,calendar,notifs,media,spacer,connectivity,alarm,bandwidth"
		val overlay =
			"""{"show_battery":false,"font_size_sp":16,"update_interval_sec":15,""" +
				""""widget_order":"$order","show_wifi":true}"""
		val fixture = """{"schema_version":1,"oem_id":"hyperos","overlay":$overlay}"""
		val backup = SettingsBackup.parse(fixture)
		assertEquals("hyperos", backup.oemId)
		assertFalse(backup.prefs.showBattery)
		assertTrue(backup.prefs.showWifi)
		assertEquals(16, backup.prefs.fontSizeSp)
		assertEquals(15, backup.prefs.updateIntervalSec)
		assertEquals(order.split(","), backup.prefs.widgetOrder)
	}

	@Test
	fun `the export carries every spec key and no app flag`() {
		val exported = OverlayPrefsCodec.exportJson(OverlayPrefs())
		assertEquals(OverlaySpecs.all.map { it.key }.toSet(), exported.keys)
		assertFalse("setup_done" in exported.keys)
		assertFalse("control_token" in exported.keys)
	}

	@Test
	fun `a non-exportable spec drops its key and defaults back on import`() {
		// show_bandwidth defaults to off: dropping the key must bring that default back.
		val minusBandwidth = OverlaySpecs.all.filter { it.key != "show_bandwidth" }
		val exported = OverlayPrefsCodec.exportOf(OverlayPrefs().copy(showBandwidth = true), minusBandwidth)
		assertFalse("show_bandwidth" in exported.keys)
		assertFalse(OverlayPrefsCodec.decodeStrict(exported).showBandwidth)
	}
}

/** Counts how much of the backing array a reader actually pulled. */
private class CountingInputStream(
	private val data: ByteArray
) : InputStream() {
	var consumed = 0
		private set
	private var pos = 0

	override fun read(): Int {
		if (pos >= data.size) return -1
		consumed++
		return data[pos++].toInt() and 0xFF
	}
}
