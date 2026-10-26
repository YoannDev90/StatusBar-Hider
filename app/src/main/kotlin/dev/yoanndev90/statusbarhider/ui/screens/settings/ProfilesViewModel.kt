package dev.yoanndev90.statusbarhider.ui.screens.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.yoanndev90.statusbarhider.core.profiles.Profile
import dev.yoanndev90.statusbarhider.core.profiles.ProfilesState
import dev.yoanndev90.statusbarhider.core.profiles.ProfilesStore
import dev.yoanndev90.statusbarhider.data.ProfilesRepository
import dev.yoanndev90.statusbarhider.overlay.OverlayController
import dev.yoanndev90.statusbarhider.ui.state.PrefsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the profile list and the apply / save / rename / delete commands.
 * The live prefs stay in [PrefsViewModel.prefs] (the overlay singleton), so
 * the screen can tell "matches the active profile" from "modified since".
 */
class ProfilesViewModel(
	application: Application
) : PrefsViewModel(application) {
	private val profilesRepo = ProfilesRepository.getInstance(application)

	/** Stored profiles plus the active id; the list screens observes this. */
	val profiles: StateFlow<ProfilesState> = profilesRepo.state

	/**
	 * The active profile resolved against the live prefs: null when nothing
	 * is applied, "modified" when the live settings drifted from the snapshot.
	 */
	val active: StateFlow<ActiveProfile?> =
		combineActive()
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	/** True when the live prefs differ from the applied snapshot (or nothing is applied). */
	val dirty: StateFlow<Boolean> =
		combineActive()
			.map { it == null || it.modified }
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

	private fun combineActive(): Flow<ActiveProfile?> =
		combine(profiles, prefs) { state, live ->
			state.profiles
				.find { it.id == state.activeId }
				?.let { ActiveProfile(it, it.prefs != live) }
		}

	/**
	 * Snapshots the live settings under [name] and applies it as active.
	 * Returns false when the name is blank or the profile cap is reached.
	 */
	fun saveCurrent(name: String): Boolean {
		val profile = profilesRepo.create(name, prefs.value) ?: return false
		log("Profile saved: ${profile.name}")
		return true
	}

	/** Replaces the live settings with the profile's snapshot and marks it active. */
	fun applyProfile(id: String) {
		val profile = profilesRepo.state.value.profiles
			.find { it.id == id } ?: return
		OverlayController.replaceAll(getApplication(), profile.prefs)
		profilesRepo.setActive(id)
		log("Profile applied: ${profile.name}")
	}

	/** Re-snapshots the live settings into [id], keeping it active. */
	fun updateProfile(id: String) {
		val profile = profilesRepo.state.value.profiles
			.find { it.id == id } ?: return
		profilesRepo.update(id, prefs.value)
		log("Profile updated: ${profile.name}")
	}

	/** Renames [id]; returns false when the new name is blank. */
	fun renameProfile(
		id: String,
		name: String
	): Boolean {
		if (!profilesRepo.rename(id, name)) return false
		val renamed = profilesRepo.state.value.profiles
			.find { it.id == id } ?: return true
		log("Profile renamed: ${renamed.name}")
		return true
	}

	/** Deletes [id] and clears the active marker when it pointed there. */
	fun deleteProfile(id: String) {
		val profile = profilesRepo.state.value.profiles
			.find { it.id == id } ?: return
		profilesRepo.delete(id)
		log("Profile deleted: ${profile.name}")
	}

	/** Resolves the cap for the UI ("limit reached" hint). */
	fun atCap(): Boolean = profiles.value.profiles.size >= ProfilesStore.MAX_PROFILES
}

/** The applied profile plus whether the live settings still match it. */
data class ActiveProfile(
	val profile: Profile,
	val modified: Boolean
)
