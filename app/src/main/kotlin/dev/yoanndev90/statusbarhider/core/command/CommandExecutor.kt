package dev.yoanndev90.statusbarhider.core.command

import android.app.Application
import androidx.annotation.StringRes
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.oem.OemConfig
import dev.yoanndev90.statusbarhider.core.shizuku.ShizukuCmd
import dev.yoanndev90.statusbarhider.data.OemRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs the blocking OEM shell commands, one at a time.
 *
 * Commands are blocking shell calls, so a second request while one is in
 * flight is refused instead of silently interleaved (and [busy] can then never
 * be cleared by the wrong job). The scope outlives the UI: a command started
 * from a screen keeps running across rotation instead of being cancelled
 * halfway through a `settings put` sequence.
 */
object CommandExecutor {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _busy = MutableStateFlow(false)

	/** True while a command is in flight; the shell shows a progress bar on it. */
	val busy: StateFlow<Boolean> = _busy.asStateFlow()

	/**
	 * @param app used for resources and as the OEM / log lookup context.
	 * @param labelRes resource id of the progress line shown before [block] runs.
	 * @param block the command set to run; it receives the active OEM config.
	 */
	fun run(
		app: Application,
		@StringRes labelRes: Int,
		block: suspend (oem: OemConfig) -> Unit
	) {
		if (!ShizukuCmd.granted()) {
			log(app, R.string.log_shizuku_not_authorized)
			return
		}
		if (_busy.value) {
			log(app, R.string.log_busy)
			return
		}
		scope.launch {
			_busy.value = true
			try {
				log(app, R.string.log_progress, app.getString(labelRes))
				block(OemRepository.getInstance(app).config.value)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				log(app, R.string.log_error, e.message)
			} finally {
				_busy.value = false
			}
		}
	}

	private fun log(
		app: Application,
		@StringRes id: Int,
		vararg args: Any?
	) {
		LogStore.append(app, app.getString(id, *args))
	}
}
