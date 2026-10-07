package dev.yoanndev90.statusbarhider.core.oem

import android.content.Context
import android.os.Build
import android.util.Log
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.log.LogStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

private const val PREFS_NAME = "statusbarhider"
private const val PREF_OEM_ID = "oem_id"

class OemConfigException(
	message: String
) : Exception(message)

@Serializable
data class OemCommand(
	val name: String,
	val cmd: String,
	val description: String = "",
	val persistent: Boolean = true
)

data class OemConfig(
	val id: String,
	val name: String,
	val untested: Boolean = false,
	val match: List<String> = emptyList(),
	val notes: List<String> = emptyList(),
	val hide: List<OemCommand>,
	val restore: List<OemCommand>,
	val status: List<OemCommand>
) {
	companion object {
		private const val TAG = "OemConfig"
		private const val CURRENT_SCHEMA_VERSION = 1

		/** Config used when no file matches the device (stock AOSP defaults). */
		private const val DEFAULT_ID = "aosp"

		/** Reader shared by every config file in assets/oem. */
		private val json = Json {
			ignoreUnknownKeys = true
			coerceInputValues = true
		}

		fun listAvailable(context: Context): List<String> =
			context.assets
				.list("oem")
				?.filter { it.endsWith(".json") }
				?.map {
					it.removeSuffix(".json")
				}?.sorted() ?: emptyList()

		fun getSavedId(context: Context): String? =
			context
				.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				.getString(PREF_OEM_ID, null)

		/**
		 * Saves the chosen config id; passing `null` forgets it so the next
		 * [detect] runs a fresh detection.
		 */
		fun saveId(
			context: Context,
			id: String?
		) {
			context
				.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				.edit()
				.putString(PREF_OEM_ID, id)
				.apply()
		}

		/**
		 * Config id to use: the saved pick when it still exists among the
		 * shipped configs (an app update can remove files), fresh detection
		 * otherwise.
		 */
		fun detect(context: Context): String {
			val saved = getSavedId(context)
			val available = listAvailable(context)
			if (saved != null) {
				if (saved in available) return saved
				Log.w(TAG, "Saved OEM config '$saved' no longer exists - re-detecting")
			}
			return detectFresh(context, available)
		}

		/** Runs detection from scratch, ignoring any saved id. */
		fun detectFresh(context: Context): String = detectFresh(context, listAvailable(context))

		private fun detectFresh(
			context: Context,
			available: List<String>
		): String {
			if (available.isEmpty()) {
				// Throwing here killed OemRepository's constructor; the load below
				// then reports the missing file in the app log.
				Log.e(TAG, "No OEM config files found in assets/oem/")
				return DEFAULT_ID
			}
			val props =
				listOf(
					Build.MANUFACTURER,
					Build.BRAND,
					Build.MODEL,
					Build.PRODUCT,
					Build.DISPLAY
				).joinToString(" ").lowercase()
			for (id in available) {
				if (props.contains(id)) return id
			}
			for (id in available) {
				if (loadCached(context, id).match.any { props.contains(it) }) return id
			}
			if (DEFAULT_ID !in available) {
				// Picking the alphabetically first config would run another
				// vendor's commands on this device; better to run none and say so.
				Log.e(TAG, "Default OEM config '$DEFAULT_ID' missing - no commands will run")
			}
			return DEFAULT_ID
		}

		fun load(
			context: Context,
			id: String
		): OemConfig {
			val raw =
				try {
					context.assets
						.open("oem/$id.json")
						.bufferedReader()
						.use { it.readText() }
				} catch (e: Exception) {
					Log.e(TAG, "Failed to read OEM config: $id", e)
					return reportFallback(context, id, e)
				}

			return try {
				parseJson(id, raw)
			} catch (e: Exception) {
				Log.e(TAG, "Failed to parse OEM config: $id", e)
				reportFallback(context, id, e)
			}
		}

		/**
		 * Parse cache, one entry per shipped id. Assets only change with an APK
		 * update, and the OEM dropdown reads every config file, so paying the
		 * asset read and the JSON parse once per process instead of once per
		 * screen (or once per tab visit) is the difference between a settings
		 * page that appears and one that stalls the main thread.
		 */
		private val parsed = ConcurrentHashMap<String, OemConfig>()

		/** [load], memoized for the lifetime of the process. */
		fun loadCached(
			context: Context,
			id: String
		): OemConfig = parsed.getOrPut(id) { load(context, id) }

		@Throws(OemConfigException::class)
		fun parseJson(
			id: String,
			raw: String
		): OemConfig {
			val root = json.parseToJsonElement(raw).jsonObject

			// Read before decoding so a too-new config is rejected with a clear
			// message instead of a plain decoding error.
			val schemaVersion = (root["schema_version"] as? JsonPrimitive)?.intOrNull ?: 0
			if (schemaVersion > CURRENT_SCHEMA_VERSION) {
				throw OemConfigException(
					"OEM config $id requires schema_version $schemaVersion, " +
						"app supports up to $CURRENT_SCHEMA_VERSION"
				)
			}

			val file = json.decodeFromJsonElement(OemConfigFile.serializer(), root)

			return OemConfig(
				id = id,
				name = file.name,
				untested = file.untested,
				match = file.match.map { it.lowercase() },
				notes = file.notes,
				hide = file.hide,
				restore = file.restore,
				status = file.status
			)
		}

		/**
		 * Config before detection has finished: no id, no name and no
		 * commands, so a consumer that does not wait cannot run anything off
		 * it by accident.
		 */
		fun placeholder(): OemConfig =
			OemConfig(
				id = "",
				name = "",
				hide = emptyList(),
				restore = emptyList(),
				status = emptyList()
			)

		/**
		 * Empty-command config used when no file could be loaded. The failure
		 * also reaches the app log: otherwise hide/restore would quietly run
		 * zero commands with nothing to explain it in the Logs tab.
		 */
		private fun reportFallback(
			context: Context,
			id: String,
			e: Exception
		): OemConfig {
			LogStore.appendOnce(
				context,
				"$TAG#load#$id",
				context.getString(R.string.log_oem_load_failed, id, e.message ?: e.toString())
			)
			return fallback(context)
		}

		/** Config used when no file could be loaded (stock AOSP defaults). */
		fun fallback(context: Context): OemConfig =
			OemConfig(
				id = "unknown",
				name = context.getString(R.string.oem_unknown),
				hide = emptyList(),
				restore = emptyList(),
				status = emptyList()
			)
	}
}

/** On-disk shape of a config file under assets/oem; `id` comes from the file name. */
@Serializable
private data class OemConfigFile(
	val name: String,
	val untested: Boolean = false,
	val match: List<String> = emptyList(),
	val notes: List<String> = emptyList(),
	val hide: List<OemCommand>,
	val restore: List<OemCommand>,
	val status: List<OemCommand>
)
