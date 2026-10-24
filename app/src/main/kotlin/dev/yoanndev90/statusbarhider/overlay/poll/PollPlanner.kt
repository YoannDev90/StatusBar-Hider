package dev.yoanndev90.statusbarhider.overlay.poll

import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs

/**
 * Decides which conditional poll loops the overlay service must run.
 *
 * Pure decision logic: no Android and no coroutines, so the "should loop X be
 * armed?" rules live in exactly one place and are unit-testable. The service
 * keeps the coroutines (tied to a service's life, hence hard to test) and the
 * Android-only gate -- the usage-access check -- and arms / drops each job
 * from [Plan].
 *
 * The unconditional general poll is deliberately absent: it has no condition
 * to evaluate, so arming it stays a plain call in the service.
 */
object PollPlanner {
	/**
	 * Which conditional loops are wanted. A data class so a re-arm can compare
	 * two plans and only restart the loops whose answer actually changed.
	 *
	 * @property bandwidth 1 s bandwidth sampler, gated by [OverlayPrefs.showBandwidth].
	 * @property mobile 2 s cellular refresh, gated by [OverlayPrefs.showMobileData].
	 * @property foreground 2 s blacklist poll: armed, list non-empty, screen on.
	 * @property burnIn periodic burn-in shift, gated by a non-zero [OverlayPrefs.burnInMin].
	 */
	data class Plan(
		val bandwidth: Boolean,
		val mobile: Boolean,
		val foreground: Boolean,
		val burnIn: Boolean
	)

	/**
	 * The plan for [prefs] at the given screen state. Every loop additionally
	 * requires a lit screen, so [screenOn] short-circuits them all.
	 */
	fun plan(prefs: OverlayPrefs, screenOn: Boolean): Plan =
		Plan(
			bandwidth = prefs.showBandwidth && screenOn,
			mobile = prefs.showMobileData && screenOn,
			foreground = prefs.hideBarInApps && prefs.hiddenApps.isNotEmpty() && screenOn,
			burnIn = prefs.burnInMin > 0 && screenOn
		)
}
