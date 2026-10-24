package dev.yoanndev90.statusbarhider.overlay.poll

import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arming rules only. The coroutines that consume [PollPlanner.Plan] stay
 * inside the service and are validated by manual device checks (screen on/off
 * cycles and per-toggle re-arming), not from here.
 */
class PollPlannerTest {
	/** Prefs tuned so every loop would be wanted if the screen were on. */
	private fun everythingOn() =
		OverlayPrefs()
			.copy(
				showBandwidth = true,
				showMobileData = true,
				hideBarInApps = true,
				hiddenApps = listOf("com.example.app"),
				burnInMin = 5
			)

	@Test
	fun `screen off disarms every loop regardless of prefs`() {
		assertEquals(
			PollPlanner.Plan(bandwidth = false, mobile = false, foreground = false, burnIn = false),
			PollPlanner.plan(everythingOn(), screenOn = false)
		)
	}

	@Test
	fun `stock prefs on a lit screen arm only the loops that need no toggle`() {
		// showBandwidth=false and hideBarInApps=false (empty list) stay off;
		// showMobileData=true and burnInMin=5 are on by default.
		assertEquals(
			PollPlanner.Plan(bandwidth = false, mobile = true, foreground = false, burnIn = true),
			PollPlanner.plan(OverlayPrefs(), screenOn = true)
		)
	}

	@Test
	fun `bandwidth and mobile follow their own toggles`() {
		val on = OverlayPrefs().copy(showBandwidth = true, showMobileData = true)
		assertTrue(PollPlanner.plan(on, screenOn = true).bandwidth)
		assertTrue(PollPlanner.plan(on, screenOn = true).mobile)

		val off = OverlayPrefs().copy(showBandwidth = false, showMobileData = false)
		assertFalse(PollPlanner.plan(off, screenOn = true).bandwidth)
		assertFalse(PollPlanner.plan(off, screenOn = true).mobile)
	}

	@Test
	fun `foreground poll needs the toggle, a non-empty list and a lit screen`() {
		val armed = OverlayPrefs().copy(hideBarInApps = true, hiddenApps = listOf("com.example.app"))

		assertTrue(PollPlanner.plan(armed, screenOn = true).foreground)

		// Screen off kills it even when fully armed.
		assertFalse(PollPlanner.plan(armed, screenOn = false).foreground)

		// Armed with nothing to hide: the 2 s poll would not earn its keep.
		assertFalse(
			PollPlanner.plan(armed.copy(hiddenApps = emptyList()), screenOn = true).foreground
		)

		// A populated list is not enough while the feature itself is off.
		assertFalse(
			PollPlanner.plan(armed.copy(hideBarInApps = false), screenOn = true).foreground
		)
	}

	@Test
	fun `burn-in loop needs a non-zero offset`() {
		assertEquals(
			false,
			PollPlanner.plan(OverlayPrefs().copy(burnInMin = 0), screenOn = true).burnIn
		)
		assertTrue(PollPlanner.plan(OverlayPrefs().copy(burnInMin = 1), screenOn = true).burnIn)
		assertFalse(PollPlanner.plan(OverlayPrefs().copy(burnInMin = 1), screenOn = false).burnIn)
	}
}
