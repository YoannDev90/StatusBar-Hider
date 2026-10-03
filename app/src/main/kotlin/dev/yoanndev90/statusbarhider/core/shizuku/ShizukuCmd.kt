package dev.yoanndev90.statusbarhider.core.shizuku

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ShizukuCmd {
	private const val TAG = "ShizukuCmd"

	data class Result(
		val exit: Int,
		val out: String
	)

	/** One thread per pipe plus one for waitFor(); all daemon so they never block exit. */
	private val executor =
		Executors.newCachedThreadPool { r ->
			Thread(r, "shizuku-cmd").apply { isDaemon = true }
		}

	fun granted(): Boolean =
		Shizuku.pingBinder() &&
			Shizuku.checkSelfPermission() ==
			android.content.pm.PackageManager.PERMISSION_GRANTED

	/**
	 * Runs [cmd] through `sh -c` and returns the **real** exit code plus the
	 * combined output (stdout, then stderr when it is not empty).
	 *
	 * stdout and stderr are drained concurrently: reading one pipe at a time
	 * deadlocks as soon as the other one fills its 64 KB buffer.
	 */
	fun run(
		context: Context,
		cmd: String,
		timeoutSec: Long = 15
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
					Log.w(TAG, "Command timed out after ${timeoutSec}s: $cmd")
					return Result(-1, context.getString(R.string.err_command_timeout, timeoutSec))
				} catch (e: Exception) {
					Log.e(TAG, "waitFor failed for: $cmd", unwrap(e))
					return Result(-1, context.getString(R.string.err_start_process, unwrap(e).message))
				}

			return Result(exit, joinOutput(await(stdoutTask, deadlineNs), await(stderrTask, deadlineNs)))
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
		deadlineNs: Long
	): String {
		val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()).coerceAtLeast(1)
		return try {
			task.get(remainingMs, TimeUnit.MILLISECONDS)
		} catch (e: Exception) {
			task.cancel(true)
			""
		}
	}

	private fun readFully(fd: ParcelFileDescriptor?): String {
		if (fd == null) return ""
		return try {
			ParcelFileDescriptor
				.AutoCloseInputStream(fd)
				.bufferedReader()
				.readText()
				.trim()
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
}
