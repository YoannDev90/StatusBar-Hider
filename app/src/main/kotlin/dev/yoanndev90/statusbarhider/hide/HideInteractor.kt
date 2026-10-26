package dev.yoanndev90.statusbarhider.hide

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import android.util.Log
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
 * restore command set, remember the intended state (nothing asks the system
 * whether this app's hide is the one still in force, so the last successful
 * run is persisted), and read the disable flags back from `dumpsys` - both
 * the aggregate `mDisabled1` and the shared record our own bits live in,
 * parsed by [DisableFlags].
 *
 * [applyAndLog] and [hideLogged] exist because "run the sequence, then append
 * every line to the app log" was copied at every entry point (Status screen,
 * control API, SystemUI watcher, tiles, boot); they are that copy, once.
 *
 * Every command goes through [ShellRunner], so a tile tap, the unlock
 * re-apply and a Status screen action can never drive the shell at the same
 * time: [applyHide] / [restore] are the raw sequence, and every entry point
 * that runs them outside [hideLogged] does it through
 * [ShellRunner.runSequence].
 */
object HideInteractor {
	private const val TAG = "HideInteractor"
	private const val PREFS_NAME = "statusbarhider"
	private const val KEY_HIDDEN = "status_bar_hidden"

	/** Bits the last successful hide read back; -1 until one has run. */
	private const val KEY_MASK = "status_bar_disable_mask"

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
	 * True while the flags this app applied are still held - the exact bits,
	 * not "some record". `mDisabled1` is a shared slot: AOSP's
	 * `cmd statusbar send-disable-flag` replaces it for every caller from a
	 * single static token, so a third-party app writing its own flags can
	 * drop ours (Essentials was seen doing it ~0.5s after every unlock, see
	 * docs/limitations.md) and leave a non-zero remainder that the old
	 * `!= 0` read called "applied" while the bar was visibly back on screen.
	 *
	 * The mask compared here is what [rememberMask] read back right after our
	 * own apply; with none recorded yet the old any-record rule stands. An
	 * unreadable `dumpsys` still counts as "not applied", which is what every
	 * caller but [readHidden] wants to know.
	 */
	suspend fun isApplied(context: Context): Boolean = readApplied(context) ?: false

	/**
	 * The hide state as the device reports it, for a screen that displays it:
	 * [isApplied]'s answer whenever `dumpsys` can be read, the persisted flag
	 * only when it cannot (Shizuku off, no `mDisabled1` line).
	 *
	 * [isHidden] alone is the last thing written, never the current state -
	 * the shared record can lose our bits right after every unlock, so a
	 * screen that only re-reads that flag keeps calling a bar that is back on
	 * screen "Hidden".
	 */
	suspend fun readHidden(context: Context): Boolean = readApplied(context) ?: isHidden(context)

	/** [isApplied]'s reading, or null when `dumpsys` carried no usable answer. */
	private suspend fun readApplied(context: Context): Boolean? {
		val (exit, out) = ShellRunner.run(context, "dumpsys statusbar")
		if (exit != 0) return null
		return holdsMask(parseDisableFlags(out), expectedMask(context))
	}

	/** The bits our last successful hide wrote, or null when none is recorded. */
	private fun expectedMask(context: Context): Long? =
		prefs(context).getLong(KEY_MASK, -1L).takeIf { it > 0L }

	/**
	 * Reads our flags back right after a successful apply and remembers them:
	 * the one value later reads can be compared against. Taken from the
	 * shared record rather than `mDisabled1`, so SystemUI's own lock flags -
	 * still set while the keyguard goes away - never enter the mask.
	 */
	private suspend fun rememberMask(context: Context) {
		val (exit, out) = ShellRunner.run(context, "dumpsys statusbar")
		if (exit != 0) return
		val shared = parseDisableFlags(out)?.shared ?: return
		if (shared <= 0L) return
		prefs(context).edit().putLong(KEY_MASK, shared).apply()
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
	 * Same sequence, but gated and awaited: `null` means the shell refused it
	 * (busy or Shizuku missing - [ShellRunner] has already logged why), so the
	 * caller must not report the old state as if the run had happened. Used by
	 * callers that show an outcome: the tile and the boot pass.
	 */
	suspend fun applyAndLogGated(
		context: Context,
		hide: Boolean,
		@StringRes labelRes: Int?
	): Result? {
		var outcome: Result? = null
		ShellRunner.runSequence(context, labelRes) { outcome = applyAndLog(context, hide) }
		return outcome
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
			} catch (e: Exception) {
				// Swallowing this left both tiles stale with no trace anywhere.
				Log.w(TAG, "requestListeningState failed", e)
			}
		}
	}

	private suspend fun run(
		context: Context,
		hide: Boolean
	): Result {
		val config = OemRepository.getInstance(context).awaitLoaded()
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
			// Read the mask back now, while the flags we just wrote are the
			// ones the shared record holds; a restore keeps the previous one,
			// whose absence is what makes the post-restore read say "off".
			if (hide) rememberMask(context)
		}
		notifyTiles(context)
		BarWidget.updateAll(context)
		return Result(ok, lines)
	}

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
