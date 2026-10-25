package dev.yoanndev90.statusbarhider.core.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one-way bridge: the renamed successor may read our blob, nobody
 * else may. Sister coverage stays in [SisterBuildTest].
 */
class SuccessorBuildTest {
	@Test
	fun `release successor passes the provider gate`() {
		assertTrue(SuccessorBuild.isSuccessorCall("dev.yoanndev90.corniche"))
	}

	@Test
	fun `debug successor passes the provider gate`() {
		assertTrue(SuccessorBuild.isSuccessorCall("dev.yoanndev90.corniche.debug"))
	}

	@Test
	fun `self, strangers and unknown callers are refused`() {
		assertFalse(SuccessorBuild.isSuccessorCall("dev.yoanndev90.statusbarhider"))
		assertFalse(SuccessorBuild.isSuccessorCall("com.example.other"))
		assertFalse(SuccessorBuild.isSuccessorCall(null))
	}

	@Test
	fun `near-miss package names are refused`() {
		assertFalse(SuccessorBuild.isSuccessorCall("dev.yoanndev90.corniche2"))
		assertFalse(SuccessorBuild.isSuccessorCall("dev.yoanndev90.corniche.debug2"))
		assertFalse(SuccessorBuild.isSuccessorCall("dev.yoanndev90.Corniche"))
	}

	@Test
	fun `the refusal wording names the successor package`() {
		assertTrue(SuccessorBuild.readableBy().contains("dev.yoanndev90.corniche"))
	}
}
