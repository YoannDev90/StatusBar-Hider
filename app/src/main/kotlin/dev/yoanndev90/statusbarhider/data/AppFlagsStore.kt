package dev.yoanndev90.statusbarhider.data

import android.content.Context
import android.content.SharedPreferences
import dev.yoanndev90.statusbarhider.core.settings.BoolSpec
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import java.security.SecureRandom

/**
 * App-level flags, stored next to the OEM pick and the hide state but kept
 * out of [dev.yoanndev90.statusbarhider.overlay.OverlayPrefs]: they are not
 * settings of the custom bar and must survive a settings import.
 */
data class AppFlags(
	val setupDone: Boolean = false
)

/** The app flags as specs: described for the registry, never exported. */
object AppFlagSpecs {
	val setupDone: BoolSpec<AppFlags> =
		BoolSpec(
			key = "setup_done",
			get = AppFlags::setupDone,
			set = { flags, value -> flags.copy(setupDone = value) },
			default = false,
			exportable = false
		)

	val all: List<SettingSpec<*, AppFlags>> = listOf(setupDone)
}

/**
 * The app flags file: the same "statusbarhider" SharedPreferences as ever,
 * described here so the registry can list and reset the flags too. The
 * storage keys are unchanged; nothing migrates.
 *
 * [controlToken] is deliberately not a spec: a secret is never rendered nor
 * serialized. It is generated on first use and stable for the install: the
 * widget bakes it into its PendingIntents, so a change would break placed
 * widgets. Lives in this prefs file, which the settings backup never reads
 * or writes, so the secret leaves the device only through an explicit copy.
 */
class AppFlagsStore private constructor(
	private val app: Context
) {
	/** False on first launch: the setup checklist opens instead of the shell. */
	fun isSetupDone(): Boolean = prefs().getBoolean(KEY_SETUP_DONE, false)

	fun setSetupDone(done: Boolean) {
		prefs().edit().putBoolean(KEY_SETUP_DONE, done).apply()
	}

	/** Secret the external control API is gated on (see [dev.yoanndev90.statusbarhider.control.ControlAuth]). */
	@Synchronized
	fun controlToken(): String {
		val prefs = prefs()
		prefs.getString(KEY_CONTROL_TOKEN, null)?.let { return it }
		val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
		val token = bytes.joinToString("") { "%02x".format(it) }
		prefs.edit().putString(KEY_CONTROL_TOKEN, token).apply()
		return token
	}

	private fun prefs(): SharedPreferences =
		app.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	companion object {
		private const val PREFS_NAME = "statusbarhider"
		private const val KEY_SETUP_DONE = "setup_done"
		private const val KEY_CONTROL_TOKEN = "control_token"

		@Volatile
		private var instance: AppFlagsStore? = null

		fun getInstance(context: Context): AppFlagsStore =
			instance ?: synchronized(this) {
				instance ?: AppFlagsStore(context.applicationContext).also { instance = it }
			}
	}
}
