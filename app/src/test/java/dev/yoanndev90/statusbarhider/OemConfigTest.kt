package dev.yoanndev90.statusbarhider

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OemConfigTest {
	private val validJson =
		"""
		{
		  "schema_version": 1,
		  "name": "TestOEM",
		  "hide": [
		    {"name": "hide1", "cmd": "cmd1", "description": "desc1", "persistent": true}
		  ],
		  "restore": [
		    {"name": "restore1", "cmd": "cmd2"}
		  ],
		  "status": [
		    {"name": "status1", "cmd": "cmd3", "persistent": false}
		  ]
		}
		""".trimIndent()

	@Test
	fun `parseJson parses valid config correctly`() {
		val config = OemConfig.parseJson("test", validJson)

		assertEquals("test", config.id)
		assertEquals("TestOEM", config.name)
		assertEquals(1, config.hide.size)
		assertEquals("hide1", config.hide[0].name)
		assertEquals("cmd1", config.hide[0].cmd)
		assertEquals("desc1", config.hide[0].description)
		assertTrue(config.hide[0].persistent)
		assertEquals(1, config.restore.size)
		assertEquals("restore1", config.restore[0].name)
		assertEquals(1, config.status.size)
		assertEquals("status1", config.status[0].name)
		assertEquals(false, config.status[0].persistent)
	}

	@Test
	fun `parseJson defaults optional fields`() {
		val json =
			"""
			{
			  "name": "Minimal",
			  "hide": [{"name": "h", "cmd": "c"}],
			  "restore": [{"name": "r", "cmd": "c"}],
			  "status": [{"name": "s", "cmd": "c"}]
			}
			""".trimIndent()

		val config = OemConfig.parseJson("min", json)

		assertEquals("Minimal", config.name)
		assertEquals("", config.hide[0].description)
		assertTrue(config.hide[0].persistent)
	}

	@Test
	fun `parseJson rejects unknown schema version`() {
		val json =
			"""
			{
			  "schema_version": 999,
			  "name": "Future",
			  "hide": [],
			  "restore": [],
			  "status": []
			}
			""".trimIndent()

		assertThrows(OemConfigException::class.java) {
			OemConfig.parseJson("future", json)
		}
	}

	@Test
	fun `parseJson allows missing schema_version`() {
		val json =
			"""
			{
			  "name": "Legacy",
			  "hide": [{"name": "h", "cmd": "c"}],
			  "restore": [{"name": "r", "cmd": "c"}],
			  "status": [{"name": "s", "cmd": "c"}]
			}
			""".trimIndent()

		val config = OemConfig.parseJson("legacy", json)
		assertEquals("Legacy", config.name)
	}

	@Test
	fun `parseJson throws on malformed JSON`() {
		assertThrows(JSONException::class.java) {
			OemConfig.parseJson("bad", "not json")
		}
	}

	@Test
	fun `parseJson throws on missing required field`() {
		val json =
			"""
			{
			  "name": "Incomplete"
			}
			""".trimIndent()

		assertThrows(JSONException::class.java) {
			OemConfig.parseJson("inc", json)
		}
	}

	@Test
	fun `parseJson handles empty command arrays`() {
		val json =
			"""
			{
			  "name": "Empty",
			  "hide": [],
			  "restore": [],
			  "status": []
			}
			""".trimIndent()

		val config = OemConfig.parseJson("empty", json)
		assertEquals("Empty", config.name)
		assertTrue(config.hide.isEmpty())
		assertTrue(config.restore.isEmpty())
		assertTrue(config.status.isEmpty())
	}

	@Test
	fun `parseJson handles multiple commands`() {
		val json =
			"""
			{
			  "name": "Multi",
			  "hide": [
			    {"name": "a", "cmd": "cmd_a"},
			    {"name": "b", "cmd": "cmd_b"},
			    {"name": "c", "cmd": "cmd_c"}
			  ],
			  "restore": [{"name": "r", "cmd": "cmd_r"}],
			  "status": [{"name": "s", "cmd": "cmd_s"}]
			}
			""".trimIndent()

		val config = OemConfig.parseJson("multi", json)
		assertEquals(3, config.hide.size)
		assertEquals("a", config.hide[0].name)
		assertEquals("b", config.hide[1].name)
		assertEquals("c", config.hide[2].name)
	}
}
