package dev.yoanndev90.statusbarhider.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.profiles.Profile
import dev.yoanndev90.statusbarhider.core.profiles.ProfilesState
import dev.yoanndev90.statusbarhider.core.profiles.ProfilesStore
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the profile snapshots, mirroring
 * [OverlayPrefsRepository]: one [StateFlow] the UI observes, every mutation
 * goes through the synchronized mutators below and lands in its own
 * SharedPreferences file.
 */
class ProfilesRepository private constructor(
	private val app: Context
) {
	private val _state = MutableStateFlow(load())
	val state: StateFlow<ProfilesState> = _state.asStateFlow()

	/**
	 * Stores [prefs] under [name] and marks it active. Returns the new profile,
	 * or null when the name is blank or the list is at [ProfilesStore.MAX_PROFILES].
	 */
	fun create(name: String, prefs: OverlayPrefs): Profile? =
		synchronized(this) {
			val cleanName = ProfilesStore.sanitizeName(name)
			if (cleanName.isEmpty() || _state.value.profiles.size >= ProfilesStore.MAX_PROFILES) {
				null
			} else {
				val profile = Profile(ProfilesStore.newId(), cleanName, System.currentTimeMillis(), prefs)
				commit(_state.value.copy(profiles = _state.value.profiles + profile, activeId = profile.id))
				profile
			}
		}

	/** Re-snapshots [id] from the live [prefs] and refreshes its date. */
	fun update(id: String, prefs: OverlayPrefs): Unit =
		synchronized(this) {
			commit(
				_state.value.copy(
					profiles =
						_state.value.profiles.map {
							if (it.id == id) it.copy(prefs = prefs, createdAt = System.currentTimeMillis()) else it
						}
				)
			)
		}

	/** Renames [id]; returns false when the new name is blank. */
	fun rename(id: String, name: String): Boolean =
		synchronized(this) {
			val cleanName = ProfilesStore.sanitizeName(name)
			if (cleanName.isEmpty()) {
				false
			} else {
				commit(_state.value.copy(profiles = _state.value.profiles.map { if (it.id == id) it.copy(name = cleanName) else it }))
				true
			}
		}

	/** Deletes [id] and clears the active marker when it pointed at the deleted profile. */
	fun delete(id: String): Unit =
		synchronized(this) {
			val state = _state.value
			commit(
				state.copy(
					profiles = state.profiles.filterNot { it.id == id },
					activeId = state.activeId.takeUnless { it == id }
				)
			)
		}

	/** Marks [id] as the applied profile (null clears the marker). */
	fun setActive(id: String?): Unit =
		synchronized(this) {
			commit(_state.value.copy(activeId = id))
		}

	/** Single write path: emit, then persist this exact value. */
	private fun commit(state: ProfilesState) {
		_state.value = state
		val encoded = ProfilesStore.encode(state)
		app
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit()
			.putString(KEY_PROFILES_V1, encoded)
			.apply()
	}

	private fun load(): ProfilesState {
		val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val raw = prefs.getString(KEY_PROFILES_V1, null) ?: return ProfilesState()
		return ProfilesStore.parse(raw) ?: corrupt(prefs, raw)
	}

	private fun corrupt(
		prefs: SharedPreferences,
		raw: String
	): ProfilesState {
		prefs.edit().putString(KEY_CORRUPT, raw).apply()
		val line = "Profiles failed to parse - empty list loaded, corrupt copy kept"
		Log.e(TAG, line)
		LogStore.append(app, line)
		return ProfilesState()
	}

	companion object {
		private const val TAG = "ProfilesRepository"
		internal const val PREFS_NAME = "profiles"
		private const val KEY_PROFILES_V1 = "profiles_v1"

		/** Preserved copy of an unparsable blob, so the defaults fallback stays recoverable. */
		private const val KEY_CORRUPT = "profiles_corrupt"

		@Volatile
		private var instance: ProfilesRepository? = null

		fun getInstance(context: Context): ProfilesRepository =
			instance ?: synchronized(this) {
				instance ?: ProfilesRepository(context.applicationContext).also { instance = it }
			}
	}
}
