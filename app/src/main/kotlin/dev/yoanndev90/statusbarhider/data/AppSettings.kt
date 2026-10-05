package dev.yoanndev90.statusbarhider.data

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/**
 * App-level flags, stored next to the OEM pick and the hide state but kept
 * out of [dev.yoanndev90.statusbarhider.overlay.OverlayPrefs]: they are not
 * settings of the custom bar and must survive a settings import.
 */
object AppSettings {
	private const val PREFS_NAME = "statusbarhider"
	private const val KEY_SETUP_DONE = "setup_done"
	private const val KEY_CONTROL_TOKEN = "control_token"

	/** False on first launch: the setup checklist opens instead of the shell. */
	fun isSetupDone(context: Context): Boolean = prefs(context).getBoolean(KEY_SETUP_DONE, false)

	fun setSetupDone(
		context: Context,
		done: Boolean
	) {
		prefs(context).edit().putBoolean(KEY_SETUP_DONE, done).apply()
	}

	/**
	 * Secret the external control API is gated on (see [dev.yoanndev90.statusbarhider.control.ControlAuth]).
	 * Generated on first use and stable for the install: the widget bakes it
	 * into its PendingIntents, so a change would break placed widgets. Lives in
	 * this prefs file, which the settings backup never reads or writes, so the
	 * secret leaves the device only through an explicit copy.
	 */
	@Synchronized
	fun controlToken(context: Context): String {
		val prefs = prefs(context)
		prefs.getString(KEY_CONTROL_TOKEN, null)?.let { return it }
		val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
		val token = bytes.joinToString("") { "%02x".format(it) }
		prefs.edit().putString(KEY_CONTROL_TOKEN, token).apply()
		return token
	}

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
