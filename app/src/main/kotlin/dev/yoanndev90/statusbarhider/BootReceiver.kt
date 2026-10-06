package dev.yoanndev90.statusbarhider

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.OverlayController
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Re-applies the hide commands after a reboot (opt-out via the
 * "Auto-hide on boot" switch, which is part of the overlay prefs). After a
 * package update it only restarts the custom bar - the disable flags live in
 * SystemUI and are still applied.
 *
 * Every command runs in a coroutine on [scope]: Shizuku dispatches its
 * binder callbacks on the main looper, and one hide pass is several shell
 * commands with a 15 s timeout each, which would ANR the receiver. [goAsync]
 * only covers the short window where Shizuku is already up; a late binder is
 * picked up afterwards by a sticky listener, without holding the broadcast open.
 */
class BootReceiver : BroadcastReceiver() {
	companion object {
		private const val TAG = "StatusBarHider"

		/** How long the broadcast stays open while waiting for the Shizuku binder. */
		private const val BINDER_WAIT_MS = 6_000L

		/** Stop waiting for a late binder after this long. */
		private const val LISTENER_TIMEOUT_MS = 60_000L

		/** Poll interval while the initial binder wait runs. */
		private const val BINDER_POLL_MS = 250L

		/** Background scope; a late binder finishes after the broadcast is released. */
		private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	}

	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
			intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
		) {
			return
		}

		// The first lines below read SharedPreferences and the log file, and
		// enabling the bar reaches startForegroundService: a broadcast has 10 s
		// before the app is ANR'd, so all of it runs off the main thread with
		// goAsync held for the duration.
		val app = context.applicationContext as? Application ?: return
		val pending = goAsync()
		var released = false
		val release = {
			if (!released) {
				released = true
				pending.finish()
			}
		}
		scope.launch {
			try {
				if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
					onPackageReplaced(app)
				} else {
					onBootCompleted(app, release)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Boot broadcast failed", e)
			} finally {
				release()
			}
		}
	}

	/**
	 * Restarts the bar, then re-applies the hide commands. [release] hands the
	 * broadcast back to the system when the Shizuku binder is still starting -
	 * holding goAsync too long triggers an ANR - and a late binder is picked up
	 * afterwards without the broadcast open.
	 */
	private suspend fun onBootCompleted(
		app: Application,
		release: () -> Unit
	) {
		val prefs = OverlayPrefsRepository
			.getInstance(app)
			.state.value

		// The overlay is a plain window - no Shizuku needed, so it can come back
		// even when the hide pass below is disabled or fails.
		if (prefs.autoStartBar) {
			log(app, "Auto-starting the custom bar on boot")
			OverlayController.setEnabled(app, true)
		}

		if (!prefs.autoHideBoot) {
			Log.i(TAG, "Auto-hide on boot is disabled - skipping")
			return
		}
		log(app, "Boot completed - scheduling status bar hide")

		if (awaitInitialBinder()) {
			runAutoHide(app)
		} else {
			release()
			if (awaitLateBinder()) runAutoHide(app)
		}
	}

	/**
	 * The overlay window dies with the process on every update, but the disable
	 * flags live in SystemUI and are still applied - only bring the bar back.
	 */
	private fun onPackageReplaced(app: Context) {
		val enabled = OverlayPrefsRepository
			.getInstance(app)
			.state.value.enabled
		if (!enabled) return
		log(app, "Restarting custom overlay after update")
		OverlayController.setEnabled(app, true)
	}

	/** Polls the binder for up to [BINDER_WAIT_MS]; true when it answered. */
	private suspend fun awaitInitialBinder(): Boolean {
		val deadline = System.currentTimeMillis() + BINDER_WAIT_MS
		while (!Shizuku.pingBinder() && System.currentTimeMillis() < deadline) {
			delay(BINDER_POLL_MS)
		}
		return Shizuku.pingBinder()
	}

	/** Suspends until a late binder arrives, or [LISTENER_TIMEOUT_MS] elapses. */
	private suspend fun awaitLateBinder(): Boolean {
		val received =
			callbackFlow {
				val listener =
					object : Shizuku.OnBinderReceivedListener {
						override fun onBinderReceived() {
							trySend(Unit)
							close()
						}
					}
				// Sticky: fires immediately when the binder arrived between the poll
				// above and this line, so there is no race window.
				Shizuku.addBinderReceivedListenerSticky(listener)
				val timeout =
					launch {
						delay(LISTENER_TIMEOUT_MS)
						close()
					}
				awaitClose {
					timeout.cancel()
					Shizuku.removeBinderReceivedListener(listener)
				}
			}.firstOrNull()
		return received != null
	}

	private suspend fun runAutoHide(app: Application) {
		try {
			if (!Shizuku.pingBinder()) {
				log(app, "Shizuku binder not available - skipping auto-hide")
				return
			}
			if (!ShellRunner.granted()) {
				log(app, "Shizuku not authorized - skipping auto-hide")
				return
			}
			// Gated: the boot pass must not interleave with a tile tap or the
			// unlock re-apply. A refusal (busy) is logged by ShellRunner itself.
			ShellRunner.runSequence(app, null) {
				val oem = OemRepository.getInstance(app).refresh()
				if (oem.untested) Log.w(TAG, "'${oem.name}' config is untested on this device")
				for (note in oem.notes) log(app, "note: $note")

				val result = HideInteractor.applyHide(app)
				for (line in result.lines) log(app, line)
				log(app, if (result.ok) "Auto-hide done" else "Auto-hide finished with failures")

				if (OverlayPrefsRepository
						.getInstance(app)
						.state.value.enabled
				) {
					log(app, "Restarting custom overlay")
					StatusBarOverlayService.start(app)
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Log.e(TAG, "Auto-hide failed", e)
			log(app, "Boot auto-hide failed: ${e.message}")
		}
	}

	/** logcat + persistent app log (the latter is readable from the app after boot). */
	private fun log(
		context: Context,
		line: String
	) {
		Log.i(TAG, line)
		LogStore.append(context, line)
	}
}
