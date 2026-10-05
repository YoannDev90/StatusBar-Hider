package dev.yoanndev90.statusbarhider.core.log

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * Bounded, timestamped in-app log.
 *
 * The same lines are mirrored to `cache/logs/statusbarhider.log` so the history
 * survives process death (boot-time failures would otherwise only be in logcat,
 * which the user may not be able to read) and can be shared as a file.
 */
object LogStore {
	private const val TAG = "LogStore"
	private const val MAX_LINES = 500
	private const val FILE_NAME = "statusbarhider.log"

	private val lock = Any()
	private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val onceKeys = Collections.synchronizedSet(mutableSetOf<String>())

	private val _lines = MutableStateFlow<List<String>>(emptyList())
	val lines: StateFlow<List<String>> = _lines.asStateFlow()

	@Volatile
	private var loaded = false

	/** Appends a line (prefixed with the time) and mirrors it to the log file. */
	fun append(
		context: Context,
		line: String
	) {
		val app = context.applicationContext
		synchronized(lock) {
			ensureLoadedLocked(app)
			// timeFormat is a shared SimpleDateFormat and append() is reached
			// from both the main thread and Dispatchers.IO: format under the lock.
			val stamped = "${timeFormat.format(Date())} $line"
			val next = _lines.value + stamped
			_lines.value = if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
		}
		// Mirroring rewrites the whole file, and UI callers append from the main
		// thread (selectOem, setOverlayEnabled...). Each writer dumps the current
		// snapshot under the lock, so the last one always holds the full history.
		ioScope.launch { synchronized(lock) { writeLocked(app) } }
	}

	/**
	 * Like [append], but drops repeats of [key]. Polling call sites rethrow
	 * the same failure every cycle, and mirroring rewrites the whole file per
	 * line, so repeating would grow the log quadratically.
	 */
	fun appendOnce(
		context: Context,
		key: String,
		line: String
	) {
		if (onceKeys.add(key)) append(context, line)
	}

	/** Reloads the persisted lines into [lines] (no-op once loaded). */
	fun ensureLoaded(context: Context) {
		synchronized(lock) { ensureLoadedLocked(context) }
	}

	/** [Intent] sharing the log file, or null when it could not be built. */
	fun shareIntent(context: Context): Intent? =
		try {
			ensureLoaded(context)
			val uri =
				FileProvider.getUriForFile(
					context,
					"${context.packageName}.fileprovider",
					file(context)
				)
			Intent(Intent.ACTION_SEND).apply {
				type = "text/plain"
				putExtra(Intent.EXTRA_STREAM, uri)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
		} catch (e: Exception) {
			Log.w(TAG, "Failed to build share intent", e)
			null
		}

	/** The backing file (exists even when empty, so sharing always has a target). */
	fun file(context: Context): File = File(dir(context), FILE_NAME)

	private fun dir(context: Context): File = File(context.cacheDir, "logs")

	private fun ensureLoadedLocked(context: Context) {
		if (loaded) return
		loaded = true
		val stored =
			try {
				val file = file(context)
				if (file.isFile) {
					file
						.readText()
						.lineSequence()
						.filter { it.isNotEmpty() }
						.toList()
				} else {
					emptyList()
				}
			} catch (e: Exception) {
				Log.w(TAG, "Failed to read log file", e)
				emptyList()
			}
		_lines.value = if (stored.size > MAX_LINES) stored.takeLast(MAX_LINES) else stored
	}

	/** Rewrite with the current (already bounded) content; runs under [lock]. */
	private fun writeLocked(context: Context) {
		try {
			val file = file(context)
			file.parentFile?.mkdirs()
			file.writeText(_lines.value.joinToString("\n") + "\n")
		} catch (e: Exception) {
			Log.w(TAG, "Failed to write log file", e)
		}
	}
}
