package dev.yoanndev90.statusbarhider.core.oem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OemConfigParseTest {
	/** `package/class`, the shape `KeepAliveAccess.parseComponent` accepts. */
	private val flatComponent = Regex("[^/]+/[^/]+")

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
	fun `config without keep-alive ships an empty list`() {
		val raw = """{"name":"Old","hide":[],"restore":[],"status":[]}"""
		assertTrue(OemConfig.parseJson("test", raw).keepAlive.isEmpty())
	}

	@Test
	fun `keep-alive entry parses with its defaults`() {
		val raw =
			"""
			{"schema_version":1,"name":"X","hide":[],"restore":[],"status":[],
			 "keepAlive":[{"name":"autostart","label":"Autostart",
			   "components":["com.a/com.a.A"],"action":"android.settings.X"}]}
			""".trimIndent()
		val link = OemConfig.parseJson("test", raw).keepAlive.single()
		assertEquals("autostart", link.name)
		assertEquals("Autostart", link.label)
		assertEquals("", link.hint)
		assertEquals(listOf("com.a/com.a.A"), link.components)
		assertEquals("android.settings.X", link.action)
	}

	@Test
	fun `every bundled keep-alive entry is well formed`() {
		for (file in assetFiles()) {
			val config = OemConfig.parseJson(file.nameWithoutExtension, file.readText())
			val names = config.keepAlive.map { it.name }
			assertEquals("${file.name} keep-alive names must be unique", names.toSet().size, names.size)
			for (link in config.keepAlive) {
				val at = "${file.name} keepAlive.${link.name}"
				assertTrue("$at needs a name", link.name.isNotBlank())
				assertTrue("$at needs a label", link.label.isNotBlank())
				assertTrue("$at has no target at all", link.components.isNotEmpty() || link.action.isNotBlank())
				for (spec in link.components) {
					// ComponentName is android.jar, which throws "Stub!" under a
					// plain JVM test, so the shape is checked as text instead.
					assertTrue("$at malformed component '$spec'", flatComponent.matches(spec))
				}
			}
		}
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
