package dev.yoanndev90.statusbarhider.data

import android.content.Context
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for [OverlayPrefs]: the only entry point that reads
 * or writes the overlay blob (parse, legacy-slot migration and corrupt-blob
 * preservation all live in [OverlayPrefs.load] / [OverlayPrefs.save]).
 *
 * The Activity, the overlay Service and BootReceiver all observe the same
 * [StateFlow], so a settings change recomposes the UI and re-applies the
 * overlay without manual reloads. [update] and [replace] are synchronized and
 * always persist first, then emit: a concurrent read-modify-write must not
 * drop an update, and every observer sees the persisted value.
 */
class OverlayPrefsRepository private constructor(
	private val app: Context
) {
	private val _state = MutableStateFlow(OverlayPrefs.load(app))
	val state: StateFlow<OverlayPrefs> = _state.asStateFlow()

	/** Applies [transform], persists the result and emits it. Returns the updated prefs. */
	fun update(transform: OverlayPrefs.() -> OverlayPrefs): OverlayPrefs =
		synchronized(this) {
			val updated = _state.value.transform()
			OverlayPrefs.save(app, updated)
			_state.value = updated
			updated
		}

	/**
	 * Replaces the whole state (backup import). Same contract as [update]:
	 * persist first, then emit, so every observer sees the imported value.
	 */
	fun replace(value: OverlayPrefs): Unit =
		synchronized(this) {
			OverlayPrefs.save(app, value)
			_state.value = value
		}

	companion object {
		@Volatile
		private var instance: OverlayPrefsRepository? = null

		fun getInstance(context: Context): OverlayPrefsRepository =
			instance ?: synchronized(this) {
				instance ?: OverlayPrefsRepository(context.applicationContext).also { instance = it }
			}
	}
}
