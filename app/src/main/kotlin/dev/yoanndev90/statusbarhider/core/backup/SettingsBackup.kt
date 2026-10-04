package dev.yoanndev90.statusbarhider.core.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import dev.yoanndev90.statusbarhider.core.oem.OemConfig
import dev.yoanndev90.statusbarhider.data.OemRepository
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.hide.HideController
import dev.yoanndev90.statusbarhider.overlay.OverlayPrefs
import dev.yoanndev90.statusbarhider.overlay.StatusBarOverlayService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import dev.yoanndev90.statusbarhider.widget.BarWidgetProvider as BarWidget

class SettingsBackupException(
	message: String
) : Exception(message)

/**
 * Export / import of everything a user would want to keep: the overlay
 * preferences and the picked OEM config.
 *
 * The blob is plain JSON with a schema version, so a backup made by a newer
 * build is refused with a clear message instead of being half-applied.
 */
object SettingsBackup {
	private const val TAG = "SettingsBackup"
	private const val CURRENT_SCHEMA = 1
	private const val FILE_NAME = "statusbarhider-settings.json"

	private const val KEY_SCHEMA = "schema_version"
	private const val KEY_OEM = "oem_id"
	private const val KEY_OVERLAY = "overlay"

	private val json =
		Json {
			ignoreUnknownKeys = true
			coerceInputValues = true
			encodeDefaults = true
		}

	/** Parsed backup: [prefs] is normalized the same way a normal load is. */
	data class Backup(
		val oemId: String,
		val prefs: OverlayPrefs
	)

	/** Builds the export blob from the live state. */
	fun export(context: Context): String {
		val app = context.applicationContext
		val overlay = OverlayPrefsRepository.getInstance(app).state.value
		val oemId = OemConfig.getSavedId(app) ?: OemConfig.detect(app)
		val root =
			JsonObject(
				mapOf(
					KEY_SCHEMA to JsonPrimitive(CURRENT_SCHEMA),
					KEY_OEM to JsonPrimitive(oemId),
					KEY_OVERLAY to json.parseToJsonElement(overlay.toJson())
				)
			)
		return json.encodeToString(JsonObject.serializer(), root)
	}

	/** Writes [export] to the cache and shares it through the file provider. */
	fun shareIntent(context: Context): Intent? =
		try {
			val app = context.applicationContext
			val file = java.io.File(app.cacheDir, "settings/$FILE_NAME")
			file.parentFile?.mkdirs()
			file.writeText(export(app))
			val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
			Intent(Intent.ACTION_SEND).apply {
				type = "application/json"
				putExtra(Intent.EXTRA_STREAM, uri)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
		} catch (e: Exception) {
			Log.w(TAG, "Failed to build the export intent", e)
			null
		}

	/** Reads a backup blob; throws [SettingsBackupException] on anything unusable. */
	fun parse(raw: String): Backup {
		val root =
			try {
				json.parseToJsonElement(raw).jsonObject
			} catch (e: Exception) {
				throw SettingsBackupException("Not a valid JSON file: ${e.message}")
			}
		val version = (root[KEY_SCHEMA] as? JsonPrimitive)?.intOrNull ?: 0
		if (version > CURRENT_SCHEMA) {
			throw SettingsBackupException("Backup schema $version is newer than the supported $CURRENT_SCHEMA")
		}
		val overlay = root[KEY_OVERLAY] ?: throw SettingsBackupException("Missing '$KEY_OVERLAY' block")
		val prefs =
			try {
				OverlayPrefs.decodeStrict(overlay)
			} catch (e: Exception) {
				throw SettingsBackupException("Unreadable overlay block: ${e.message}")
			}
		val oemId = (root[KEY_OEM] as? JsonPrimitive)?.content.orEmpty()
		return Backup(oemId, prefs)
	}

	/**
	 * Applies a backup: prefs first (observers recompose), then the OEM pick,
	 * then the side effects that must follow a prefs swap - the overlay service
	 * and the widgets / tiles whose labels changed.
	 */
	fun importSettings(
		context: Context,
		raw: String
	) {
		val app = context.applicationContext
		val backup = parse(raw)
		OverlayPrefsRepository.getInstance(app).replace(backup.prefs)
		if (backup.oemId.isNotEmpty()) OemConfig.saveId(app, backup.oemId)
		OemRepository.getInstance(app).refresh()
		if (backup.prefs.enabled) {
			StatusBarOverlayService.start(app)
		} else {
			StatusBarOverlayService.stop(app)
		}
		HideController.notifyTiles(app)
		BarWidget.updateAll(app)
	}
}
