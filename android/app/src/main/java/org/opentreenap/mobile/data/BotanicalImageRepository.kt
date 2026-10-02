package org.opentreenap.mobile.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class BotanicalImageEntry(
    val scientificName: String,
    val imageUrl: String,
    val pageUrl: String?
)

class BotanicalImageRepository(
    context: Context,
    manifestUrl: String
) {
    private val manifestUrl =
        manifestUrl.trim()

    private val cacheFile =
        File(
            context.filesDir,
            "botanical-images-cache-v2.json"
        )

    private val preferences =
        context.getSharedPreferences(
            "botanical-images",
            Context.MODE_PRIVATE
        )

    @Volatile
    private var entries:
        Map<String, BotanicalImageEntry> =
        loadCached()

    fun imageUrl(
        scientificName: String?
    ): String? =
        entry(scientificName)?.imageUrl

    fun pageUrl(
        scientificName: String?
    ): String? =
        entry(scientificName)?.pageUrl

    fun refresh(): Int {
        require(
            manifestUrl.startsWith("https://")
        ) {
            "Il manifest botanico deve usare HTTPS"
        }

        val connection =
            (
                URL(manifestUrl)
                    .openConnection()
                    as HttpURLConnection
                ).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty(
                    "Accept",
                    "application/json"
                )

                preferences
                    .getString("etag", null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        setRequestProperty(
                            "If-None-Match",
                            it
                        )
                    }

                setRequestProperty(
                    "User-Agent",
                    "OpenTreeNap-Android/0.5.1"
                )
            }

        try {
            val status =
                connection.responseCode

            if (
                status ==
                HttpURLConnection.HTTP_NOT_MODIFIED
            ) {
                return entries.size
            }

            if (status !in 200..299) {
                error(
                    "Manifest botanico HTTP $status"
                )
            }

            val raw =
                connection.inputStream
                    .bufferedReader(
                        Charsets.UTF_8
                    )
                    .use { it.readText() }

            val parsed = parse(raw)

            if (parsed.isNotEmpty()) {
                entries = parsed
                saveCache(raw)

                connection
                    .getHeaderField("ETag")
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        preferences
                            .edit()
                            .putString(
                                "etag",
                                it
                            )
                            .apply()
                    }
            }

            return parsed.size
        } finally {
            connection.disconnect()
        }
    }

    private fun entry(
        scientificName: String?
    ): BotanicalImageEntry? =
        scientificName
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.let { entries[it] }

    private fun loadCached():
        Map<String, BotanicalImageEntry> =
        runCatching {
            if (!cacheFile.exists()) {
                emptyMap()
            } else {
                parse(
                    cacheFile.readText()
                )
            }
        }.getOrDefault(
            emptyMap()
        )

    private fun saveCache(
        raw: String
    ) {
        runCatching {
            val tmp =
                File(
                    cacheFile.parentFile,
                    cacheFile.name + ".tmp"
                )

            tmp.writeText(raw)

            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(raw)
                tmp.delete()
            }
        }
    }

    private fun parse(
        raw: String
    ): Map<String, BotanicalImageEntry> {
        val root = JSONObject(raw)
        val species =
            root.optJSONObject("species")
                ?: return emptyMap()

        val result =
            LinkedHashMap<
                String,
                BotanicalImageEntry
                >()

        val keys = species.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val item =
                species.optJSONObject(key)
                    ?: continue

            val scientificName =
                item.optString(
                    "scientific_name",
                    key
                ).trim()

            val imageUrl =
                item.optString(
                    "representative_image"
                )
                    .trim()
                    .ifBlank {
                        item.optString(
                            "image_url"
                        ).trim()
                    }

            if (
                scientificName.isBlank() ||
                imageUrl.isBlank()
            ) {
                continue
            }

            result[
                scientificName
                    .lowercase(
                        Locale.ROOT
                    )
            ] =
                BotanicalImageEntry(
                    scientificName =
                        scientificName,
                    imageUrl = imageUrl,
                    pageUrl =
                        item.optString(
                            "page_url"
                        )
                            .trim()
                            .takeIf {
                                it.isNotBlank()
                            }
                )
        }

        return result
    }
}
