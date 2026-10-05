package dev.yoanndev90.statusbarhider.core.oem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OemConfigParseTest {
	@Test
	fun `too-new schema version is rejected with a clear message`() {
		val raw = """{"schema_version":99,"name":"X","hide":[],"restore":[],"status":[]}"""
		val e = assertThrows(OemConfigException::class.java) { OemConfig.parseJson("test", raw) }
		assertTrue(e.message.orEmpty().contains("schema_version 99"))
	}

	@Test
	fun `valid config parses and lowercases match strings`() {
		val raw =
			"""
			{"schema_version":1,"name":"Test OEM","match":["TestOEM"],
			 "hide":[{"name":"a","cmd":"echo a"}],
			 "restore":[{"name":"a","cmd":"echo b"}],
			 "status":[{"name":"a","cmd":"echo c"}]}
			""".trimIndent()
		val config = OemConfig.parseJson("test", raw)
		assertEquals("test", config.id)
		assertEquals("Test OEM", config.name)
		assertEquals(listOf("testoem"), config.match)
		assertEquals("echo a", config.hide.single().cmd)
		assertEquals(1, config.restore.size)
		assertEquals(1, config.status.size)
	}

	@Test
	fun `every bundled oem asset parses`() {
		val dir = assetsDir()
		val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: error("no assets in $dir")
		assertTrue("expected a real set of bundled configs", files.size >= 10)
		for (file in files) {
			val config = OemConfig.parseJson(file.nameWithoutExtension, file.readText())
			assertEquals(file.nameWithoutExtension, config.id)
			assertTrue("${file.name} needs a display name", config.name.isNotBlank())
		}
	}

	@Test
	fun `malformed json is not swallowed`() {
		assertThrows(Exception::class.java) { OemConfig.parseJson("test", "not json") }
	}

	/** Resolved from the module dir so the test works from Gradle and IDE runs alike. */
	private fun assetsDir(): File {
		val candidates = listOf("src/main/assets/oem", "app/src/main/assets/oem")
		return candidates.mapNotNull { File(it).takeIf(File::isDirectory) }.firstOrNull()
			?: error("bundled oem assets not found, looked in $candidates")
	}
}
