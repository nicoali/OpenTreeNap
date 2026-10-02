package org.opentreenap.mobile.api

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.opentreenap.mobile.model.ApiUser
import org.opentreenap.mobile.model.InstancePermissions
import org.opentreenap.mobile.model.SpeciesItem
import org.opentreenap.mobile.model.TreeMarker
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
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
        require(baseUrl.startsWith("https://")) {
            "OTM_BASE_URL deve usare HTTPS"
        }
        require(instance.isNotBlank()) { "OTM_INSTANCE mancante" }
        require(accessKey.isNotBlank()) { "OTM_ACCESS_KEY mancante" }

        // Keep the public map GET byte-for-byte compatible with the
        // V0.2 request path that is already verified against OpenTreeNap.
        val timestamp = utcTimestamp()
        val requestUrl =
            baseUrl + "/api/v4/instance/" + instance + "/plots" +
                "?offset=" + offset + "&size=" + size +
                "&mobile=1" +
                "&timestamp=" + timestamp + "&access_key=" + accessKey
        val signature = signer.sign("GET", requestUrl)

        val connection =
            (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Signature", signature)
                setRequestProperty(
                    "platform-ver-build",
                    "OpenTreeNap-Android/0.4.1"
                )
            }

        try {
            val status = connection.responseCode
            val stream =
                if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
            val body =
                stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()

            if (status !in 200..299) {
                error(
                    "API HTTP " + status + ": " +
                        body.take(300)
                )
            }

            return parsePlots(body)
        } finally {
            connection.disconnect()
        }
    }

    fun fetchAllTrees(
        pageSize: Int = 1000,
        maxTrees: Int = 5000,
        onProgress: ((List<TreeMarker>) -> Unit)? = null
    ): List<TreeMarker> {
        val all = mutableListOf<TreeMarker>()
        var offset = 0

        while (offset < maxTrees) {
            val page =
                try {
                    fetchTrees(
                        size = pageSize,
                        offset = offset
                    )
                } catch (error: Throwable) {
                    error(
                        "Caricamento pagina offset=$offset fallito: " +
                            (error.message ?: error.javaClass.simpleName)
                    )
                }

            all += page
            onProgress?.invoke(all.toList())

            if (page.size < pageSize) {
                break
            }

            offset += pageSize
        }

        return all
    }

    fun fetchPlot(plotId: Int): TreeMarker {
        val raw = request(
            method = "GET",
            path = "/api/v4/instance/$instance/plots/$plotId"
        )
        return parsePlot(JSONObject(raw))
            ?: error("Risposta albero non valida")
    }

    fun login(username: String, password: String): ApiUser {
        val raw = request(
            method = "GET",
            path = "/api/v4/user",
            username = username,
            password = password
        )
        val obj = JSONObject(raw)
        return ApiUser(
            id = obj.optInt("id", -1),
            username = obj.optString("username").ifBlank { username },
            firstName = obj.optString("first_name").takeIf { it.isNotBlank() },
            lastName = obj.optString("last_name").takeIf { it.isNotBlank() },
            email = obj.optString("email").takeIf { it.isNotBlank() }
        )
    }

    fun fetchInstancePermissions(
        username: String,
        password: String
    ): InstancePermissions {
        val raw = request(
            method = "GET",
            path = "/api/v4/instance/$instance",
            username = username,
            password = password
        )
        val perms = JSONObject(raw).optJSONObject("meta_perms")
        return InstancePermissions(
            canAddTree = perms?.optBoolean("can_add_tree", false) ?: false,
            canEditTree = perms?.optBoolean("can_edit_tree", false) ?: false,
            canEditTreePhoto = perms?.optBoolean("can_edit_tree_photo", false) ?: false
        )
    }

    fun fetchSpecies(): List<SpeciesItem> {
        val raw = request(
            method = "GET",
            path = "/api/v4/instance/$instance/species"
        )
        val array = JSONArray(raw)
        val result = ArrayList<SpeciesItem>(array.length())

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optInt("id", -1)
            if (id < 0) continue

            val commonName = item.optString("common_name").trim()
            val scientificName = item.optString("scientific_name").trim()
            val value = item.optString("value").trim().ifBlank {
                listOf(commonName, scientificName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }

            result += SpeciesItem(
                id = id,
                commonName = commonName,
                scientificName = scientificName,
                value = value
            )
        }

        return result.sortedWith(
            compareBy<SpeciesItem> { it.commonName.lowercase(Locale.ROOT) }
                .thenBy { it.scientificName.lowercase(Locale.ROOT) }
        )
    }

    fun createTree(
        latitude: Double,
        longitude: Double,
        speciesId: Int,
        diameter: Double?,
        height: Double?,
        username: String,
        password: String
    ): TreeMarker {
        val tree = JSONObject()
            .put("species", JSONObject().put("id", speciesId))

        diameter?.let { tree.put("diameter", it) }
        height?.let { tree.put("height", it) }

        val payload = JSONObject()
            .put(
                "plot",
                JSONObject().put(
                    "geom",
                    JSONObject()
                        .put("srid", 4326)
                        .put("x", longitude)
                        .put("y", latitude)
                )
            )
            .put("tree", tree)

        val raw = request(
            method = "POST",
            path = "/api/v4/instance/$instance/plots",
            body = payload.toString().toByteArray(StandardCharsets.UTF_8),
            username = username,
            password = password
        )

        return parsePlot(JSONObject(raw))
            ?: error("Albero creato ma risposta non valida")
    }

    fun updateTree(
        plotId: Int,
        speciesId: Int?,
        diameter: Double?,
        height: Double?,
        username: String,
        password: String
    ): TreeMarker {
        val tree = JSONObject()
        speciesId?.let { tree.put("species", JSONObject().put("id", it)) }
        diameter?.let { tree.put("diameter", it) }
        height?.let { tree.put("height", it) }

        val payload = JSONObject().put("tree", tree)

        val raw = request(
            method = "PUT",
            path = "/api/v4/instance/$instance/plots/$plotId",
            body = payload.toString().toByteArray(StandardCharsets.UTF_8),
            username = username,
            password = password
        )

        return parsePlot(JSONObject(raw))
            ?: error("Albero aggiornato ma risposta non valida")
    }

    private fun request(
        method: String,
        path: String,
        query: LinkedHashMap<String, String> = linkedMapOf(),
        body: ByteArray = ByteArray(0),
        username: String? = null,
        password: String? = null
    ): String {
        require(baseUrl.startsWith("https://")) { "OTM_BASE_URL deve usare HTTPS" }
        require(instance.isNotBlank()) { "OTM_INSTANCE mancante" }
        require(accessKey.isNotBlank()) { "OTM_ACCESS_KEY mancante" }

        val allQuery = LinkedHashMap<String, String>()
        allQuery.putAll(query)
        allQuery["timestamp"] = utcTimestamp()
        allQuery["access_key"] = accessKey

        val requestUrl =
            baseUrl + path + "?" +
                allQuery.entries.joinToString("&") { it.key + "=" + it.value }

        val signature = signer.sign(method, requestUrl, body)

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Signature", signature)
            setRequestProperty("platform-ver-build", "OpenTreeNap-Android/0.4.1")

            if (username != null && password != null) {
                val credentials = "$username:$password"
                val encoded = Base64.encodeToString(
                    credentials.toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
                )
                setRequestProperty("Authorization", "Basic $encoded")
            }

            if (body.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setFixedLengthStreamingMode(body.size)
            }
        }

        try {
            if (body.isNotEmpty()) {
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream
                else connection.errorStream
            val response =
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val detail = response.take(400).ifBlank {
                    when (status) {
                        401 -> "Credenziali non valide o accesso richiesto"
                        403 -> "Non hai i permessi per questa operazione"
                        else -> "Errore HTTP $status"
                    }
                }
                error("API HTTP $status: $detail")
            }

            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePlots(rawJson: String): List<TreeMarker> {
        val array = JSONArray(rawJson)
        val markers = ArrayList<TreeMarker>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parsePlot(item)?.let { markers += it }
        }
        return markers
    }

    private fun parsePlot(item: JSONObject): TreeMarker? {
        val plot = item.optJSONObject("plot") ?: item.optJSONObject("feature") ?: return null
        val geometry = plot.optJSONObject("geom") ?: return null

        val latitude = geometry.optDouble("y", Double.NaN)
        val longitude = geometry.optDouble("x", Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return null
        }

        val tree = item.optJSONObject("tree")
        val hasTree = item.optBoolean("has_tree", tree != null && tree.length() > 0)
        if (!hasTree || tree == null) return null

        val plotId = plot.optInt("id", -1)
        val treeId = tree.optInt("id", -1).takeIf { it >= 0 }
        val species = tree.optJSONObject("species")
        val speciesId = species?.optInt("id", -1)?.takeIf { it >= 0 }
        val commonName = species?.optString("common_name")?.trim().orEmpty()
        val genus = species?.optString("genus")?.trim().orEmpty()
        val speciesName = species?.optString("species")?.trim().orEmpty()
        val explicitScientific = species?.optString("scientific_name")?.trim().orEmpty()
        val scientificName = explicitScientific.ifBlank {
            listOf(genus, speciesName).filter { it.isNotBlank() }.joinToString(" ")
        }
        val address = item.optString("address_full").trim()
        val apiTitle = item.optString("title").trim()

        val title = commonName.takeIf { it.isNotBlank() }
            ?: scientificName.takeIf { it.isNotBlank() }
            ?: apiTitle.takeIf { it.isNotBlank() }
            ?: treeId?.let { "Albero #$it" }
            ?: "Albero"

        val diameter = tree.optNullableDouble("diameter")
        val height = tree.optNullableDouble("height")
        val customId =
            tree.optString("owner_orig_id").trim().takeIf { it.isNotBlank() }
                ?: plot.optString("owner_orig_id").trim().takeIf { it.isNotBlank() }

        return TreeMarker(
            plotId = plotId,
            treeId = treeId,
            latitude = latitude,
            longitude = longitude,
            title = title,
            snippet = buildSnippet(commonName, scientificName, address, plotId),
            commonName = commonName.takeIf { it.isNotBlank() },
            scientificName = scientificName.takeIf { it.isNotBlank() },
            speciesId = speciesId,
            address = address.takeIf { it.isNotBlank() },
            diameter = diameter,
            height = height,
            customId = customId,
            isMonumental = hasMonumentalFlag(item, tree)
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key, Double.NaN).takeIf { it.isFinite() }
    }

    private fun buildSnippet(
        commonName: String,
        scientificName: String,
        address: String,
        plotId: Int
    ): String? {
        val parts = mutableListOf<String>()
        if (commonName.isNotBlank()) parts += commonName
        if (scientificName.isNotBlank() && scientificName != commonName) {
            parts += scientificName
        }
        if (address.isNotBlank()) parts += address
        if (plotId >= 0) parts += "Sito #$plotId"
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun hasMonumentalFlag(item: JSONObject, tree: JSONObject?): Boolean =
        jsonHasMonumentalFlag(item) || (tree?.let { jsonHasMonumentalFlag(it) } == true)

    private fun jsonHasMonumentalFlag(obj: JSONObject, depth: Int = 0): Boolean {
        if (depth > 5) return false
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            val normalized = key.lowercase(Locale.ROOT)
            val relevant = normalized.contains("monument") ||
                normalized.contains("centenar") ||
                normalized.contains("heritage")
            if (relevant && isTruthy(value)) return true
            if (value is JSONObject && jsonHasMonumentalFlag(value, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.trim().lowercase(Locale.ROOT) in setOf(
            "1", "true", "yes", "si", "sì",
            "monumentale", "albero monumentale d'italia",
            "centenario", "centenaria", "heritage"
        )
        is JSONArray -> {
            var found = false
            for (index in 0 until value.length()) {
                if (isTruthy(value.opt(index))) {
                    found = true
                    break
                }
            }
            found
        }
        is JSONObject -> {
            val keys = value.keys()
            var found = false
            while (keys.hasNext()) {
                if (isTruthy(value.opt(keys.next()))) {
                    found = true
                    break
                }
            }
            found
        }
        else -> false
    }

    private fun utcTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
