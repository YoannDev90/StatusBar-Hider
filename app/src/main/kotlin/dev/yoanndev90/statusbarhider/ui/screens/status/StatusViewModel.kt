package dev.yoanndev90.statusbarhider.ui.screens.status

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.viewModelScope
import dev.yoanndev90.statusbarhider.core.backup.SettingsBackup
import dev.yoanndev90.statusbarhider.core.backup.SettingsBackupException
import dev.yoanndev90.statusbarhider.core.backup.SettingsProvider
import dev.yoanndev90.statusbarhider.core.backup.SisterBuild
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.hide.HideInteractor
import dev.yoanndev90.statusbarhider.overlay.OverlayController
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import dev.yoanndev90.statusbarhider.ui.state.PrefsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

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

	private val _sisterLabel = MutableStateFlow<String?>(null)

	/** Installed name of the other build (release <-> debug), or null when only this build is installed. */
	val sisterLabel: StateFlow<String?> = _sisterLabel.asStateFlow()

	private val _shizukuVersion = MutableStateFlow<String?>(null)

	/** Installed Shizuku version ("13.5.4 (62)"), or null when Shizuku is not installed. */
	val shizukuVersion: StateFlow<String?> = _shizukuVersion.asStateFlow()

	init {
		// Package visibility costs a binder round-trip, so detection stays off
		// the main thread; the row simply appears once it answers.
		viewModelScope.launch {
			_sisterLabel.value = withContext(Dispatchers.IO) { SisterBuild.installedLabel(application) }
			_shizukuVersion.value = withContext(Dispatchers.IO) { shizukuVersionLabel(application) }
		}
	}

	/** Computed once per session, like the sister label; the PM caches the answer. */
	private fun shizukuVersionLabel(context: Context): String? =
		try {
			val info = context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
			"${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
		} catch (ignore: NameNotFoundException) {
			null
		}

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

	/**
	 * System status bar: whether the hide is in effect on the device, read
	 * back from `dumpsys` rather than from the flag the last run wrote. Seeded
	 * from the observed state (the [HideInteractor] cache, falling back to that
	 * flag) so the card is not blank until the first read lands.
	 */
	private val _systemBarHidden = MutableStateFlow(HideInteractor.observedHidden(application))

	val systemBarHidden: StateFlow<Boolean> = _systemBarHidden.asStateFlow()

	/** Custom bar: the overlay window's real attachment, straight from the service. */
	val customBarVisible: StateFlow<Boolean> = StatusBarOverlayService.visible

	/**
	 * Re-reads the hide state from the shell. Home calls it on every resume
	 * and on the falling edge of [ShellRunner.busy] - the moment a command
	 * run has finished writing the state it claims.
	 */
	fun refreshSystemBarState() {
		viewModelScope.launch { _systemBarHidden.value = HideInteractor.readHidden(getApplication()) }
	}

	fun applyHide() {
		ShellRunner.run(getApplication(), "applying hide...") { oem ->
			if (oem.untested) {
				appendLog("Warning: '${oem.name}' config is untested here - adjust commands if nothing happens.")
			}
			for (note in oem.notes) {
				appendLog("note: $note")
			}
			val result = HideInteractor.applyAndLog(getApplication(), hide = true)
			if (result.ok) {
				appendLog("Done. Swipe-down is preserved.")
			}
		}
	}

	fun checkState() {
		ShellRunner.run(getApplication(), "reading state...") { oem ->
			for (cmd in oem.status) {
				val (_, out) = ShellRunner.run(cmd.cmd)
				appendLog("${cmd.name} = ${out.ifEmpty { "(empty)" }}")
			}
		}
	}

	fun restore() {
		ShellRunner.run(getApplication(), "restoring...") {
			HideInteractor.applyAndLog(getApplication(), hide = false)
			// Restoring the system bar and the custom bar are alternatives: only
			// touch the overlay state when it is actually on.
			if (prefsRepo.state.value.enabled) {
				setOverlayEnabled(false)
			}
		}
	}

	/**
	 * Switches to another shipped OEM config. Selecting re-reads and re-parses
	 * the asset, so it moves to IO; the dropdown is already closed by then.
	 */
	fun selectOem(id: String) {
		viewModelScope.launch {
			val config = withContext(Dispatchers.IO) { oemRepo.select(id) }
			appendLog("OEM config: ${config.name}")
		}
	}

	/** Forgets the saved pick and re-runs device detection, off the main thread. */
	fun redetectOem() {
		viewModelScope.launch {
			val config = withContext(Dispatchers.IO) { oemRepo.redetect() }
			appendLog("OEM config re-detected: ${config.name}")
		}
	}

	/** Writes the export file on IO and logs; null means the share intent could not be built. */
	suspend fun exportSettings(): Intent? =
		withContext(Dispatchers.IO) {
			val intent = SettingsBackup.shareIntent(getApplication())
			if (intent != null) log("Settings exported to a JSON file")
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
						?.let { stream -> SettingsBackup.readCapped(stream) }
						?: throw SettingsBackupException("Cannot read ${uri.lastPathSegment}")
				OverlayController.replaceAll(getApplication(), SettingsBackup.importSettings(getApplication(), raw))
				log("Settings imported")
				true
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				log("ERROR: ${e.message}")
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

	/**
	 * Pulls the backup blob from the sister build's provider and applies it
	 * through the same path as a file import. False (and a log line) when the
	 * sibling is unreachable or its blob is unusable; toasts belong to the
	 * screen.
	 */
	suspend fun importFromSister(): Boolean =
		withContext(Dispatchers.IO) {
			try {
				val raw = SettingsProvider.fetchSisterBlob(getApplication())
				OverlayController.replaceAll(getApplication(), SettingsBackup.importSettings(getApplication(), raw))
				log("Settings imported")
				true
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				log("ERROR: ${e.message}")
				false
			}
		}

	/** Same contract as [importSettings]: the cross-build pull completes even if the tab goes away. */
	fun importFromSister(onDone: (Boolean) -> Unit) {
		viewModelScope.launch { onDone(importFromSister()) }
	}
}
