package dev.yoanndev90.statusbarhider.overlay.poll

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the overlay's restartable poll loops and their arming.
 *
 * Each loop runs in the service's [scope], so destroy (which cancels the
 * scope) tears them down; a screen-off calls [cancelAll] and a screen-on (or a
 * prefs change) re-arms through the `schedule*` helpers. Whether a loop is
 * wanted at all comes from [PollPlanner], recomputed from the live prefs and
 * screen state, so the arming site and a loop's own exit check cannot drift
 * apart.
 *
 * The loops' bodies live behind [PollHost], which the service implements: this
 * class holds no Android type, only the coroutines and the "wanted?" decisions.
 */
internal class OverlayPoller(
	private val scope: CoroutineScope,
	private val host: PollHost
) {
	companion object {
		private const val FOREGROUND_POLL_MS = 2_000L

		/**
		 * The label and the signal bars are what the eye tracks; the general
		 * poll (5..60 s) is far too slow for a level that moves on its own.
		 */
		private const val MOBILE_POLL_MS = 2_000L
	}

	private var pollJob: Job? = null
	private var burnInJob: Job? = null
	private var bandwidthJob: Job? = null
	private var foregroundJob: Job? = null
	private var mobileJob: Job? = null

	/** The missing-usage-access hint is logged once per service run, not per poll. */
	private var usageHintLogged = false

	/** Recomputed from the live prefs and screen state; see [PollPlanner]. */
	private val plan: PollPlanner.Plan
		get() = PollPlanner.plan(host.currentPrefs, host.isScreenOn)

	/** Drops every loop. The screen-off path uses it; destroy cancels the scope. */
	fun cancelAll() {
		pollJob?.cancel()
		burnInJob?.cancel()
		bandwidthJob?.cancel()
		foregroundJob?.cancel()
		mobileJob?.cancel()
	}

	/** Alarm / calendar / connectivity poll; the screen-off cancel ends it. */
	fun schedulePoll() {
		pollJob?.cancel()
		pollJob = scope.launch { pollLoop() }
	}

	/**
	 * (Re)arms the 1s bandwidth sampler. Every path that can turn the screen or
	 * the toggle back on must call it, otherwise the loop dies on the first
	 * screen-off / screen-on cycle.
	 */
	fun scheduleBandwidth() {
		bandwidthJob?.cancel()
		bandwidthJob = null
		if (plan.bandwidth) {
			bandwidthJob = scope.launch { bandwidthLoop() }
		}
	}

	/**
	 * (Re)arms the 2 s cellular refresh. The loop dies with the screen or the
	 * "Mobile data" toggle; turning the toggle off must go through here so the
	 * bar does not keep polling a radio it no longer draws.
	 */
	fun scheduleMobile() {
		mobileJob?.cancel()
		mobileJob = null
		if (plan.mobile) {
			mobileJob = scope.launch { mobileLoop() }
		}
	}

	/** Periodic burn-in shift; re-reads `burnInMin` every cycle. */
	fun scheduleBurnIn() {
		burnInJob?.cancel()
		burnInJob = null
		if (plan.burnIn) {
			burnInJob = scope.launch { burnInLoop() }
		}
	}

	/**
	 * (Re)arms the 2 s foreground-app poll. Turning the feature off, emptying
	 * the list or switching the screen off must go through here, otherwise the
	 * loop either dies or keeps a suppressed bar suppressed forever.
	 */
	fun scheduleForegroundPoll() {
		foregroundJob?.cancel()
		foregroundJob = null
		if (!plan.foreground) {
			host.ensureNotSuppressed()
			return
		}
		if (!host.usageAccessGranted) {
			if (!usageHintLogged) {
				usageHintLogged = true
				host.onUsageAccessMissing()
			}
			host.ensureNotSuppressed()
			return
		}
		foregroundJob = scope.launch { foregroundLoop() }
	}

	private suspend fun CoroutineScope.pollLoop() {
		while (isActive && host.isScreenOn) {
			host.refreshPolled()
			delay((host.currentPrefs.updateIntervalSec.coerceIn(5, 60) * 1000).toLong())
		}
	}

	/** 1 s bandwidth sampler; the loop dies with the screen or the toggle. */
	private suspend fun CoroutineScope.bandwidthLoop() {
		while (isActive && host.isScreenOn && host.currentPrefs.showBandwidth) {
			host.sampleBandwidth()
			delay(1000L)
		}
	}

	/** 2 s cellular label / signal-level refresh; see [scheduleMobile]. */
	private suspend fun CoroutineScope.mobileLoop() {
		while (isActive && host.isScreenOn && host.currentPrefs.showMobileData) {
			host.refreshMobile()
			delay(MOBILE_POLL_MS)
		}
	}

	/** The 2 s poll only earns its keep while something could actually hide. */
	private fun CoroutineScope.foregroundPollWanted(): Boolean = isActive && plan.foreground

	/** 2 s foreground-app poll; the loop dies with the feature or the screen. */
	private suspend fun CoroutineScope.foregroundLoop() {
		while (foregroundPollWanted()) {
			host.refreshSuppressed()
			delay(FOREGROUND_POLL_MS)
		}
	}

	private suspend fun CoroutineScope.burnInLoop() {
		while (isActive && host.isScreenOn && host.currentPrefs.burnInMin > 0) {
			delay((host.currentPrefs.burnInMin.coerceIn(1, 30) * 60 * 1000).toLong())
			if (!isActive) return
			host.shiftForBurnIn()
		}
	}
}
