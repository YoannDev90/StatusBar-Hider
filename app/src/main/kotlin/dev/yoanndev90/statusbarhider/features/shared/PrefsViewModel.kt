package dev.yoanndev90.statusbarhider.features.shared

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.OverlayController
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.data.ShizukuRepository
import dev.yoanndev90.statusbarhider.data.ShizukuState
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.coroutines.flow.StateFlow

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

	/** Asks for the Shizuku grant; logs instead of prompting when that is pointless. */
	fun requestShizukuPermission() {
		when (shizukuRepo.state.value) {
			ShizukuState.NOT_RUNNING -> log(R.string.log_shizuku_not_running)
			ShizukuState.READY -> log(R.string.log_already_authorized)
			ShizukuState.NOT_GRANTED -> shizukuRepo.requestPermission()
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
	 * the Status, Style and widget entry points.
	 */
	fun showOverlayBar() {
		val app = getApplication<Application>()
		if (!Settings.canDrawOverlays(app)) {
			Toast.makeText(app, R.string.toast_grant_overlay_permission, Toast.LENGTH_LONG).show()
			val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}"))
			app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
