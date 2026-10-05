package dev.yoanndev90.statusbarhider.control

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.CommandExecutor
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.OverlayController
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideController

/**
 * Single entry point behind the external control API (Tasker, `am broadcast`,
 * automation apps) and the home screen widget.
 *
 * Actions are explicit-broadcast only (no intent filter, so only
 * component-targeted broadcasts reach the receiver), and every dispatch is
 * written to the app log so an automation is traceable.
 */
object ControlActions {
	private const val TAG = "ControlActions"

	const val ACTION_SHOW_BAR = "dev.yoanndev90.statusbarhider.SHOW_BAR"
	const val ACTION_HIDE_BAR = "dev.yoanndev90.statusbarhider.HIDE_BAR"
	const val ACTION_TOGGLE_BAR = "dev.yoanndev90.statusbarhider.TOGGLE_BAR"
	const val ACTION_HIDE_SYSTEM_BAR = "dev.yoanndev90.statusbarhider.HIDE_SYSTEM_BAR"
	const val ACTION_RESTORE_SYSTEM_BAR = "dev.yoanndev90.statusbarhider.RESTORE_SYSTEM_BAR"
	const val ACTION_TOGGLE_SYSTEM_BAR = "dev.yoanndev90.statusbarhider.TOGGLE_SYSTEM_BAR"

	private val ALL_ACTIONS = setOf(
		ACTION_SHOW_BAR,
		ACTION_HIDE_BAR,
		ACTION_TOGGLE_BAR,
		ACTION_HIDE_SYSTEM_BAR,
		ACTION_RESTORE_SYSTEM_BAR,
		ACTION_TOGGLE_SYSTEM_BAR
	)

	/** True for one of the six control actions, so a receiver can tell them apart from system intents. */
	fun isControlAction(action: String?): Boolean = action != null && action in ALL_ACTIONS

	/**
	 * Executes [action]; returns false when it is not one of the control
	 * actions, so callers can fall through to their own handling.
	 */
	fun dispatch(
		context: Context,
		action: String?
	): Boolean {
		val app = context.applicationContext
		when (action) {
			ACTION_SHOW_BAR -> setBar(app, true)
			ACTION_HIDE_BAR -> setBar(app, false)
			ACTION_TOGGLE_BAR -> setBar(
				app,
				!OverlayPrefsRepository
					.getInstance(app)
					.state.value.enabled
			)
			ACTION_HIDE_SYSTEM_BAR -> systemBar(app, hide = true)
			ACTION_RESTORE_SYSTEM_BAR -> systemBar(app, hide = false)
			ACTION_TOGGLE_SYSTEM_BAR -> systemBar(app, hide = !HideController.isHidden(app))
			else -> return false
		}
		return true
	}

	/** Shows or hides the custom bar; refuses to enable it without the overlay permission. */
	private fun setBar(
		context: Context,
		enabled: Boolean
	) {
		val app = context.applicationContext
		if (enabled && !Settings.canDrawOverlays(app)) {
			// Enabling would silently do nothing: the window cannot be attached.
			Log.w(TAG, "Overlay permission missing")
			LogStore.append(app, app.getString(R.string.log_overlay_permission_missing))
			return
		}
		OverlayController.setEnabled(app, enabled)
		// OverlayController stays quiet on purpose (tiles and boot do not log).
		LogStore.append(app, app.getString(if (enabled) R.string.log_bar_shown else R.string.log_bar_hidden))
	}

	/** Runs the OEM hide / restore set; the shell work happens in [CommandExecutor]. */
	private fun systemBar(
		context: Context,
		hide: Boolean
	) {
		val app = context.applicationContext as? Application ?: return
		CommandExecutor.run(app, if (hide) R.string.log_applying_hide else R.string.log_restoring) {
			val result = if (hide) HideController.applyHide(app) else HideController.restore(app)
			result.lines.forEach { LogStore.append(app, it) }
		}
	}
}
