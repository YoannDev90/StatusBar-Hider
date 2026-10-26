package dev.yoanndev90.statusbarhider.ui.state

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.oem.OemConfig
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.SettingsRegistry
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.data.OverlayPrefsStore
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.overlay.LockScreenOverlayService
import dev.yoanndev90.statusbarhider.overlay.OverlayController
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

	/** Registry view of the overlay file: specs, defaults, modified settings. */
	val overlayStore = OverlayPrefsStore(prefsRepo)

	/** Live Shizuku authorization; can change under an open screen (permission dialog). */
	val shizuku: StateFlow<ShizukuState> = shizukuRepo.state

	/** Live OEM config; the setup checklist reads its keep-alive screens from it. */
	val oem: StateFlow<OemConfig> = OemRepository.getInstance(application).config

	/** One-shot UI instructions; a tryEmit never suspends, drops only when the screen ignores them. */
	private val _events = MutableSharedFlow<PrefsEvent>(extraBufferCapacity = 8)
	val events: SharedFlow<PrefsEvent> = _events.asSharedFlow()

	/** Asks for the Shizuku grant; logs instead of prompting when that is pointless. */
	fun requestShizukuPermission() {
		when (shizukuRepo.state.value) {
			ShizukuState.NOT_RUNNING -> log("Shizuku is not running. Start it, then try again.")
			ShizukuState.READY -> log("Already authorized.")
			ShizukuState.NOT_GRANTED ->
				if (!shizukuRepo.requestPermission()) log("Shizuku is not running. Start it, then try again.")
		}
	}

	/** Applies [transform]; only the off -> on edge restarts the overlay service. */
	fun updatePrefs(transform: OverlayPrefs.() -> OverlayPrefs) {
		OverlayController.update(getApplication(), transform)
	}

	/** Switches the custom bar on or off and logs the transition. */
	fun setOverlayEnabled(enabled: Boolean) {
		OverlayController.setEnabled(getApplication(), enabled)
		log(if (enabled) "Custom bar shown (overlay)" else "Custom bar hidden")
	}

	/**
	 * Enables [LockScreenOverlayService] by merging its component into the
	 * secure `enabled_accessibility_services` list (never overwriting what is
	 * already there) and turning `accessibility_enabled` on.
	 */
	fun enableLockScreenOverlay() {
		val app = getApplication<Application>()
		ShellRunner.run(app, "enabling lock-screen accessibility service...") {
			val error = writeLockOverlay(lockOverlayComponent(app), enable = true)
			if (error == null) {
				log("Lock-screen overlay service enabled. Lock the screen to see it.")
			} else {
				log("Could not enable lock-screen overlay service: $error")
			}
		}
	}

	/**
	 * Removes [LockScreenOverlayService] again: the same merge in reverse, so
	 * every other service the user turned on stays on, and
	 * `accessibility_enabled` only drops when nothing is left.
	 */
	fun disableLockScreenOverlay() {
		val app = getApplication<Application>()
		ShellRunner.run(app, "disabling lock-screen accessibility service...") {
			val error = writeLockOverlay(lockOverlayComponent(app), enable = false)
			if (error == null) {
				log("Lock-screen overlay service disabled.")
			} else {
				log("Could not disable lock-screen overlay service: $error")
			}
		}
	}

	private fun lockOverlayComponent(app: Application): String =
		ComponentName(app, LockScreenOverlayService::class.java).flattenToString()

	/**
	 * Adds or drops [component] in the secure enabled-accessibility list and
	 * keeps `accessibility_enabled` in step with what is left. Returns the
	 * shell failure text, or null when the list was read and both writes took.
	 */
	private suspend fun writeLockOverlay(
		component: String,
		enable: Boolean
	): String? {
		val (readExit, current) = ShellRunner.run("settings get secure enabled_accessibility_services")
		// A failed read answers with error text, not the service list (the
		// transport merges stderr into out): parsing that as a list and writing
		// it back would wipe every service the user actually enabled.
		if (readExit != 0) return current.trim().ifEmpty { "exit $readExit" }
		val existing = current.trim().let { if (it == "null" || it.isEmpty()) "" else it }
		val others = existing.split(':').filter { it.isNotEmpty() && it != component }
		val merged = if (enable) (others + component).joinToString(":") else others.joinToString(":")
		val (exit, out) =
			ShellRunner.run("settings put secure enabled_accessibility_services '$merged'")
		val enabledFlag = if (enable || others.isNotEmpty()) 1 else 0
		val (exit2, out2) = ShellRunner.run("settings put secure accessibility_enabled $enabledFlag")
		return if (exit == 0 && exit2 == 0) {
			null
		} else {
			(out + out2).trim().ifEmpty { "exit $exit/$exit2" }
		}
	}

	/** Persists the date pattern and logs it so the user can confirm it took. */
	fun setDateFormat(pattern: String) {
		updatePrefs { copy(dateFormat = pattern) }
		log("Date format: $pattern")
	}

	/**
	 * Restores every setting to its default value through the registry, then
	 * re-applies the file: [OverlayController.replaceAll] is the one path that
	 * starts or stops the service and refreshes the tiles and widgets.
	 */
	fun resetToDefaults() {
		val app = getApplication<Application>()
		SettingsRegistry.resetStore(overlayStore)
		OverlayController.replaceAll(app, overlayStore.default)
		log("Settings reset to defaults")
	}

	/** Puts a section's settings back to their defaults; the rest of the file is kept. */
	fun resetSection(section: SettingSection<OverlayPrefs>) {
		updatePrefs { section.resetIn(this) }
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

	/** Appends [line] (a hardcoded English log message) to the app log. */
	protected fun log(line: String) {
		appendLog(line)
	}
}
