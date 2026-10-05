package dev.yoanndev90.statusbarhider.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlAuthTest {
	@Test
	fun `null or empty expected token never matches`() {
		assertFalse(ControlAuth.matches(null, "secret"))
		assertFalse(ControlAuth.matches("", "secret"))
	}

	@Test
	fun `null provided token never matches`() {
		assertFalse(ControlAuth.matches("secret", null))
	}

	@Test
	fun `exact token matches`() {
		assertTrue(ControlAuth.matches("secret", "secret"))
		assertTrue(ControlAuth.matches("36ecc50f68738a993c0a62f8f35ad5c4", "36ecc50f68738a993c0a62f8f35ad5c4"))
	}

	@Test
	fun `wrong or truncated token is rejected`() {
		assertFalse(ControlAuth.matches("secret", "secre"))
		assertFalse(ControlAuth.matches("secret", "secreT"))
		assertFalse(ControlAuth.matches("secret", "Secret"))
		assertFalse(ControlAuth.matches("secret", "secret "))
		assertFalse(ControlAuth.matches("secret", ""))
	}
}
