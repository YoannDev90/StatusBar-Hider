package dev.yoanndev90.statusbarhider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.shizuku.ShizukuCmd
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideController
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Re-applies the hide commands after a reboot (opt-out via the
 * "Auto-hide on boot" switch, which is part of the overlay prefs).
 *
 * Every command runs on a dedicated background thread: Shizuku dispatches its
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

		private val executor =
			Executors.newSingleThreadExecutor { r ->
				Thread(r, "boot-hide").apply { isDaemon = true }
			}
	}

	override fun onReceive(
		context: Context,
		intent: Intent
	) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

		val app = context.applicationContext
		if (!OverlayPrefsRepository
				.getInstance(app)
				.state.value.autoHideBoot
		) {
			Log.i(TAG, "Auto-hide on boot is disabled - skipping")
			return
		}
		log(app, "Boot completed - scheduling status bar hide")

		val pending = goAsync()
		val handler = Handler(Looper.getMainLooper())
		executor.execute {
			try {
				val deadline = System.currentTimeMillis() + BINDER_WAIT_MS
				while (!Shizuku.pingBinder() && System.currentTimeMillis() < deadline) {
					try {
						Thread.sleep(250)
					} catch (e: InterruptedException) {
						break
					}
				}
				if (Shizuku.pingBinder()) {
					runAutoHide(app)
				} else {
					// Shizuku is still starting up: release the broadcast (holding
					// goAsync too long triggers an ANR) and finish when it arrives.
					waitForLateBinder(app, handler)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Boot auto-hide failed", e)
			} finally {
				pending.finish()
			}
		}
	}

	/** Registers a sticky binder listener; runs the hide as soon as Shizuku is up. */
	private fun waitForLateBinder(
		app: Context,
		handler: Handler
	) {
		var listener: Shizuku.OnBinderReceivedListener? = null
		val timeout = Runnable { listener?.let { Shizuku.removeBinderReceivedListener(it) } }
		handler.postDelayed(timeout, LISTENER_TIMEOUT_MS)
		listener =
			object : Shizuku.OnBinderReceivedListener {
				override fun onBinderReceived() {
					handler.removeCallbacks(timeout)
					Shizuku.removeBinderReceivedListener(this)
					executor.execute { runAutoHide(app) }
				}
			}
		// Sticky: fires immediately when the binder arrived between the poll above
		// and this line, so there is no race window.
		Shizuku.addBinderReceivedListenerSticky(listener)
	}

	private fun runAutoHide(app: Context) {
		try {
			if (!Shizuku.pingBinder()) {
				log(app, "Shizuku binder not available - skipping auto-hide")
				return
			}
			if (!ShizukuCmd.granted()) {
				log(app, "Shizuku not authorized - skipping auto-hide")
				return
			}
			val oem = OemRepository.getInstance(app).refresh()
			if (oem.untested) Log.w(TAG, "'${oem.name}' config is untested on this device")
			for (note in oem.notes) log(app, "note: $note")

			val result = runBlocking { HideController.applyHide(app) }
			for (line in result.lines) log(app, line)
			log(app, if (result.ok) "Auto-hide done" else "Auto-hide finished with failures")

			if (OverlayPrefsRepository
					.getInstance(app)
					.state.value.enabled
			) {
				log(app, "Restarting custom overlay")
				StatusBarOverlayService.start(app)
			}
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
