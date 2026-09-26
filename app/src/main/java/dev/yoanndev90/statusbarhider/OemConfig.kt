package dev.yoanndev90.statusbarhider

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "statusbarhider"
private const val PREF_OEM_ID = "oem_id"

data class OemCommand(
    val name: String,
    val cmd: String,
    val description: String = "",
    val persistent: Boolean = true,
)

data class OemConfig(
    val id: String,
    val name: String,
    val hide: List<OemCommand>,
    val restore: List<OemCommand>,
    val status: List<OemCommand>,
) {
    companion object {
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
            id: String,
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
            val props =
                listOf(
                    Build.MANUFACTURER,
                    Build.BRAND,
                    Build.MODEL,
                    Build.PRODUCT,
                    Build.DISPLAY,
                ).joinToString(" ").lowercase()
            for (id in available) {
                if (props.contains(id)) return id
            }
            return available.first()
        }

        fun load(
            context: Context,
            id: String,
        ): OemConfig {
            val raw =
                context.assets
                    .open("oem/$id.json")
                    .bufferedReader()
                    .use { it.readText() }
            val json = JSONObject(raw)

            fun parseCommands(arr: JSONArray): List<OemCommand> =
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    OemCommand(
                        name = o.getString("name"),
                        cmd = o.getString("cmd"),
                        description = o.optString("description", ""),
                        persistent = o.optBoolean("persistent", true),
                    )
                }

            return OemConfig(
                id = id,
                name = json.getString("name"),
                hide = parseCommands(json.getJSONArray("hide")),
                restore = parseCommands(json.getJSONArray("restore")),
                status = parseCommands(json.getJSONArray("status")),
            )
        }
    }
}
