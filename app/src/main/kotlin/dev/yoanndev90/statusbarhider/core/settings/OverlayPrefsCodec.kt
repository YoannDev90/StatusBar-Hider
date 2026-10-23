package dev.yoanndev90.statusbarhider.core.settings

import dev.yoanndev90.statusbarhider.core.settings.specs.OverlaySpecs
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.WidgetId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Pure codec of the stored overlay blob: string <-> [OverlayPrefs].
 *
 * No Android types here on purpose: the same code runs in unit tests, in the
 * settings repository, in the backup parser and in the profiles store, and
 * all four must agree on what a stored value means. The storage keys are the
 * model's own field names (`@SerialName` needs compile-time constants next to
 * the class), so this file declares none of them.
 *
 * There is no legacy support here: a blob written by an older build is read
 * as-is, with its unknown keys ignored and every absent field falling back to
 * its default. `tools/settings-migrate/migrate_backup.py` upgrades an old
 * backup file when a user actually needs one converted.
 */
object OverlayPrefsCodec {
	/** Shared reader / writer for the stored blob. */
	private val json =
		Json {
			ignoreUnknownKeys = true
			coerceInputValues = true
			encodeDefaults = true
		}

	/** Stored shape of [prefs]. */
	fun toJson(prefs: OverlayPrefs): String = json.encodeToString(OverlayPrefs.serializer(), prefs)

	/**
	 * Parses the stored blob, then clamps it through [OverlaySpecs.normalize].
	 * Returns null on malformed input so the caller can report it and keep
	 * the raw copy instead of silently losing settings.
	 */
	fun fromJson(raw: String): OverlayPrefs? =
		try {
			OverlaySpecs.normalize(json.decodeFromString(OverlayPrefs.serializer(), raw))
		} catch (_: Exception) {
			null
		}

	/**
	 * Strict parse for backups and profile snapshots: throws on malformed
	 * input instead of silently falling back to the defaults, but clamps
	 * exactly like [fromJson].
	 */
	fun decodeStrict(element: JsonElement): OverlayPrefs =
		OverlaySpecs.normalize(json.decodeFromJsonElement(OverlayPrefs.serializer(), element))
}

/**
 * Keeps [OverlayPrefs.widgetOrder] stored as the legacy comma-separated string
 * instead of a JSON array, so older builds can still read it.
 */
internal object WidgetOrderSerializer : KSerializer<List<String>> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("widgetOrder", PrimitiveKind.STRING)

	override fun serialize(
		encoder: Encoder,
		value: List<String>
	) {
		encoder.encodeString(value.joinToString(","))
	}

	override fun deserialize(decoder: Decoder): List<String> = WidgetId.parseOrder(decoder.decodeString())
}

/**
 * Same storage choice as [WidgetOrderSerializer] for the app blacklist:
 * package names never contain a comma, and the CSV form stays readable.
 */
internal object CsvListSerializer : KSerializer<List<String>> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("csvList", PrimitiveKind.STRING)

	override fun serialize(
		encoder: Encoder,
		value: List<String>
	) {
		encoder.encodeString(value.joinToString(","))
	}

	override fun deserialize(decoder: Decoder): List<String> =
		decoder
			.decodeString()
			.split(",")
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.distinct()
}
