package dev.yoanndev90.statusbarhider.core.command

import android.content.Context

/**
 * Reads the status bar disable state back from `dumpsys statusbar`.
 *
 * The platform wipes every disable record ~0.5-2s after keyguard dismissal;
 * the unlock re-apply watches for that total clear (see
 * [dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService]) instead of
 * trusting a guessed sleep.
 */
object DisableFlags {
	/**
	 * True while `dumpsys statusbar` reports a non-zero `mDisabled1`, i.e.
	 * any disable record is still active - ours and/or SystemUI's lock flags.
	 * False only when everything has been cleared.
	 */
	suspend fun isApplied(context: Context): Boolean {
		val (exit, out) = CommandRunner.run(context, "dumpsys statusbar")
		if (exit != 0) return false
		val line = out.lineSequence().firstOrNull { it.trimStart().startsWith("mDisabled1=") } ?: return false
		val hex = line.substringAfter("mDisabled1=").trim().removePrefix("0x")
		val disabled1 = hex.toLongOrNull(16) ?: 0L
		return disabled1 != 0L
	}
}
