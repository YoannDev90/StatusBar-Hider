package dev.yoanndev90.statusbarhider.data

import android.content.Context
import dev.yoanndev90.statusbarhider.core.oem.OemConfig
import dev.yoanndev90.statusbarhider.core.settings.SettingSpec
import dev.yoanndev90.statusbarhider.core.settings.TextSpec

/** The OEM pick as a value: [oemId] "" means "no pick, detection decides". */
data class OemPrefs(
	val oemId: String = ""
)

/** The OEM pick as one spec: carried by the backup envelope, never drawn as a row. */
object OemSpecs {
	val oemId: TextSpec<OemPrefs> =
		TextSpec(
			key = "oem_id",
			get = OemPrefs::oemId,
			set = { prefs, value -> prefs.copy(oemId = value) },
			default = "",
			validate = { true }
		)

	val all: List<SettingSpec<*, OemPrefs>> = listOf(oemId)
}

/**
 * The picked OEM config id as a setting file: the same SharedPreferences as
 * ever ([OemConfig] owns the key), described here so backup reads one spec.
 * The loaded config stays [OemRepository]'s job — a write saves the pick and
 * makes the repository reload, and detection keeps its fallback role.
 */
class OemStore private constructor(
	private val app: Context
) {
	/** The saved pick; "" when none was ever made. */
	fun currentId(): String = OemConfig.getSavedId(app).orEmpty()

	/** Picks [id]; "" forgets it, so the next load detects afresh. */
	fun write(id: String) {
		OemConfig.saveId(app, id.ifEmpty { null })
		OemRepository.getInstance(app).refresh()
	}

	companion object {
		@Volatile
		private var instance: OemStore? = null

		fun getInstance(context: Context): OemStore =
			instance ?: synchronized(this) {
				instance ?: OemStore(context.applicationContext).also { instance = it }
			}
	}
}
