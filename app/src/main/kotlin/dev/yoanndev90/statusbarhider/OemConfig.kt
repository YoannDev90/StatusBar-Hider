package dev.yoanndev90.statusbarhider

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "statusbarhider"
private const val PREF_OEM_ID = "oem_id"

class OemConfigException(
	message: String
) : Exception(message)

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

		fun saveId(
			context: Context,
			id: String
		) {
			context
				.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				.edit()
				.putString(PREF_OEM_ID, id)
				.apply()
		}

		fun detect(context: Context): String {
			getSavedId(context)?.let { return it }
			val available = listAvailable(context)
			if (available.isEmpty()) {
				error("No OEM config files found in assets/oem/")
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
				if (load(context, id).match.any { props.contains(it) }) return id
			}
			return if (DEFAULT_ID in available) DEFAULT_ID else available.first()
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
					return FallbackConfig
				}

			return try {
				parseJson(id, raw)
			} catch (e: Exception) {
				Log.e(TAG, "Failed to parse OEM config: $id", e)
				FallbackConfig
			}
		}

		@Throws(OemConfigException::class)
		fun parseJson(
			id: String,
			raw: String
		): OemConfig {
			val json = JSONObject(raw)

			val schemaVersion = json.optInt("schema_version", 0)
			if (schemaVersion > CURRENT_SCHEMA_VERSION) {
				throw OemConfigException(
					"OEM config $id requires schema_version $schemaVersion, " +
						"app supports up to $CURRENT_SCHEMA_VERSION"
				)
			}

			fun parseCommands(arr: JSONArray): List<OemCommand> =
				(0 until arr.length()).map { i ->
					val o = arr.getJSONObject(i)
					OemCommand(
						name = o.getString("name"),
						cmd = o.getString("cmd"),
						description = o.optString("description", ""),
						persistent = o.optBoolean("persistent", true)
					)
				}

			fun parseStringList(arr: JSONArray?): List<String> =
				arr?.let { a -> (0 until a.length()).map { i -> a.getString(i) } } ?: emptyList()

			return OemConfig(
				id = id,
				name = json.getString("name"),
				untested = json.optBoolean("untested", false),
				match = parseStringList(json.optJSONArray("match")).map { it.lowercase() },
				notes = parseStringList(json.optJSONArray("notes")),
				hide = parseCommands(json.getJSONArray("hide")),
				restore = parseCommands(json.getJSONArray("restore")),
				status = parseCommands(json.getJSONArray("status"))
			)
		}

		private val FallbackConfig =
			OemConfig(
				id = "unknown",
				name = "Unknown OEM",
				hide = emptyList(),
				restore = emptyList(),
				status = emptyList()
			)
	}
}
