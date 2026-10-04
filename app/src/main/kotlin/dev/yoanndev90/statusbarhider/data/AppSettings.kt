package dev.yoanndev90.statusbarhider.data

import android.content.Context
import android.content.SharedPreferences

/**
 * App-level flags, stored next to the OEM pick and the hide state but kept
 * out of [dev.yoanndev90.statusbarhider.overlay.OverlayPrefs]: they are not
 * settings of the custom bar and must survive a settings import.
 */
object AppSettings {
	private const val PREFS_NAME = "statusbarhider"
	private const val KEY_SETUP_DONE = "setup_done"

	/** False on first launch: the setup checklist opens instead of the shell. */
	fun isSetupDone(context: Context): Boolean = prefs(context).getBoolean(KEY_SETUP_DONE, false)

	fun setSetupDone(
		context: Context,
		done: Boolean
	) {
		prefs(context).edit().putBoolean(KEY_SETUP_DONE, done).apply()
	}

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
