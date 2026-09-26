package com.example.statusbarhider

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OemCommand(
    val name: String,
    val cmd: String,
    val description: String = "",
    val persistent: Boolean = true,
)

data class OemConfig(
    val name: String,
    val hide: List<OemCommand>,
    val restore: List<OemCommand>,
    val status: List<OemCommand>,
) {
    companion object {

        fun listAvailable(context: Context): List<String> =
            context.assets.list("oem")?.filter { it.endsWith(".json") }?.map {
                it.removeSuffix(".json")
            }?.sorted() ?: emptyList()

        fun load(context: Context, id: String): OemConfig {
            val raw = context.assets.open("oem/$id.json").bufferedReader().use { it.readText() }
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
                name = json.getString("name"),
                hide = parseCommands(json.getJSONArray("hide")),
                restore = parseCommands(json.getJSONArray("restore")),
                status = parseCommands(json.getJSONArray("status")),
            )
        }
    }
}
