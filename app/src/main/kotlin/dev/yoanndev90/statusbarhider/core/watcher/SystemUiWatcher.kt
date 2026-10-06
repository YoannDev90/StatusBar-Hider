package dev.yoanndev90.statusbarhider.core.watcher

import android.app.Application
import android.content.Context
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap

/**
 * Re-applies the hide commands when SystemUI restarts.
 *
 * The `cmd statusbar send-disable-flag` calls are volatile: they die with the
 * SystemUI process, and until now only a reboot (BootReceiver) put them back.
 * The watcher keeps the SystemUI pid as a baseline and re-runs the set as soon
 * as it changes - a crash, a force-stop or an update all look the same.
 *
 * It only runs while an owner (the Activity or the overlay service) is alive
 * and only spends one shell command a minute, so a device that is not hiding
 * anything pays nothing.
 */
object SystemUiWatcher {
	private const val TAG = "SystemUiWatcher"
	private const val TICK_MS = 60_000L
	private const val CMD_TIMEOUT_SEC = 5L

	/** Single command: pidof, with a ps fallback for devices without toybox pidof. */
	private const val PID_CMD =
		"pidof com.android.systemui || " +
			"ps -A -o PID=,NAME= | grep -m1 com.android.systemui | awk '{print $1}'"

	/** Owners currently interested in the watcher; empty = the loop stops. */
	private val owners: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

	/** Process-wide loop scope; only the loop [Job] below is ever cancelled. */
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	/** Current 60 s loop; null while no owner is registered. */
	@Volatile
	private var loop: Job? = null

	/** Application context of the last owner; a process ref, so no component is kept alive. */
	@Volatile
	private var app: Application? = null

	@Volatile
	private var baselinePid: String? = null

	@Volatile
	private var missingShizukuLogged = false

	/** Registers [owner]; the first owner starts the 60 s loop. */
	fun start(
		owner: Any,
		context: Context
	) {
		val started =
			synchronized(owners) {
				val first = owners.isEmpty()
				// The owner goes in whatever the outcome. stop() cancels the loop
				// as soon as the set empties, so an owner skipped here would let
				// the Activity tear the loop down while the overlay service is
				// still alive - and the re-apply it exists for.
				owners.add(owner)
				app = context.applicationContext as? Application
				if (first) {
					baselinePid = null
					missingShizukuLogged = false
					// Started under the lock: launching outside it let a start land
					// while a stopped owner's loop was still in delay(), leaving two
					// loops.
					loop = scope.launch { runLoop() }
				}
				first
			}
		if (started) Log.i(TAG, "Watcher started for ${owner.javaClass.simpleName}")
	}

	/** Unregisters [owner]; the last one stops the loop. */
	fun stop(owner: Any) {
		synchronized(owners) {
			owners.remove(owner)
			if (owners.isNotEmpty()) return
			loop?.cancel()
			loop = null
		}
	}

	/** One check now, then one every [TICK_MS] until the last owner leaves. */
	private suspend fun CoroutineScope.runLoop() {
		while (isActive && !ownersEmpty()) {
			try {
				checkOnce()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// A failing check must not silently kill the loop.
				Log.w(TAG, "watch tick failed", e)
			}
			delay(TICK_MS)
		}
	}

	private fun ownersEmpty(): Boolean = synchronized(owners) { owners.isEmpty() }

	/** One pass: read the SystemUI pid, re-apply when it moved. */
	private suspend fun checkOnce() {
		if (ownersEmpty()) return
		val application = app ?: return
		// Nothing to protect when the system bar is visible. The baseline goes
		// with it: keeping the old pid would make the first check after the next
		// hide look like a SystemUI restart and fire a duplicate sequence.
		if (!HideInteractor.isHidden(application)) {
			baselinePid = null
			return
		}
		if (!ShellRunner.granted()) {
			if (!missingShizukuLogged) {
				missingShizukuLogged = true
				Log.w(TAG, "Shizuku not granted - watcher idle")
			}
			return
		}
		missingShizukuLogged = false
		val pid = readSystemUiPid(application)?.trim().orEmpty()
		if (pid.isEmpty()) return
		val baseline = baselinePid
		if (baseline == null) {
			baselinePid = pid
			return
		}
		if (pid == baseline) return
		val line = application.getString(R.string.log_systemui_restarted, baseline, pid)
		// The baseline moves only once the sequence actually started. Advancing
		// it before a fire-and-forget launch recorded the restart and then lost
		// the re-apply when the shell refused the command as busy - the bar
		// stayed visible with nothing left to retry it.
		val started =
			ShellRunner.runSequence(application, R.string.log_reapply_hide) {
				baselinePid = pid
				Log.i(TAG, line)
				LogStore.append(application, line)
				HideInteractor.applyAndLog(application, hide = true)
			}
		if (!started) Log.w(TAG, "Re-apply refused - retrying on the next tick")
	}

	private suspend fun readSystemUiPid(context: Context): String? =
		try {
			val (exit, out) = ShellRunner.run(context, PID_CMD, CMD_TIMEOUT_SEC)
			// stderr is merged into `out`: a failure such as "sh: pidof: not
			// found" must never be taken for the pid.
			if (exit != 0) null else out
		} catch (e: Exception) {
			// A broken shell must not look like "SystemUI is gone" forever.
			Log.w(TAG, "readSystemUiPid", e)
			LogStore.appendOnce(
				context,
				"$TAG#readSystemUiPid",
				context.getString(R.string.log_error, "pidof: ${e.message}")
			)
			null
		}
}
