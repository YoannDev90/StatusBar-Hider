package dev.yoanndev90.statusbarhider.features.shared

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.OverlayController
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.coroutines.flow.StateFlow

/**
 * Base for every screen ViewModel that reads or writes [OverlayPrefs].
 *
 * Exposes the live prefs plus the two operations that must stay in lockstep
 * with the overlay service ([updatePrefs] and [setOverlayEnabled]) and the
 * logging helpers every screen needs; screens add their own commands on top.
 */
abstract class PrefsViewModel(
	application: Application
) : AndroidViewModel(application) {
	protected val prefsRepo = OverlayPrefsRepository.getInstance(application)

	/** Live overlay prefs; every screen observes this instead of copying state. */
	val prefs: StateFlow<OverlayPrefs> = prefsRepo.state

	/** Applies [transform]; only the off -> on edge restarts the overlay service. */
	fun updatePrefs(transform: OverlayPrefs.() -> OverlayPrefs) {
		OverlayController.update(getApplication(), transform)
	}

	/** Switches the custom bar on or off and logs the transition. */
	fun setOverlayEnabled(enabled: Boolean) {
		OverlayController.setEnabled(getApplication(), enabled)
		log(if (enabled) R.string.log_bar_shown else R.string.log_bar_hidden)
	}

	/** Appends a raw line to the app log. */
	protected fun appendLog(line: String) {
		LogStore.append(getApplication(), line)
	}

	/** Appends the formatted string behind [id] to the app log. */
	protected fun log(
		@StringRes id: Int,
		vararg args: Any?
	) {
		appendLog(str(id, *args))
	}

	/** Resolves a string resource, optionally formatted with [args]. */
	protected fun str(
		@StringRes id: Int,
		vararg args: Any?
	): String = getApplication<Application>().getString(id, *args)
}
