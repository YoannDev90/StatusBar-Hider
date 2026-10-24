package dev.yoanndev90.statusbarhider.core.profiles

import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** One named snapshot of the overlay settings. */
@Serializable
data class Profile(
	val id: String,
	val name: String,
	val createdAt: Long,
	val prefs: OverlayPrefs
)

/** Everything the profiles file holds: the snapshots plus which one is applied. */
@Serializable
data class ProfilesState(
	val profiles: List<Profile> = emptyList(),
	/** Id of the applied profile, or null when the live settings match no snapshot. */
	val activeId: String? = null
)

/**
 * Pure codec for the profiles blob: no Android types, so it stays unit-testable.
 * [parse] normalizes every snapshot's prefs the same way a normal load does.
 */
object ProfilesStore {
	/** Ceiling on stored profiles; each one carries a full settings copy. */
	const val MAX_PROFILES = 20

	/** Names are user input: trimmed, capped, never blank. */
	const val MAX_NAME_LENGTH = 40

	/** Shared reader / writer, same flags as the overlay blob. */
	private val json = Json {
		ignoreUnknownKeys = true
		coerceInputValues = true
		encodeDefaults = true
	}

	/** Fresh unique id for a new profile. */
	fun newId(): String = UUID.randomUUID().toString()

	/** Trims and caps a user-supplied name; blank stays blank for the caller to reject. */
	fun sanitizeName(raw: String): String = raw.trim().take(MAX_NAME_LENGTH)

	fun encode(state: ProfilesState): String = json.encodeToString(ProfilesState.serializer(), state)

	/**
	 * Parses the stored blob, normalizing every snapshot's prefs like a normal
	 * load. Returns null on malformed input so the caller can keep the raw copy.
	 */
	fun parse(raw: String): ProfilesState? =
		try {
			val state = json.decodeFromString(ProfilesState.serializer(), raw)
			state.copy(profiles = state.profiles.map { it.normalizePrefs() })
		} catch (_: Exception) {
			null
		}

	/** Round-trips the snapshot's prefs through the strict decoder to clamp values. */
	private fun Profile.normalizePrefs(): Profile {
		val element = json.parseToJsonElement(json.encodeToString(OverlayPrefs.serializer(), prefs))
		return copy(prefs = OverlayPrefs.decodeStrict(element))
	}
}
