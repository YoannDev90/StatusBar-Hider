package dev.yoanndev90.statusbarhider.core.watcher

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.CommandExecutor
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.shizuku.ShizukuCmd
import dev.yoanndev90.statusbarhider.hide.HideController
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

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

	private val handler = Handler(Looper.getMainLooper())
	private val executor =
		Executors.newSingleThreadExecutor { r ->
			Thread(r, "systemui-watch").apply { isDaemon = true }
		}

	/** Application context of the last owner; a process ref, so no component is kept alive. */
	@Volatile
	private var app: Application? = null

	@Volatile
	private var baselinePid: String? = null

	@Volatile
	private var missingShizukuLogged = false

	private val tick =
		Runnable {
			// Re-arm first: a failing check must not silently kill the loop.
			schedule()
			executor.execute { checkOnce() }
		}

	/** Registers [owner]; the first owner starts the 60 s loop. */
	fun start(
		owner: Any,
		context: Context
	) {
		val first: Boolean
		synchronized(owners) {
			first = owners.isEmpty()
			owners.add(owner)
		}
		if (!first) return
		app = context.applicationContext as? Application
		baselinePid = null
		missingShizukuLogged = false
		handler.removeCallbacks(tick)
		handler.post(tick)
		Log.i(TAG, "Watcher started for ${owner.javaClass.simpleName}")
	}

	/** Unregisters [owner]; the last one stops the loop. */
	fun stop(owner: Any) {
		val empty: Boolean
		synchronized(owners) {
			owners.remove(owner)
			empty = owners.isEmpty()
		}
		if (empty) handler.removeCallbacks(tick)
	}

	private fun schedule() {
		handler.removeCallbacks(tick)
		if (!ownersEmpty()) handler.postDelayed(tick, TICK_MS)
	}

	private fun ownersEmpty(): Boolean = synchronized(owners) { owners.isEmpty() }

	/** One pass: read the SystemUI pid, re-apply when it moved. Runs on the executor. */
	private fun checkOnce() {
		if (ownersEmpty()) return
		val application = app ?: return
		// Nothing to protect when the system bar is visible.
		if (!HideController.isHidden(application)) return
		if (!ShizukuCmd.granted()) {
			if (!missingShizukuLogged) {
				missingShizukuLogged = true
				Log.w(TAG, "Shizuku not granted - watcher idle")
			}
			return
		}
		val pid = readSystemUiPid(application)?.trim().orEmpty()
		if (pid.isEmpty()) return
		val baseline = baselinePid
		if (baseline == null) {
			baselinePid = pid
			return
		}
		if (pid == baseline) return
		// Update first: applyHide takes seconds, the next tick must not re-fire.
		baselinePid = pid
		missingShizukuLogged = false
		val line = application.getString(R.string.log_systemui_restarted, baseline, pid)
		Log.i(TAG, line)
		LogStore.append(application, line)
		CommandExecutor.run(application, R.string.log_reapply_hide) {
			val result = HideController.applyHide(application)
			result.lines.forEach { LogStore.append(application, it) }
		}
	}

	private fun readSystemUiPid(context: Context): String? =
		try {
			ShizukuCmd.run(context, PID_CMD, CMD_TIMEOUT_SEC).out
		} catch (_: Exception) {
			null
		}
}
