package org.opentreenap.mobile.api

import android.util.Base64
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 request signing compatible with the historical OpenTreeMap API v4
 * and the legacy otm-android RequestSignature implementation.
 */
internal class HmacSigner(
    private val secretKey: String
) {
    fun sign(
        verb: String,
        url: String,
        body: ByteArray = ByteArray(0)
    ): String {
        val uri = URI(url)
        val hostWithPort = uri.authority
        val path = uri.path

        val queryParts = (uri.query ?: "")
            .split("&")
            .filter { it.isNotBlank() }
            .sorted()
            .map { part ->
                val separator = part.indexOf('=')
                if (separator < 0) {
                    part
                } else {
                    val key = part.substring(0, separator)
                    val value = part.substring(separator + 1)
                    key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                }
            }

        val canonicalQuery = queryParts.joinToString("&")
        val encodedBody = Base64.encodeToString(body, Base64.NO_WRAP)
        val payload = verb + "\n" + hostWithPort + "\n" + path + "\n" +
            canonicalQuery + encodedBody

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(
            mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
    }
}
