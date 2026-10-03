package dev.yoanndev90.statusbarhider.hide

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.CommandRunner
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.tiles.CustomBarTile
import dev.yoanndev90.statusbarhider.tiles.HideRestoreTile

/**
 * Runs the OEM hide / restore command sets and remembers whether the system
 * status bar is currently hidden (the platform exposes no query for it, so the
 * last successful run is persisted instead).
 *
 * Shared by the Quick Settings tiles and [dev.yoanndev90.statusbarhider.features.status.StatusViewModel]
 * so the tile state and the in-app buttons can never disagree.
 */
object HideController {
	private const val PREFS_NAME = "statusbarhider"
	private const val KEY_HIDDEN = "status_bar_hidden"

	/** Outcome of one hide / restore run, already formatted for the log view. */
	data class Result(
		val ok: Boolean,
		val lines: List<String>
	)

	/** Last persisted hide state. */
	fun isHidden(context: Context): Boolean = prefs(context).getBoolean(KEY_HIDDEN, false)

	/** Applies every configured hide command; persists the state on full success. */
	suspend fun applyHide(context: Context): Result = run(context, hide = true)

	/** Runs every configured restore command; persists the state on full success. */
	suspend fun restore(context: Context): Result = run(context, hide = false)

	/**
	 * Asks SystemUI to refresh both tiles. A no-op for tiles the user never
	 * added, so it is safe to call from anywhere.
	 */
	fun notifyTiles(context: Context) {
		if (Build.VERSION.SDK_INT < 24) return
		val app = context.applicationContext
		val components =
			listOf(
				ComponentName(app, HideRestoreTile::class.java),
				ComponentName(app, CustomBarTile::class.java)
			)
		for (component in components) {
			try {
				TileService.requestListeningState(app, component)
			} catch (_: Exception) {
			}
		}
	}

	private suspend fun run(
		context: Context,
		hide: Boolean
	): Result {
		val config = OemRepository.getInstance(context).config.value
		val commands = if (hide) config.hide else config.restore
		if (commands.isEmpty()) {
			val resId = if (hide) R.string.log_no_hide_commands else R.string.log_no_restore_commands
			return Result(false, listOf(context.getString(resId, config.name)))
		}
		val lines = mutableListOf<String>()
		var ok = true
		for (cmd in commands) {
			val (exit, out) = CommandRunner.run(context, cmd.cmd)
			if (exit == 0) {
				val output = out.ifEmpty { context.getString(R.string.log_ok) }
				lines += context.getString(R.string.log_command_ok, cmd.name, output)
			} else {
				ok = false
				val output = out.ifEmpty { context.getString(R.string.log_exit_code, exit) }
				lines += context.getString(R.string.log_command_failed, cmd.name, output)
			}
		}
		if (ok) {
			prefs(context).edit().putBoolean(KEY_HIDDEN, hide).apply()
		}
		notifyTiles(context)
		return Result(ok, lines)
	}

	private fun prefs(context: Context): android.content.SharedPreferences =
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
