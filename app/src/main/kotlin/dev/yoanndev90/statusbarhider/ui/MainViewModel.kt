package dev.yoanndev90.statusbarhider.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.yoanndev90.statusbarhider.ShizukuCmd
import dev.yoanndev90.statusbarhider.data.CommandRunner
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
	val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
	val shizukuText: String = "",
	val oemName: String = "",
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

	private val logsFlow = MutableStateFlow<List<String>>(emptyList())
	private val busyFlow = MutableStateFlow(false)
	private var cmdJob: Job? = null

	val uiState: StateFlow<MainUiState> =
		combine(
			shizukuRepo.state,
			oemRepo.config,
			prefsRepo.state,
			logsFlow,
			busyFlow
		) { shizuku, oem, prefs, logs, busy ->
			MainUiState(
				shizuku = shizuku,
				shizukuText =
					when (shizuku) {
						ShizukuState.NOT_RUNNING -> "Shizuku: not running"
						ShizukuState.NOT_GRANTED -> "Shizuku: waiting for authorization"
						ShizukuState.READY -> "Shizuku: ready"
					},
				oemName = oem.name,
				prefs = prefs,
				logs = logs,
				busy = busy
			)
		}.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

	init {
		shizukuRepo.start()
	}

	override fun onCleared() {
		shizukuRepo.stop()
		super.onCleared()
	}

	fun refreshShizuku() {
		shizukuRepo.refresh()
	}

	fun requestShizukuPermission(requestCode: Int) {
		if (shizukuRepo.state.value == ShizukuState.NOT_RUNNING) {
			appendLog("Shizuku is not running. Start it, then try again.")
			return
		}
		if (ShizukuCmd.granted()) {
			appendLog("Already authorized.")
		} else {
			shizukuRepo.requestPermission(requestCode)
		}
	}

	fun applyHide() {
		runCommand("applying hide...") { oem ->
			for (cmd in oem.hide) {
				val (_, out) = CommandRunner.run(cmd.cmd)
				appendLog("${cmd.name} -> ${out.ifEmpty { "ok" }}")
			}
			appendLog("Done. Swipe-down is preserved.")
		}
	}

	fun checkState() {
		runCommand("reading state...") { oem ->
			for (cmd in oem.status) {
				val (_, out) = CommandRunner.run(cmd.cmd)
				appendLog("${cmd.name} = ${out.ifEmpty { "(empty)" }}")
			}
		}
	}

	fun restore() {
		runCommand("restoring...") { oem ->
			for (cmd in oem.restore) {
				CommandRunner.run(cmd.cmd)
				appendLog("${cmd.name} -> restored")
			}
			prefsRepo.update { copy(enabled = false) }
			StatusBarOverlayService.stop(getApplication())
		}
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
		appendLog(if (enabled) "Custom bar shown (overlay)" else "Custom bar hidden")
	}

	fun appendLog(line: String) {
		logsFlow.value += line
	}

	private fun runCommand(
		label: String,
		block: suspend (oem: dev.yoanndev90.statusbarhider.OemConfig) -> Unit
	) {
		if (!ShizukuCmd.granted()) {
			appendLog("Shizuku not authorized. Use button 1 first.")
			return
		}
		cmdJob?.cancel()
		cmdJob =
			viewModelScope.launch(Dispatchers.IO) {
				busyFlow.value = true
				try {
					appendLog("... $label")
					block(oemRepo.config.value)
				} catch (e: Exception) {
					if (e is kotlinx.coroutines.CancellationException) throw e
					appendLog("ERROR: ${e.message}")
				} finally {
					busyFlow.value = false
				}
			}
	}
}
