package dev.yoanndev90.statusbarhider.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SisterBuildTest {
	@Test
	fun `release build points at the debug package`() {
		assertEquals("dev.yoanndev90.statusbarhider.debug", SisterBuild.sisterPackageOf("dev.yoanndev90.statusbarhider"))
	}

	@Test
	fun `debug build points back at the release package`() {
		assertEquals("dev.yoanndev90.statusbarhider", SisterBuild.sisterPackageOf("dev.yoanndev90.statusbarhider.debug"))
	}

	@Test
	fun `only the sister package passes the provider gate`() {
		assertTrue(SisterBuild.isSisterCall("dev.yoanndev90.statusbarhider", "dev.yoanndev90.statusbarhider.debug"))
		assertTrue(SisterBuild.isSisterCall("dev.yoanndev90.statusbarhider.debug", "dev.yoanndev90.statusbarhider"))
	}

	@Test
	fun `self, strangers and unknown callers are refused`() {
		assertFalse(SisterBuild.isSisterCall("dev.yoanndev90.statusbarhider", "dev.yoanndev90.statusbarhider"))
		assertFalse(SisterBuild.isSisterCall("dev.yoanndev90.statusbarhider", "com.example.other"))
		assertFalse(SisterBuild.isSisterCall("dev.yoanndev90.statusbarhider", null))
	}

	@Test
	fun `authority follows the package`() {
		assertEquals(
			"dev.yoanndev90.statusbarhider.debug.settingsprovider",
			SisterBuild.authorityOf("dev.yoanndev90.statusbarhider.debug")
		)
	}
}
