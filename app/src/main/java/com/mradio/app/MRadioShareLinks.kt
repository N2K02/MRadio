package com.mradio.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Opaque, versioned, URL-safe stream tokens for WhatsApp-friendly HTTPS App Links.
 * The website passes the token through unchanged; only the Android app implements decoding.
 * This is compression/encoding, NOT encryption: anyone with this format can decode it.
 */
internal object MRadioShareLinks {
    private const val URL = "https://n2k02.github.io/mradio/r/?d="
    private const val VERSION = "1"
    private const val MAX_URL_BYTES = 4096
    private const val MAX_TOKEN_LENGTH = 4096

    fun forStream(streamUrl: String): String {
        require(isValidStreamUrl(streamUrl)) { "Expected an HTTP(S) radio stream URL" }
        val input = streamUrl.toByteArray(Charsets.UTF_8)
        require(input.size <= MAX_URL_BYTES)
        val compressed = ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION, true)).use { deflater ->
                deflater.write(input)
            }
            output.toByteArray()
        }
        return URL + VERSION + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun decodeToken(token: String?): String? {
        if (token == null || token.length !in 2..MAX_TOKEN_LENGTH ||
            !token.startsWith(VERSION) || !token.drop(1).all { it.isLetterOrDigit() || it == '-' || it == '_' }
        ) return null
        val compressed = runCatching {
            Base64.getUrlDecoder().decode(token.substring(VERSION.length))
        }.getOrNull() ?: return null
        if (compressed.isEmpty() || compressed.size > MAX_URL_BYTES) return null

        val plain = runCatching {
            InflaterInputStream(ByteArrayInputStream(compressed), Inflater(true)).use { inflater ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(512)
                while (true) {
                    val count = inflater.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > MAX_URL_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }.getOrNull() ?: return null
        val url = String(plain, Charsets.UTF_8)
        return url.takeIf(::isValidStreamUrl)
    }

    fun isSharePage(candidate: String?): Boolean {
        val uri = runCatching { URI(candidate ?: return false) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host.equals("n2k02.github.io", true) &&
            uri.path?.trimEnd('/') == "/mradio/r"
    }

    private fun isValidStreamUrl(url: String): Boolean {
        if (url.isEmpty() || url.length > MAX_URL_BYTES || url.any { it.isISOControl() }) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }
}
