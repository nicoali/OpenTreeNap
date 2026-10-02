package org.opentreenap.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.opentreenap.mobile.model.TreeMarker
import java.io.File

data class TreeCacheSnapshot(
    val trees: List<TreeMarker>,
    val savedAt: Long
)

class TreeCache(
    context: Context
) {
    private val cacheFile =
        File(context.filesDir, "trees-cache-v2.json")

    fun load(): TreeCacheSnapshot? {
        if (!cacheFile.exists()) return null

        return runCatching {
            val root = JSONObject(cacheFile.readText())
            val array = root.optJSONArray("trees") ?: JSONArray()
            val trees = ArrayList<TreeMarker>(array.length())

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                trees += item.toTreeMarker()
            }

            TreeCacheSnapshot(
                trees = trees,
                savedAt = root.optLong("savedAt", 0L)
            )
        }.getOrNull()
    }

    fun save(trees: List<TreeMarker>) {
        runCatching {
            val array = JSONArray()
            trees.forEach { array.put(it.toJson()) }

            val root = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("trees", array)

            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(root.toString())

            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    private fun TreeMarker.toJson(): JSONObject =
        JSONObject()
            .put("plotId", plotId)
            .put("treeId", treeId ?: JSONObject.NULL)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("title", title)
            .put("snippet", snippet ?: JSONObject.NULL)
            .put("commonName", commonName ?: JSONObject.NULL)
            .put("scientificName", scientificName ?: JSONObject.NULL)
            .put("speciesId", speciesId ?: JSONObject.NULL)
            .put("address", address ?: JSONObject.NULL)
            .put("diameter", diameter ?: JSONObject.NULL)
            .put("height", height ?: JSONObject.NULL)
            .put("customId", customId ?: JSONObject.NULL)
            .put("isMonumental", isMonumental)

    private fun JSONObject.toTreeMarker(): TreeMarker =
        TreeMarker(
            plotId = optInt("plotId", -1),
            treeId = optNullableInt("treeId"),
            latitude = optDouble("latitude"),
            longitude = optDouble("longitude"),
            title = optString("title", "Albero"),
            snippet = optNullableString("snippet"),
            commonName = optNullableString("commonName"),
            scientificName = optNullableString("scientificName"),
            speciesId = optNullableInt("speciesId"),
            address = optNullableString("address"),
            diameter = optNullableDouble("diameter"),
            height = optNullableDouble("height"),
            customId = optNullableString("customId"),
            isMonumental = optBoolean("isMonumental", false)
        )

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null
        else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null
        else optInt(key).takeIf { it >= 0 }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null
        else optDouble(key, Double.NaN).takeIf { it.isFinite() }
}
