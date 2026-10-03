package dev.yoanndev90.statusbarhider.data

import android.content.Context
import dev.yoanndev90.statusbarhider.OemConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the active [OemConfig]: auto-detected once (then persisted), or picked
 * by the user from the OEM dropdown.
 */
class OemRepository private constructor(
	private val appContext: Context
) {
	private val _config = MutableStateFlow(loadActive())
	val config: StateFlow<OemConfig> = _config.asStateFlow()

	/** Re-runs detection + load (e.g. after new OEM files ship). Returns the new config. */
	fun refresh(): OemConfig {
		val loaded = loadActive()
		_config.value = loaded
		return loaded
	}

	/** Forces a fresh detection, forgetting any previously saved / picked id. */
	fun redetect(): OemConfig = refreshWith { OemConfig.saveId(appContext, null) }

	/** Switches to another shipped config id. */
	fun select(id: String): OemConfig = refreshWith { OemConfig.saveId(appContext, id) }

	private fun refreshWith(before: () -> Unit): OemConfig {
		before()
		return refresh()
	}

	private fun loadActive(): OemConfig {
		val oemId = OemConfig.detect(appContext)
		OemConfig.saveId(appContext, oemId)
		return OemConfig.load(appContext, oemId)
	}

	companion object {
		@Volatile
		private var instance: OemRepository? = null

		fun getInstance(context: Context): OemRepository =
			instance ?: synchronized(this) {
				instance ?: OemRepository(context.applicationContext).also { instance = it }
			}
	}
}
