package org.opentreenap.mobile.api

import org.json.JSONArray
import org.json.JSONObject
import org.opentreenap.mobile.model.TreeMarker
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OtmApiClient(
    baseUrl: String,
    private val instance: String,
    private val accessKey: String,
    secretKey: String
) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val signer = HmacSigner(secretKey)

    fun fetchTrees(size: Int = 100, offset: Int = 0): List<TreeMarker> {
        require(baseUrl.startsWith("https://")) { "OTM_BASE_URL deve usare HTTPS" }
        require(instance.isNotBlank()) { "OTM_INSTANCE mancante" }
        require(accessKey.isNotBlank()) { "OTM_ACCESS_KEY mancante" }

        val timestamp = utcTimestamp()
        val requestUrl =
            baseUrl + "/api/v4/instance/" + instance + "/plots" +
                "?offset=" + offset + "&size=" + size +
                "&timestamp=" + timestamp + "&access_key=" + accessKey
        val signature = signer.sign("GET", requestUrl)

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Signature", signature)
            setRequestProperty("platform-ver-build", "OpenTreeNap-Android/0.2.0")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("API HTTP " + status + ": " + body.take(300))
            return parsePlots(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePlots(rawJson: String): List<TreeMarker> {
        val array = JSONArray(rawJson)
        val markers = ArrayList<TreeMarker>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val plot = item.optJSONObject("plot") ?: continue
            val geometry = plot.optJSONObject("geom") ?: continue
            val latitude = geometry.optDouble("y", Double.NaN)
            val longitude = geometry.optDouble("x", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite()) continue

            val tree = item.optJSONObject("tree")
            val hasTree = item.optBoolean("has_tree", tree != null && tree.length() > 0)
            if (!hasTree) continue

            val plotId = plot.optInt("id", -1)
            val treeId = tree?.optInt("id", -1)?.takeIf { it >= 0 }
            val species = tree?.optJSONObject("species")
            val commonName = species?.optString("common_name")?.trim().orEmpty()
            val genus = species?.optString("genus")?.trim().orEmpty()
            val speciesName = species?.optString("species")?.trim().orEmpty()
            val scientificName = listOf(genus, speciesName).filter { it.isNotBlank() }.joinToString(" ")
            val address = item.optString("address_full").trim()
            val apiTitle = item.optString("title").trim()

            val title = commonName.takeIf { it.isNotBlank() }
                ?: scientificName.takeIf { it.isNotBlank() }
                ?: apiTitle.takeIf { it.isNotBlank() }
                ?: treeId?.let { "Albero #$it" }
                ?: "Albero"

            markers += TreeMarker(
                plotId = plotId,
                treeId = treeId,
                latitude = latitude,
                longitude = longitude,
                title = title,
                snippet = buildSnippet(commonName, scientificName, address, plotId),
                commonName = commonName.takeIf { it.isNotBlank() },
                scientificName = scientificName.takeIf { it.isNotBlank() },
                address = address.takeIf { it.isNotBlank() },
                isMonumental = hasMonumentalFlag(item, tree)
            )
        }
        return markers
    }

    private fun buildSnippet(
        commonName: String,
        scientificName: String,
        address: String,
        plotId: Int
    ): String? {
        val parts = mutableListOf<String>()
        if (commonName.isNotBlank()) parts += commonName
        if (scientificName.isNotBlank() && scientificName != commonName) parts += scientificName
        if (address.isNotBlank()) parts += address
        if (plotId >= 0) parts += "Sito #$plotId"
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun hasMonumentalFlag(item: JSONObject, tree: JSONObject?): Boolean =
        jsonHasMonumentalFlag(item) || (tree?.let { jsonHasMonumentalFlag(it) } == true)

    private fun jsonHasMonumentalFlag(obj: JSONObject, depth: Int = 0): Boolean {
        if (depth > 4) return false
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            val normalized = key.lowercase(Locale.ROOT)
            val relevant = normalized.contains("monument") ||
                normalized.contains("centenar") ||
                normalized.contains("heritage")
            if (relevant && isTruthy(value)) return true
            if (value is JSONObject && jsonHasMonumentalFlag(value, depth + 1)) return true
        }
        return false
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.trim().lowercase(Locale.ROOT) in setOf(
            "1", "true", "yes", "si", "sì", "monumentale", "centenario", "centenaria", "heritage"
        )
        else -> false
    }

    private fun utcTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
