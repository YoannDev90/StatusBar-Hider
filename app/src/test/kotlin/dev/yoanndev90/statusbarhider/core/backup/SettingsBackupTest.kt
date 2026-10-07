package dev.yoanndev90.statusbarhider.core.backup

import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
