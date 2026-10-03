package org.opentreenap.mobile.api

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.opentreenap.mobile.model.ApiUser
import org.opentreenap.mobile.model.InstancePermissions
import org.opentreenap.mobile.model.ReverseGeocodeResult
import org.opentreenap.mobile.model.SpeciesItem
import org.opentreenap.mobile.model.TreeExtraField
import org.opentreenap.mobile.model.TreeMarker
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
                    "OpenTreeNap-Android/0.7.2"
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

    fun reverseGeocode(
        latitude: Double,
        longitude: Double
    ): ReverseGeocodeResult? {
        val location =
            URLEncoder.encode(
                "${longitude},${latitude}",
                "UTF-8"
            )

        val url =
            URL(
                "https://geocode.arcgis.com/arcgis/rest/services/" +
                    "World/GeocodeServer/reverseGeocode" +
                    "?location=" + location +
                    "&distance=200&outSR=4326&f=json&forStorage=true"
            )

        val connection =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
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

            if (status !in 200..299 || body.isBlank()) {
                return null
            }

            val address =
                JSONObject(body)
                    .optJSONObject("address")
                    ?: return null

            fun firstValue(vararg keys: String): String? =
                keys.asSequence()
                    .map {
                        address.optString(it)
                            .trim()
                    }
                    .firstOrNull {
                        it.isNotBlank()
                    }

            val street =
                firstValue(
                    "Address",
                    "ShortLabel"
                )
            val city =
                firstValue(
                    "City",
                    "District",
                    "Subregion"
                )
            val postal =
                firstValue(
                    "Postal",
                    "PostalExt"
                )
            val formatted =
                firstValue(
                    "LongLabel",
                    "Match_addr"
                )
                    ?: listOfNotNull(
                        street,
                        city,
                        postal
                    )
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.joinToString(", ")

            if (
                street == null &&
                city == null &&
                postal == null &&
                formatted == null
            ) {
                return null
            }

            return ReverseGeocodeResult(
                street = street,
                city = city,
                postalCode = postal,
                formatted = formatted
            )
        } finally {
            connection.disconnect()
        }
    }

    fun createTree(
        latitude: Double,
        longitude: Double,
        speciesId: Int,
        diameter: Double?,
        height: Double?,
        addressStreet: String? = null,
        addressCity: String? = null,
        addressZip: String? = null,
        username: String,
        password: String
    ): TreeMarker {
        val tree = JSONObject()
            .put("species", JSONObject().put("id", speciesId))

        diameter?.let { tree.put("diameter", it) }
        height?.let { tree.put("height", it) }

        val plot =
            JSONObject().put(
                "geom",
                JSONObject()
                    .put("srid", 4326)
                    .put("x", longitude)
                    .put("y", latitude)
            )

        addressStreet
            ?.takeIf { it.isNotBlank() }
            ?.let {
                plot.put(
                    "address_street",
                    it
                )
            }
        addressCity
            ?.takeIf { it.isNotBlank() }
            ?.let {
                plot.put(
                    "address_city",
                    it
                )
            }
        addressZip
            ?.takeIf { it.isNotBlank() }
            ?.let {
                plot.put(
                    "address_zip",
                    it
                )
            }

        val payload =
            JSONObject()
                .put("plot", plot)
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

    fun updateMeasurements(
        plotId: Int,
        height: Double?,
        circumferenceCm: Double?,
        heightMethod: String?,
        circumferenceMethod: String?,
        heightStatus: String?,
        circumferenceStatus: String?,
        quality: String?,
        username: String,
        password: String
    ): TreeMarker {
        // Persist the core dendrometric values first using the same path as
        // the already-tested tree editor. This way a problem in a new UDF
        // cannot roll back height/DBH together with the measurement metadata.
        if (height != null || circumferenceCm != null) {
            val coreTree = JSONObject()

            height?.let {
                coreTree.put("height", it)
            }

            circumferenceCm?.let {
                coreTree.put(
                    "diameter",
                    it / Math.PI
                )
            }

            val corePayload =
                JSONObject()
                    .put("tree", coreTree)

            request(
                method = "PUT",
                path = "/api/v4/instance/$instance/plots/$plotId",
                body =
                    corePayload.toString()
                        .toByteArray(
                            StandardCharsets.UTF_8
                        ),
                username = username,
                password = password
            )
        }

        val metadataTree = JSONObject()

        circumferenceCm?.let {
            metadataTree.put(
                "udf:Circonferenza 1,30 m",
                it
            )
        }

        heightMethod
            ?.takeIf { it.isNotBlank() }
            ?.let {
                metadataTree.put(
                    "udf:Metodo misura altezza",
                    it
                )
            }

        circumferenceMethod
            ?.takeIf { it.isNotBlank() }
            ?.let {
                metadataTree.put(
                    "udf:Metodo misura circonferenza",
                    it
                )
            }

        heightStatus
            ?.takeIf { it.isNotBlank() }
            ?.let {
                metadataTree.put(
                    "udf:Stato misura altezza",
                    it
                )
            }

        circumferenceStatus
            ?.takeIf { it.isNotBlank() }
            ?.let {
                metadataTree.put(
                    "udf:Stato misura circonferenza",
                    it
                )
            }

        quality
            ?.takeIf { it.isNotBlank() }
            ?.let {
                metadataTree.put(
                    "udf:Qualità misura",
                    it
                )
            }

        if (metadataTree.length() > 0) {
            metadataTree.put(
                "udf:Data rilievo",
                localDate()
            )

            val metadataPayload =
                JSONObject()
                    .put("tree", metadataTree)

            try {
                request(
                    method = "PUT",
                    path = "/api/v4/instance/$instance/plots/$plotId",
                    body =
                        metadataPayload.toString()
                            .toByteArray(
                                StandardCharsets.UTF_8
                            ),
                    username = username,
                    password = password
                )
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "Altezza/DBH salvati, ma i metadati della misura non sono stati salvati: " +
                        (
                            error.message
                                ?: error.javaClass.simpleName
                            ),
                    error
                )
            }
        }

        // Always read back from OTN before showing success.
        val saved =
            fetchPlot(plotId)

        height?.let { expected ->
            val actual =
                saved.height
                    ?: error(
                        "Il server ha risposto senza errore, ma l'altezza non risulta salvata."
                    )

            if (kotlin.math.abs(actual - expected) > 0.05) {
                error(
                    "Verifica salvataggio fallita: altezza richiesta " +
                        String.format(Locale.ITALY, "%.2f", expected) +
                        " m, valore restituito " +
                        String.format(Locale.ITALY, "%.2f", actual) +
                        " m."
                )
            }
        }

        circumferenceCm?.let { expected ->
            val actual =
                saved.extraFields
                    .firstOrNull {
                        it.label.equals(
                            "Circonferenza 1,30 m",
                            ignoreCase = true
                        )
                    }
                    ?.value
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                    ?: error(
                        "DBH salvato, ma la circonferenza originale non risulta nei dati OpenTreeNap."
                    )

            if (kotlin.math.abs(actual - expected) > 0.1) {
                error(
                    "Verifica salvataggio fallita: circonferenza richiesta " +
                        String.format(Locale.ITALY, "%.1f", expected) +
                        " cm, valore restituito " +
                        String.format(Locale.ITALY, "%.1f", actual) +
                        " cm."
                )
            }
        }

        return saved
    }

    fun updateTree(
        plotId: Int,
        speciesId: Int?,
        diameter: Double?,
        height: Double?,
        addressStreet: String? = null,
        addressCity: String? = null,
        addressZip: String? = null,
        username: String,
        password: String
    ): TreeMarker {
        val tree = JSONObject()
        speciesId?.let { tree.put("species", JSONObject().put("id", it)) }
        diameter?.let { tree.put("diameter", it) }
        height?.let { tree.put("height", it) }

        val plot = JSONObject()
        addressStreet
            ?.takeIf { it.isNotBlank() }
            ?.let { plot.put("address_street", it) }
        addressCity
            ?.takeIf { it.isNotBlank() }
            ?.let { plot.put("address_city", it) }
        addressZip
            ?.takeIf { it.isNotBlank() }
            ?.let { plot.put("address_zip", it) }

        val payload =
            JSONObject().put("tree", tree)
        if (plot.length() > 0) {
            payload.put("plot", plot)
        }

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
            setRequestProperty("platform-ver-build", "OpenTreeNap-Android/0.7.2")

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
        val addressStreet =
            plot.optString("address_street")
                .trim()
                .takeIf { it.isNotBlank() }
        val addressCity =
            plot.optString("address_city")
                .trim()
                .takeIf { it.isNotBlank() }
        val addressZip =
            plot.optString("address_zip")
                .trim()
                .takeIf { it.isNotBlank() }
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

        val firstPhoto =
            item.optJSONArray("photos")
                ?.optJSONObject(0)

        val photoUrl =
            firstPhoto
                ?.let { photo ->
                    listOf(
                        photo.optString("absolute_image"),
                        photo.optString("image"),
                        photo.optString("thumbnail")
                    )
                        .firstOrNull {
                            it.isNotBlank()
                        }
                }
                ?.let { absoluteUrl(it) }

        val mobileMeta =
            item.optJSONObject("mobile_meta")

        val updatedAt =
            mobileMeta
                ?.optString("updated_at")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val updatedBy =
            mobileMeta
                ?.optJSONObject("updated_by")
                ?.optString("username")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val detailUrl =
            mobileMeta
                ?.optString("detail_url")
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val extraFields =
            parseExtraFields(mobileMeta)

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
            addressStreet = addressStreet,
            addressCity = addressCity,
            addressZip = addressZip,
            diameter = diameter,
            height = height,
            customId = customId,
            photoUrl = photoUrl,
            isMonumental = hasMonumentalFlag(item, tree),
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            detailUrl = detailUrl,
            extraFields = extraFields
        )
    }

    private fun parseExtraFields(
        mobileMeta: JSONObject?
    ): List<TreeExtraField> {
        if (mobileMeta == null) {
            return emptyList()
        }

        val values =
            LinkedHashMap<String, TreeExtraField>()

        fun appendObject(
            obj: JSONObject?
        ) {
            if (obj == null) return

            val keys = obj.keys()

            while (keys.hasNext()) {
                val rawKey =
                    keys.next().trim()

                if (
                    rawKey.isBlank() ||
                    rawKey.startsWith("_") ||
                    rawKey.lowercase(Locale.ROOT) in
                    setOf(
                        "monumentale",
                        "monumental"
                    )
                ) {
                    continue
                }

                val value =
                    readableJsonValue(
                        obj.opt(rawKey)
                    )
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: continue

                val dedupeKey =
                    rawKey.lowercase(Locale.ROOT)

                if (!values.containsKey(dedupeKey)) {
                    values[dedupeKey] =
                        TreeExtraField(
                            label =
                                formatUdfLabel(rawKey),
                            value = value
                        )
                }
            }
        }

        // Tree values are more specific and win if the same label exists on
        // both tree and plot.
        appendObject(
            mobileMeta.optJSONObject(
                "tree_udfs"
            )
        )
        appendObject(
            mobileMeta.optJSONObject(
                "plot_udfs"
            )
        )

        return values.values
            .sortedWith(
                compareBy<TreeExtraField> {
                    udfPriority(it.label)
                }.thenBy {
                    it.label.lowercase(
                        Locale.ROOT
                    )
                }
            )
    }

    private fun readableJsonValue(
        value: Any?
    ): String? =
        when (value) {
            null,
            JSONObject.NULL -> null

            is Boolean ->
                if (value) "Sì" else "No"

            is Number ->
                value.toString()

            is String ->
                value.trim()
                    .takeIf {
                        it.isNotBlank() &&
                            !it.equals(
                                "null",
                                ignoreCase = true
                            )
                    }

            is JSONArray ->
                buildList {
                    for (
                        index in
                        0 until value.length()
                    ) {
                        readableJsonValue(
                            value.opt(index)
                        )?.let { add(it) }
                    }
                }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")

            else ->
                value.toString()
                    .trim()
                    .takeIf {
                        it.isNotBlank()
                    }
        }

    private fun formatUdfLabel(
        raw: String
    ): String {
        val cleaned =
            raw.replace('_', ' ')
                .replace(
                    "\\s+".toRegex(),
                    " "
                )
                .trim()

        if (
            cleaned.equals(
                "masaf",
                ignoreCase = true
            )
        ) {
            return "MASAF"
        }

        return cleaned.replaceFirstChar {
            if (it.isLowerCase()) {
                it.titlecase(Locale.ITALY)
            } else {
                it.toString()
            }
        }
    }

    private fun udfPriority(
        label: String
    ): Int {
        val value =
            label.lowercase(Locale.ROOT)

        return when {
            "masaf" in value -> 0
            "centenar" in value -> 1
            "circonfer" in value -> 2
            "anno" in value -> 3
            "stato" in value -> 4
            else -> 10
        }
    }

    private fun absoluteUrl(
        raw: String
    ): String {
        val value = raw.trim()

        return when {
            value.startsWith("https://") ||
                value.startsWith("http://") ->
                value

            value.startsWith("/") ->
                baseUrl + value

            else ->
                baseUrl + "/" + value
        }
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

    private fun localDate(): String {
        val formatter =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            )
        formatter.timeZone =
            TimeZone.getDefault()

        return formatter.format(
            Date()
        )
    }

    private fun utcTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
}
