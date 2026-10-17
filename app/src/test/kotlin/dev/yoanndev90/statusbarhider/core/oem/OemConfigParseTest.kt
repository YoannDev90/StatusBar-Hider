package dev.yoanndev90.statusbarhider.core.oem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
		val files = assetFiles()
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

	@Test
	fun `config without schema version is accepted as legacy`() {
		val raw = """{"name":"Old","hide":[],"restore":[],"status":[]}"""
		assertEquals("Old", OemConfig.parseJson("test", raw).name)
	}

	@Test
	fun `command defaults are an empty description and a persistent command`() {
		val raw = """{"name":"X","hide":[{"name":"a","cmd":"echo"}],"restore":[],"status":[]}"""
		val command = OemConfig.parseJson("test", raw).hide.single()
		assertEquals("", command.description)
		assertTrue(command.persistent)
	}

	@Test
	fun `placeholder carries no commands so an impatient caller runs nothing`() {
		val placeholder = OemConfig.placeholder()
		assertEquals("", placeholder.id)
		assertEquals("", placeholder.name)
		assertTrue(placeholder.hide.isEmpty())
		assertTrue(placeholder.restore.isEmpty())
		assertTrue(placeholder.status.isEmpty())
		assertTrue(placeholder.match.isEmpty())
		assertFalse(placeholder.untested)
	}

	@Test
	fun `every bundled config ships runnable commands and lowercase matches`() {
		for (file in assetFiles()) {
			val config = OemConfig.parseJson(file.nameWithoutExtension, file.readText())
			assertTrue("${file.name} needs hide commands", config.hide.isNotEmpty())
			assertTrue("${file.name} needs restore commands", config.restore.isNotEmpty())
			assertTrue("${file.name} needs status commands", config.status.isNotEmpty())
			for (command in config.hide + config.restore + config.status) {
				assertTrue("${file.name} command name blank", command.name.isNotBlank())
				assertTrue("${file.name} command cmd blank", command.cmd.isNotBlank())
			}
			assertTrue("${file.name} has blank match entries", config.match.none { it.isBlank() })
			assertEquals("${file.name} match must stay lowercase", config.match.map { it.lowercase() }, config.match)
		}
	}

	/** Resolved from the module dir so the test works from Gradle and IDE runs alike. */
	private fun assetsDir(): File {
		val candidates = listOf("src/main/assets/oem", "app/src/main/assets/oem")
		return candidates.mapNotNull { File(it).takeIf(File::isDirectory) }.firstOrNull()
			?: error("bundled oem assets not found, looked in $candidates")
	}

	private fun assetFiles(): List<File> =
		assetsDir()
			.listFiles { f -> f.isFile && f.extension == "json" }
			?.sortedBy { it.name }
			?: error("no assets in ${assetsDir()}")
}
