package dev.yoanndev90.statusbarhider.hide

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.tiles.CustomBarTile
import dev.yoanndev90.statusbarhider.tiles.HideRestoreTile
import dev.yoanndev90.statusbarhider.widget.BarWidgetProvider as BarWidget

/**
 * Everything the app does to the system status bar: run the OEM hide /
 * restore command set, remember the state (the platform exposes no query for
 * it, so the last successful run is persisted), and read the disable flags
 * back from `dumpsys`.
 *
 * [applyAndLog] and [hideLogged] exist because "run the sequence, then append
 * every line to the app log" was copied at every entry point (Status screen,
 * control API, SystemUI watcher, tiles, boot); they are that copy, once.
 *
 * Every command goes through [ShellRunner], so a tile tap, the unlock
 * re-apply and a Status screen action can never drive the shell at the same
 * time.
 */
object HideInteractor {
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
	 * True while `dumpsys statusbar` reports a non-zero `mDisabled1`, i.e.
	 * any disable record is still active -- ours and/or SystemUI's lock flags.
	 * False only when everything has been cleared: the platform wipes every
	 * disable record ~0.5-2s after keyguard dismissal, so the unlock re-apply
	 * watches for that total clear (see
	 * [dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService]) instead
	 * of trusting a guessed sleep.
	 */
	suspend fun isApplied(context: Context): Boolean {
		val (exit, out) = ShellRunner.run(context, "dumpsys statusbar")
		if (exit != 0) return false
		val line = out.lineSequence().firstOrNull { it.trimStart().startsWith("mDisabled1=") } ?: return false
		val hex = line.substringAfter("mDisabled1=").trim().removePrefix("0x")
		val disabled1 = hex.toLongOrNull(16) ?: 0L
		return disabled1 != 0L
	}

	/**
	 * Runs the hide ([hide] = true) or restore set and appends every result
	 * line to the app log; returns the result for callers that add their own
	 * line on top ("Done", an unlock-failure header).
	 */
	suspend fun applyAndLog(
		context: Context,
		hide: Boolean
	): Result {
		val result = if (hide) applyHide(context) else restore(context)
		result.lines.forEach { LogStore.append(context, it) }
		return result
	}

	/**
	 * Fire-and-forget [ShellRunner] sequence: apply / restore with its
	 * progress line and log. One line for what used to be a copied
	 * `run { applyHide; forEach log }` block at each entry point.
	 */
	fun hideLogged(
		app: Application,
		hide: Boolean,
		@StringRes labelRes: Int?
	) {
		ShellRunner.run(app, labelRes) { applyAndLog(app, hide) }
	}

	/**
	 * Asks SystemUI to refresh both tiles. A no-op for tiles the user never
	 * added, so it is safe to call from anywhere.
	 */
	fun notifyTiles(context: Context) {
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
			val (exit, out) = ShellRunner.run(context, cmd.cmd)
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
		BarWidget.updateAll(context)
		return Result(ok, lines)
	}

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
