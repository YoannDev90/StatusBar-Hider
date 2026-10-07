package dev.yoanndev90.statusbarhider.core.command

import android.app.Application
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.StringRes
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.oem.OemConfig
import dev.yoanndev90.statusbarhider.data.OemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The single path every shell command takes.
 *
 * Three stacks used to run the same job -- a blocking transport
 * (`ShizukuCmd`), a suspend wrapper (`CommandRunner`) and the busy-gated
 * orchestrator (`CommandExecutor`) -- with timeouts and the Shizuku
 * permission check applied inconsistently, and nothing stopped two of them
 * from running `sh -c` at the same time. [ShellRunner] folds them into one:
 *
 * - the suspend [run] executes a single command **serialized** app-wide
 *   through [mutex], so a watcher `dumpsys` read can never land in the middle
 *   of a `settings put` sequence;
 * - the [Application] overload runs a whole sequence behind [busy] with the
 *   progress line (the former `CommandExecutor`); the busy flag is taken with
 *   `compareAndSet`, so two callers can no longer both pass a check-then-set;
 * - [runSequence] is the same gate, but suspend: a caller that needs the
 *   outcome (the tile, the boot pass) waits for it instead of starting a
 *   second sequence beside it;
 * - [granted] is the one Shizuku readiness check.
 */
object ShellRunner {
	private const val TAG = "ShellRunner"

	/**
	 * Budget for reading stdout/stderr once the command has exited, kept apart
	 * from the command timeout: a command that answers at the last second would
	 * otherwise get ~0 ms and its output would be dropped.
	 */
	private const val DRAIN_TIMEOUT_SEC = 5L

	/** Exit code plus combined output (stdout, then stderr when not empty). */
	data class Result(
		val exit: Int,
		val out: String
	)

	/** Exactly one `sh -c` runs at any moment, whoever asked for it. */
	private val mutex = Mutex()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _busy = MutableStateFlow(false)

	/** True while a sequence is in flight; the shell shows a progress bar on it. */
	val busy: StateFlow<Boolean> = _busy.asStateFlow()

	/** One thread per pipe plus one for waitFor(); all daemon so they never block exit. */
	private val executor =
		Executors.newCachedThreadPool { r ->
			Thread(r, "shizuku-cmd").apply { isDaemon = true }
		}

	/** True when the Shizuku binder is up and our permission is granted. */
	fun granted(): Boolean =
		try {
			Shizuku.pingBinder() &&
				Shizuku.checkSelfPermission() ==
				android.content.pm.PackageManager.PERMISSION_GRANTED
		} catch (e: Exception) {
			// Shizuku's requireService() throws instead of answering when the
			// binder dies between pingBinder() and the permission check, and
			// every readiness gate sits on this function.
			Log.w(TAG, "Shizuku permission check failed", e)
			false
		}

	/**
	 * Fire-and-forget [runSequence] on the app scope: a command started from a
	 * screen keeps running across rotation instead of being cancelled halfway
	 * through a `settings put` sequence.
	 *
	 * @param app used for resources and as the OEM / log lookup context.
	 * @param labelRes resource id of the progress line shown before [block]
	 * runs, or null to run without one (automatic re-applies still log failures
	 * and refusals -- an auto path that dropped them silently could leave the
	 * bar unhidden with nothing in the log to say why).
	 * @param block the command set to run; it receives the active OEM config.
	 */
	fun run(
		app: Application,
		@StringRes labelRes: Int?,
		block: suspend (oem: OemConfig) -> Unit
	) {
		scope.launch { runSequence(app, labelRes, block) }
	}

	/**
	 * Same gate as [run], but suspends until the sequence has finished and
	 * reports whether it ran: `false` means the shell was refused (no Shizuku
	 * grant, or another sequence already holds [busy]) and [block] never
	 * started. Callers that own a result - the tile, the boot pass, the
	 * SystemUI watcher - must use this so two sequences cannot interleave
	 * command by command and leave the persisted hide state to a coin toss.
	 *
	 * One [block] at a time, taken with `compareAndSet` so two callers can no
	 * longer both pass a check-then-set; the flag is cleared in `finally`, so a
	 * cancelled coroutine cannot leave the shell busy forever.
	 */
	suspend fun runSequence(
		app: Context,
		@StringRes labelRes: Int?,
		block: suspend (oem: OemConfig) -> Unit
	): Boolean {
		if (!granted()) {
			log(app, R.string.log_shizuku_not_authorized)
			return false
		}
		if (!_busy.compareAndSet(false, true)) {
			log(app, R.string.log_busy)
			return false
		}
		return try {
			if (labelRes != null) {
				log(app, R.string.log_progress, app.getString(labelRes))
			}
			block(OemRepository.getInstance(app).config.value)
			true
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			log(app, R.string.log_error, e.message ?: e.toString())
			false
		} finally {
			_busy.value = false
		}
	}

	/**
	 * Runs [cmd] through `sh -c` and returns the **real** exit code plus the
	 * combined output, serialized app-wide: callers queue instead of spawning
	 * parallel shell processes.
	 */
	suspend fun run(
		context: Context,
		cmd: String,
		timeoutSec: Long = 15
	): Result = withContext(Dispatchers.IO) { mutex.withLock { execute(context, cmd, timeoutSec) } }

	/**
	 * Blocking transport for one command; must only be called with [mutex]
	 * held.
	 *
	 * stdout and stderr are drained concurrently: reading one pipe at a time
	 * deadlocks as soon as the other one fills its 64 KB buffer.
	 */
	private fun execute(
		context: Context,
		cmd: String,
		timeoutSec: Long
	): Result {
		val binder = Shizuku.getBinder() ?: return Result(-1, context.getString(R.string.err_shizuku_binder_unavailable))
		val service = IShizukuService.Stub.asInterface(binder)
		val remote =
			try {
				service.newProcess(arrayOf("sh", "-c", cmd), null, null)
			} catch (e: Exception) {
				Log.e(TAG, "Failed to start process for: $cmd", e)
				return Result(-1, context.getString(R.string.err_start_process, e.message))
			}

		val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
		try {
			val stdoutTask = executor.submit<String> { readFully(remote.inputStream) }
			val stderrTask = executor.submit<String> { readFully(remote.errorStream) }
			val exitTask = executor.submit<Int> { remote.waitFor() }

			val exit =
				try {
					exitTask.get(timeoutSec, TimeUnit.SECONDS)
				} catch (e: TimeoutException) {
					exitTask.cancel(true)
					Log.w(TAG, "Command timed out after ${timeoutSec}s: $cmd", e)
					return Result(-1, context.getString(R.string.err_command_timeout, timeoutSec))
				} catch (e: Exception) {
					Log.e(TAG, "waitFor failed for: $cmd", unwrap(e))
					return Result(-1, context.getString(R.string.err_start_process, unwrap(e).message))
				}

			// waitFor() may have consumed the whole budget: the pipes get a window
			// of their own, otherwise a command answering at the last second would
			// have its output dropped and reported as an empty success.
			val drainDeadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(DRAIN_TIMEOUT_SEC)
			return Result(
				exit,
				joinOutput(await(stdoutTask, drainDeadlineNs, cmd), await(stderrTask, drainDeadlineNs, cmd))
			)
		} finally {
			try {
				remote.destroy()
			} catch (e: Exception) {
				Log.w(TAG, "Failed to destroy remote process", e)
			}
		}
	}

	/** Reads a pipe to EOF, bounded by [deadlineNs]; a stuck reader is dropped, not waited on. */
	private fun await(
		task: Future<String>,
		deadlineNs: Long,
		cmd: String
	): String {
		val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()).coerceAtLeast(1)
		return try {
			task.get(remainingMs, TimeUnit.MILLISECONDS)
		} catch (e: Exception) {
			task.cancel(true)
			Log.w(TAG, "Dropped output of: $cmd", unwrap(e))
			""
		}
	}

	private fun readFully(fd: ParcelFileDescriptor?): String {
		if (fd == null) return ""
		return try {
			// `use` closes the descriptor: AutoCloseInputStream only does that in
			// close(), not at EOF, so without it every command leaks two fds to
			// the GC finalizer.
			ParcelFileDescriptor
				.AutoCloseInputStream(fd)
				.bufferedReader()
				.use { it.readText().trim() }
		} catch (e: Exception) {
			Log.w(TAG, "Failed to read process output", e)
			""
		}
	}

	private fun joinOutput(
		stdout: String,
		stderr: String
	): String =
		when {
			stderr.isEmpty() -> stdout
			stdout.isEmpty() -> stderr
			else -> "$stdout\n$stderr"
		}

	/** [ExecutionException] hides the real cause behind a generic wrapper. */
	private fun unwrap(e: Exception): Throwable = (e as? ExecutionException)?.cause ?: e

	private fun log(
		app: Context,
		@StringRes id: Int,
		vararg args: Any?
	) {
		LogStore.append(app, app.getString(id, *args))
	}
}
