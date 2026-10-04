package dev.yoanndev90.statusbarhider.data

import android.content.Context
import android.content.SharedPreferences
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for [OverlayPrefs].
 *
 * The Activity, the overlay Service and BootReceiver all observe the same
 * [StateFlow], so a settings change recomposes the UI and re-applies the
 * overlay without manual reloads. Persistence still goes through the
 * existing SharedPreferences slot (see [OverlayPrefs]) for backward
 * compatibility; the storage format is intentionally untouched.
 */
class OverlayPrefsRepository private constructor(
	private val prefs: SharedPreferences
) {
	private val _state = MutableStateFlow(load())
	val state: StateFlow<OverlayPrefs> = _state.asStateFlow()

	/** Re-read from disk (e.g. after an external change). */
	fun refresh(): OverlayPrefs {
		val loaded = load()
		_state.value = loaded
		return loaded
	}

	/** Applies [transform], persists the result and emits it. Returns the updated prefs. */
	fun update(transform: OverlayPrefs.() -> OverlayPrefs): OverlayPrefs {
		val updated = _state.value.transform()
		OverlayPrefs.save(prefs, updated)
		_state.value = updated
		return updated
	}

	/**
	 * Replaces the whole state (backup import). Same contract as [update]:
	 * persist first, then emit, so every observer sees the imported value.
	 */
	fun replace(value: OverlayPrefs) {
		OverlayPrefs.save(prefs, value)
		_state.value = value
	}

	private fun load(): OverlayPrefs = OverlayPrefs.load(prefs)

	companion object {
		@Volatile
		private var instance: OverlayPrefsRepository? = null

		fun getInstance(context: Context): OverlayPrefsRepository =
			instance ?: synchronized(this) {
				instance ?: OverlayPrefsRepository(
					context.applicationContext.getSharedPreferences(
						OverlayPrefs.PREFS_NAME,
						Context.MODE_PRIVATE
					)
				).also { instance = it }
			}
	}
}
