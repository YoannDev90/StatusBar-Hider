package dev.yoanndev90.statusbarhider.features.shared

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.OverlayController
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.overlay.LockScreenOverlayService
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Base for every screen ViewModel that reads or writes [OverlayPrefs].
 *
 * Exposes the live prefs plus the two operations that must stay in lockstep
 * with the overlay service ([updatePrefs] and [setOverlayEnabled]), the
 * Shizuku grant shared by the Status and Setup tabs, and the logging helpers
 * every screen needs; screens add their own commands on top.
 */
open class PrefsViewModel(
	application: Application
) : AndroidViewModel(application) {
	protected val prefsRepo = OverlayPrefsRepository.getInstance(application)
	private val shizukuRepo = ShizukuRepository.getInstance()

	/** Live overlay prefs; every screen observes this instead of copying state. */
	val prefs: StateFlow<OverlayPrefs> = prefsRepo.state

	/** Live Shizuku authorization; can change under an open screen (permission dialog). */
	val shizuku: StateFlow<ShizukuState> = shizukuRepo.state

	/** One-shot UI instructions; a tryEmit never suspends, drops only when the screen ignores them. */
	private val _events = MutableSharedFlow<PrefsEvent>(extraBufferCapacity = 8)
	val events: SharedFlow<PrefsEvent> = _events.asSharedFlow()

	/** Asks for the Shizuku grant; logs instead of prompting when that is pointless. */
	fun requestShizukuPermission() {
		when (shizukuRepo.state.value) {
			ShizukuState.NOT_RUNNING -> log(R.string.log_shizuku_not_running)
			ShizukuState.READY -> log(R.string.log_already_authorized)
			ShizukuState.NOT_GRANTED ->
				if (!shizukuRepo.requestPermission()) log(R.string.log_shizuku_not_running)
		}
	}

	/** Applies [transform]; only the off -> on edge restarts the overlay service. */
	fun updatePrefs(transform: OverlayPrefs.() -> OverlayPrefs) {
		OverlayController.update(getApplication(), transform)
	}

	/** Switches the custom bar on or off and logs the transition. */
	fun setOverlayEnabled(enabled: Boolean) {
		OverlayController.setEnabled(getApplication(), enabled)
		log(if (enabled) R.string.log_bar_shown else R.string.log_bar_hidden)
	}

	/**
	 * Enables [LockScreenOverlayService] by merging its component into the
	 * secure `enabled_accessibility_services` list (never overwriting what is
	 * already there) and turning `accessibility_enabled` on.
	 */
	fun enableLockScreenOverlay() {
		val app = getApplication<Application>()
		val component = ComponentName(app, LockScreenOverlayService::class.java).flattenToString()
		ShellRunner.run(app, R.string.log_enabling_lock_a11y) {
			val (_, current) = ShellRunner.run(app, "settings get secure enabled_accessibility_services")
			val existing = current.trim().let { if (it == "null" || it.isEmpty()) "" else it }
			val merged =
				if (existing.split(':').none { it == component }) {
					if (existing.isEmpty()) component else "$existing:$component"
				} else {
					existing
				}
			val (exit, out) =
				ShellRunner.run(app, "settings put secure enabled_accessibility_services '$merged'")
			val (exit2, out2) = ShellRunner.run(app, "settings put secure accessibility_enabled 1")
			if (exit == 0 && exit2 == 0) {
				log(R.string.log_lock_a11y_ok)
			} else {
				log(R.string.log_lock_a11y_failed, (out + out2).trim().ifEmpty { "exit $exit/$exit2" })
			}
		}
	}

	/** Persists the date pattern and logs it so the user can confirm it took. */
	fun setDateFormat(pattern: String) {
		updatePrefs { copy(dateFormat = pattern) }
		log(R.string.log_date_format, pattern)
	}

	/** Saves the calendar widget state; call only once the permission is granted. */
	fun setShowCalendar(enabled: Boolean) {
		updatePrefs { copy(showCalendar = enabled) }
	}

	/** Moves the widget at [fromIndex] to [toIndex] in the persisted bar order. */
	fun moveWidget(
		fromIndex: Int,
		toIndex: Int
	) {
		updatePrefs {
			copy(widgetOrder = widgetOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
		}
	}

	/**
	 * Shows the custom bar: the window cannot be attached without its
	 * permission, so ask for it first instead of failing silently. Shared by
	 * the Status, Style and widget entry points. The grant UI is a
	 * [PrefsEvent]; toasts and settings pages need a Context the VM has not.
	 */
	fun showOverlayBar() {
		val app = getApplication<Application>()
		if (!Settings.canDrawOverlays(app)) {
			_events.tryEmit(PrefsEvent.GrantOverlayPermission)
			return
		}
		setOverlayEnabled(true)
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
