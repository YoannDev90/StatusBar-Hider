package dev.yoanndev90.statusbarhider.data

import android.content.Context
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for [OverlayPrefs]: the only entry point that reads
 * or writes the overlay blob (parse, legacy-slot migration and corrupt-blob
 * preservation all live in [OverlayPrefs.load] / [OverlayPrefs.save]).
 *
 * The Activity, the overlay Service and BootReceiver all observe the same
 * [StateFlow], so a settings change recomposes the UI and re-applies the
 * overlay without manual reloads. [update] and [replace] are synchronized:
 * a concurrent read-modify-write must not drop an update.
 *
 * Emitting is immediate and in-memory, but the JSON is written once the state
 * has been quiet for [PERSIST_DEBOUNCE_MS] on [Dispatchers.IO]. Dragging a
 * slider emits ~60 updates a second, and serializing each one on the caller's
 * thread was pure waste: the emitted value is already what the next write will
 * contain. The only cost is a change made less than a debounce window before
 * the process dies - which the previous code did not guarantee either, since
 * [OverlayPrefs.save] ends in `apply()`.
 */
class OverlayPrefsRepository private constructor(
	private val app: Context
) {
	private val _state = MutableStateFlow(OverlayPrefs.load(app))
	val state: StateFlow<OverlayPrefs> = _state.asStateFlow()

	private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var persistJob: Job? = null
	private var persistVersion = 0

	/** Applies [transform], emits the result and schedules the write. Returns the updated prefs. */
	fun update(transform: OverlayPrefs.() -> OverlayPrefs): OverlayPrefs =
		synchronized(this) {
			val updated = _state.value.transform()
			_state.value = updated
			schedulePersist(updated)
			updated
		}

	/**
	 * Replaces the whole state (backup import). Same contract as [update]:
	 * emit, then schedule the write of exactly this value.
	 */
	fun replace(value: OverlayPrefs): Unit =
		synchronized(this) {
			_state.value = value
			schedulePersist(value)
		}

	/**
	 * Writes the latest snapshot after a quiet window. Only the newest scheduled
	 * version reaches disk: a job that was already past its delay when a newer
	 * update arrived must not overwrite it with the stale value.
	 */
	private fun schedulePersist(value: OverlayPrefs) {
		val version = ++persistVersion
		persistJob?.cancel()
		persistJob =
			persistScope.launch {
				delay(PERSIST_DEBOUNCE_MS)
				synchronized(this@OverlayPrefsRepository) {
					if (version == persistVersion) OverlayPrefs.save(app, value)
				}
			}
	}

	companion object {
		/** Quiet time before the JSON is written; long enough to coalesce a drag. */
		private const val PERSIST_DEBOUNCE_MS = 300L

		@Volatile
		private var instance: OverlayPrefsRepository? = null

		fun getInstance(context: Context): OverlayPrefsRepository =
			instance ?: synchronized(this) {
				instance ?: OverlayPrefsRepository(context.applicationContext).also { instance = it }
			}
	}
}
