package dev.yoanndev90.statusbarhider.features.status

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.backup.SettingsBackup
import dev.yoanndev90.statusbarhider.core.backup.SettingsBackupException
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.features.shared.PrefsViewModel
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device identity + Shizuku authorization, everything the Status tab shows above its cards. */
data class StatusUiState(
	val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
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
				oemId = oem.id,
				oemName = oem.name,
				oemUntested = oem.untested
			)
		}
			// WhileSubscribed: no eternal combine while the tab is off screen.
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUiState())

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

	/** Writes the export file on IO and logs; null means the share intent could not be built. */
	suspend fun exportSettings(): Intent? =
		withContext(Dispatchers.IO) {
			val intent = SettingsBackup.shareIntent(getApplication())
			if (intent != null) log(R.string.log_settings_exported)
			intent
		}

	/**
	 * Reads a picked backup and applies it as one uninterruptible block, so a
	 * cancelled scope cannot leave the settings half-swapped. False (and a
	 * log line) on anything unusable; toasts belong to the screen.
	 */
	suspend fun importSettings(uri: Uri): Boolean =
		withContext(Dispatchers.IO) {
			try {
				val raw =
					getApplication<Application>()
						.contentResolver
						.openInputStream(uri)
						?.bufferedReader()
						?.use { it.readText() }
						?: throw SettingsBackupException("Cannot read ${uri.lastPathSegment}")
				SettingsBackup.importSettings(getApplication(), raw)
				log(R.string.log_settings_imported)
				true
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				log(R.string.log_error, e.message)
				false
			}
		}

	/**
	 * Runs [exportSettings] in [viewModelScope]. The export writes a file before
	 * the chooser appears, and a composition-scoped launch would cancel it when
	 * the user leaves the tab - an empty file and no dialog to pick it.
	 */
	fun exportSettings(onDone: (Intent?) -> Unit) {
		viewModelScope.launch { onDone(exportSettings()) }
	}

	/** Same contract as [exportSettings]: the read completes even if the tab goes away. */
	fun importSettings(
		uri: Uri,
		onDone: (Boolean) -> Unit
	) {
		viewModelScope.launch { onDone(importSettings(uri)) }
	}
}
