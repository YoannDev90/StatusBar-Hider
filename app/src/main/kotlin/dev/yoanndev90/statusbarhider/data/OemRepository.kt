package dev.yoanndev90.statusbarhider.data

import android.content.Context
import android.util.Log
import dev.yoanndev90.statusbarhider.core.oem.OemConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Holds the active [OemConfig]: auto-detected once (then persisted), or picked
 * by the user from the OEM dropdown.
 *
 * Detection reads assets and SharedPreferences, and the thread that asks first
 * is the main one - a freshly constructed ViewModel asks before anything else
 * does. So the instance starts on [OemConfig.placeholder] and fills itself in
 * on the IO lane; consumers that would run commands wait with [awaitLoaded].
 */
class OemRepository private constructor(
	private val appContext: Context
) {
	private val placeholder = OemConfig.placeholder()
	private val _config = MutableStateFlow(placeholder)
	val config: StateFlow<OemConfig> = _config.asStateFlow()

	init {
		ioScope.launch {
			val active =
				try {
					loadActive()
				} catch (e: Exception) {
					Log.w(TAG, "OEM detection failed", e)
					OemConfig.fallback(appContext)
				}
			// A refresh() that got there first keeps its answer.
			_config.compareAndSet(placeholder, active)
		}
	}

	/** The active config, waiting out the one-time detection pass. */
	suspend fun awaitLoaded(): OemConfig = config.first { it !== placeholder }

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
		return OemConfig.loadCached(appContext, oemId)
	}

	companion object {
		private const val TAG = "OemRepository"

		/** Process-lifetime lane for the detection pass started in `init`. */
		private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

		@Volatile
		private var instance: OemRepository? = null

		fun getInstance(context: Context): OemRepository =
			instance ?: synchronized(this) {
				instance ?: OemRepository(context.applicationContext).also { instance = it }
			}
	}
}
