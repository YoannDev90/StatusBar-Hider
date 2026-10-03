package dev.yoanndev90.statusbarhider.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.ShizukuCmd
import dev.yoanndev90.statusbarhider.data.CommandRunner
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.hide.HideController
import dev.yoanndev90.statusbarhider.log.LogStore
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
	val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
	val shizukuText: String = "",
	val oemId: String = "",
	val oemName: String = "",
	val oemUntested: Boolean = false,
	val prefs: OverlayPrefs = OverlayPrefs(),
	val logs: List<String> = emptyList(),
	val busy: Boolean = false
)

/**
 * Holds all MainActivity logic. The Activity (XML today, Compose in P3)
 * only renders [uiState] and forwards user events.
 */
class MainViewModel(
	application: Application
) : AndroidViewModel(application) {
	private val shizukuRepo = ShizukuRepository.getInstance()
	private val oemRepo = OemRepository.getInstance(application)
	private val prefsRepo = OverlayPrefsRepository.getInstance(application)

	private val busyFlow = MutableStateFlow(false)

	val uiState: StateFlow<MainUiState> =
		combine(
			shizukuRepo.state,
			oemRepo.config,
			prefsRepo.state,
			LogStore.lines,
			busyFlow
		) { shizuku, oem, prefs, logs, busy ->
			MainUiState(
				shizuku = shizuku,
				shizukuText =
					when (shizuku) {
						ShizukuState.NOT_RUNNING -> str(R.string.shizuku_not_running)
						ShizukuState.NOT_GRANTED -> str(R.string.shizuku_not_granted)
						ShizukuState.READY -> str(R.string.shizuku_ready)
					},
				oemId = oem.id,
				oemName = oem.name,
				oemUntested = oem.untested,
				prefs = prefs,
				logs = logs,
				busy = busy
			)
		}.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

	init {
		shizukuRepo.start()
		// Persisted lines (from a previous run, or a boot auto-hide) are shown too.
		LogStore.ensureLoaded(getApplication())
	}

	override fun onCleared() {
		shizukuRepo.stop()
		super.onCleared()
	}

	/** Resolves a string resource, optionally formatting it with [args]. */
	private fun str(
		id: Int,
		vararg args: Any?
	): String = getApplication<Application>().getString(id, *args)

	fun refreshShizuku() {
		shizukuRepo.refresh()
	}

	fun requestShizukuPermission(requestCode: Int) {
		if (shizukuRepo.state.value == ShizukuState.NOT_RUNNING) {
			appendLog(str(R.string.log_shizuku_not_running))
			return
		}
		if (ShizukuCmd.granted()) {
			appendLog(str(R.string.log_already_authorized))
		} else {
			shizukuRepo.requestPermission(requestCode)
		}
	}

	fun applyHide() {
		runCommand(R.string.log_applying_hide) { oem ->
			if (oem.untested) {
				appendLog(str(R.string.log_untested_config, oem.name))
			}
			for (note in oem.notes) {
				appendLog(str(R.string.log_note, note))
			}
			val result = HideController.applyHide(getApplication())
			result.lines.forEach { appendLog(it) }
			if (result.ok) {
				appendLog(str(R.string.log_done))
			}
		}
	}

	fun checkState() {
		runCommand(R.string.log_reading_state) { oem ->
			for (cmd in oem.status) {
				val (_, out) = CommandRunner.run(getApplication<Application>(), cmd.cmd)
				appendLog(str(R.string.log_status_value, cmd.name, out.ifEmpty { str(R.string.log_status_empty) }))
			}
		}
	}

	fun restore() {
		runCommand(R.string.log_restoring) { _ ->
			val result = HideController.restore(getApplication())
			result.lines.forEach { appendLog(it) }
			// Restore the system bar and the custom bar are alternatives: only
			// touch the overlay state when it is actually on.
			if (prefsRepo.state.value.enabled) {
				prefsRepo.update { copy(enabled = false) }
				StatusBarOverlayService.stop(getApplication())
			}
		}
	}

	/** Switches to another shipped OEM config. */
	fun selectOem(id: String) {
		val config = oemRepo.select(id)
		appendLog(str(R.string.log_oem_selected, config.name))
	}

	/** Forgets the saved pick and re-runs device detection. */
	fun redetectOem() {
		val config = oemRepo.redetect()
		appendLog(str(R.string.log_oem_redetected, config.name))
	}

	fun updatePrefs(transform: OverlayPrefs.() -> OverlayPrefs) {
		val updated = prefsRepo.update(transform)
		if (updated.enabled) StatusBarOverlayService.start(getApplication())
	}

	fun setOverlayEnabled(enabled: Boolean) {
		prefsRepo.update { copy(enabled = enabled) }
		if (enabled) {
			StatusBarOverlayService.start(getApplication())
		} else {
			StatusBarOverlayService.stop(getApplication())
		}
		appendLog(str(if (enabled) R.string.log_bar_shown else R.string.log_bar_hidden))
	}

	fun appendLog(line: String) {
		LogStore.append(getApplication(), line)
	}

	/**
	 * @param labelRes resource id of the progress line shown before [block] runs.
	 *
	 * Commands are blocking shell calls, so a second request while one is in
	 * flight is refused instead of silently interleaved (and [MainUiState.busy]
	 * can then never be cleared by the wrong job).
	 */
	private fun runCommand(
		labelRes: Int,
		block: suspend (oem: dev.yoanndev90.statusbarhider.OemConfig) -> Unit
	) {
		if (!ShizukuCmd.granted()) {
			appendLog(str(R.string.log_shizuku_not_authorized))
			return
		}
		if (busyFlow.value) {
			appendLog(str(R.string.log_busy))
			return
		}
		viewModelScope.launch(Dispatchers.IO) {
			busyFlow.value = true
			try {
				appendLog(str(R.string.log_progress, str(labelRes)))
				block(oemRepo.config.value)
			} catch (e: Exception) {
				if (e is kotlinx.coroutines.CancellationException) throw e
				appendLog(str(R.string.log_error, e.message))
			} finally {
				busyFlow.value = false
			}
		}
	}
}
