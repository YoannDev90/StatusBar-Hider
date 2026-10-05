package dev.yoanndev90.statusbarhider.features.status

import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Device identity + Shizuku authorization, everything the Status tab shows above its cards. */
data class StatusUiState(
	val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
	val shizukuText: String = "",
	val oemId: String = "",
	val oemName: String = "",
	val oemUntested: Boolean = false
)

/**
 * Status tab: OEM detection, Shizuku authorization and the hide / restore
 * command set. Commands run through [ShellRunner], whose [ShellRunner.busy]
 * the shell turns into a progress bar.
 */
class StatusViewModel(
	application: Application
) : PrefsViewModel(application) {
	private val shizukuRepo = ShizukuRepository.getInstance()
	private val oemRepo = OemRepository.getInstance(application)

	val uiState: StateFlow<StatusUiState> =
		combine(shizukuRepo.state, oemRepo.config) { shizuku, oem ->
			StatusUiState(
				shizuku = shizuku,
				shizukuText =
					when (shizuku) {
						ShizukuState.NOT_RUNNING -> str(R.string.shizuku_not_running)
						ShizukuState.NOT_GRANTED -> str(R.string.shizuku_not_granted)
						ShizukuState.READY -> str(R.string.shizuku_ready)
					},
				oemId = oem.id,
				oemName = oem.name,
				oemUntested = oem.untested
			)
		}.stateIn(viewModelScope, SharingStarted.Eagerly, StatusUiState())

	fun requestShizukuPermission() {
		if (shizukuRepo.state.value == ShizukuState.NOT_RUNNING) {
			appendLog(str(R.string.log_shizuku_not_running))
			return
		}
		if (shizukuRepo.state.value == ShizukuState.READY) {
			appendLog(str(R.string.log_already_authorized))
		} else {
			shizukuRepo.requestPermission()
		}
	}

	fun applyHide() {
		ShellRunner.run(getApplication(), R.string.log_applying_hide) { oem ->
			if (oem.untested) {
				appendLog(str(R.string.log_untested_config, oem.name))
			}
			for (note in oem.notes) {
				appendLog(str(R.string.log_note, note))
			}
			val result = HideInteractor.applyAndLog(getApplication(), hide = true)
			if (result.ok) {
				appendLog(str(R.string.log_done))
			}
		}
	}

	fun checkState() {
		ShellRunner.run(getApplication(), R.string.log_reading_state) { oem ->
			for (cmd in oem.status) {
				val (_, out) = ShellRunner.run(getApplication(), cmd.cmd)
				appendLog(str(R.string.log_status_value, cmd.name, out.ifEmpty { str(R.string.log_status_empty) }))
			}
		}
	}

	fun restore() {
		ShellRunner.run(getApplication(), R.string.log_restoring) {
			HideInteractor.applyAndLog(getApplication(), hide = false)
			// Restoring the system bar and the custom bar are alternatives: only
			// touch the overlay state when it is actually on.
			if (prefsRepo.state.value.enabled) {
				setOverlayEnabled(false)
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
}
