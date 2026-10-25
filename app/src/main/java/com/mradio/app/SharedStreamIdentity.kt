package com.mradio.app

import java.net.URI
import java.util.Locale

/**
 * Stable stream identity for recognising already-saved links. Preserve paths and
 * queries (including authentication tokens); only normalise URL casing, default
 * ports and fragments, which are not sent to streaming servers.
 */
internal object SharedStreamIdentity {
    fun normalized(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (uri.rawUserInfo != null) return null
        val port = uri.port.takeUnless { it == -1 || (scheme == "http" && it == 80) ||
            (scheme == "https" && it == 443) }
        return buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(uri.rawPath.orEmpty().ifBlank { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        }
    }

    fun matches(a: String, b: String): Boolean {
        val left = normalized(a) ?: return false
        return left == normalized(b)
    }

    fun match(stations: List<Station>, url: String): Station? {
        val key = normalized(url) ?: return null
        return stations.firstOrNull { station ->
            station.streams.any { normalized(it) == key }
        }
    }
}
