package dev.yoanndev90.statusbarhider.core.settings

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
	 * Parses the stored blob, then clamps it. Returns null on malformed input
	 * so the caller can report it and keep the raw copy instead of silently
	 * losing settings.
	 */
	fun fromJson(raw: String): OverlayPrefs? =
		try {
			normalize(json.decodeFromString(OverlayPrefs.serializer(), raw))
		} catch (_: Exception) {
			null
		}

	/**
	 * Strict parse for backups and profile snapshots: throws on malformed
	 * input instead of silently falling back to the defaults, but clamps
	 * exactly like [fromJson].
	 */
	fun decodeStrict(element: JsonElement): OverlayPrefs = normalize(json.decodeFromJsonElement(OverlayPrefs.serializer(), element))

	/** Clamps free-form fields the way the old per-key opt* parser did. */
	fun normalize(prefs: OverlayPrefs): OverlayPrefs =
		prefs.copy(
			updateIntervalSec = prefs.updateIntervalSec.coerceIn(5, 60),
			padStartDp = prefs.padStartDp.coerceIn(0, 32),
			padTopDp = prefs.padTopDp.coerceIn(0, 32),
			padEndDp = prefs.padEndDp.coerceIn(0, 32),
			padBottomDp = prefs.padBottomDp.coerceIn(0, 32),
			dateFormat = prefs.dateFormat.ifEmpty { OverlayPrefs.DEFAULT_DATE_FORMAT },
			maxNotifs = prefs.maxNotifs.coerceIn(1, 8),
			burnInMin = prefs.burnInMin.coerceIn(0, 30),
			fontSizeSp = prefs.fontSizeSp.coerceIn(10, 20),
			fontWeightName = prefs.fontWeightName.takeIf { it in FONT_WEIGHTS } ?: "NORMAL",
			widgetSpacingDp = prefs.widgetSpacingDp.coerceIn(0, 12),
			cameraGapDp = prefs.cameraGapDp.coerceIn(0, 16),
			cameraNudgeXDp = prefs.cameraNudgeXDp.coerceIn(-48, 48),
			cameraNudgeYDp = prefs.cameraNudgeYDp.coerceIn(-48, 48),
			cameraOffsetXPct = prefs.cameraOffsetXPct.coerceIn(0, 100),
			cameraOffsetYDp = prefs.cameraOffsetYDp.coerceIn(0, 60),
			cameraSizeDp = prefs.cameraSizeDp.coerceIn(0, 40),
			cameraRingStrokeDp = prefs.cameraRingStrokeDp.coerceIn(1, 8),
			cameraRingColor = prefs.cameraRingColor.takeIf { RING_COLOR.matches(it) } ?: ""
		)

	/** Accepted values of [OverlayPrefs.fontWeightName]. */
	private val FONT_WEIGHTS = setOf("NORMAL", "MEDIUM", "BOLD")

	/** Stored ring colors are plain `#RRGGBB`; anything else falls back to the text color. */
	private val RING_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
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
