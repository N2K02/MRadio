package com.mradio.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Radio Browser (radio-browser.info): a free, community-run directory of internet radio
 * stations. Used to discover new Mauritius stations, search worldwide, and find backup
 * streams when all of a station's own streams fail.
 */
object RadioBrowser {
    private const val USER_AGENT = "MRadio/1.0 (Android; github.com/N2K02/MRadio)"
    private val fallbackHosts = listOf(
        "de1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "fi1.api.radio-browser.info",
    )

    @Volatile
    private var hosts: List<String>? = null

    /** All working Mauritius stations, most listened first. */
    suspend fun mauritius(): List<Station> =
        stations("/json/stations/bycountrycodeexact/MU?hidebroken=true&order=clickcount&reverse=true&limit=300")

    suspend fun search(query: String, mauritiusOnly: Boolean): List<Station> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val country = if (mauritiusOnly) "&countrycode=MU" else ""
        return stations("/json/stations/search?name=$q$country&hidebroken=true&order=clickcount&reverse=true&limit=80")
    }

    suspend fun byUuid(uuid: String): Station? =
        stations("/json/stations/byuuid/${URLEncoder.encode(uuid, "UTF-8")}").firstOrNull()

    /** Other streams for the same station, best first. Never throws. */
    suspend fun alternates(station: Station): List<String> {
        val key = normalizeName(station.name)
        if (key.length < 3) return emptyList()
        val mu = station.country.equals("MU", ignoreCase = true)
        return runCatching { search(station.name, mauritiusOnly = mu) }.getOrDefault(emptyList())
            .filter {
                val k = normalizeName(it.name)
                k.length >= 3 && (k == key || k.contains(key) || key.contains(k))
            }
            .flatMap { it.streams }
            .distinct()
    }

    private suspend fun stations(path: String): List<Station> = parse(get(path))

    private class Raw(
        val uuid: String,
        val name: String,
        val url: String,
        val favicon: String,
        val tags: String,
        val country: String,
        val score: Int,
    )

    /**
     * The directory often lists one station several times with different stream links.
     * Those are merged into one station whose streams are ordered best first:
     * last check OK, then higher bitrate, then HTTPS.
     */
    private fun parse(a: JSONArray): List<Station> {
        val raws = (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
            val name = o.optString("name").trim()
            if (url.isEmpty() || name.isEmpty() || !url.startsWith("http")) return@mapNotNull null
            val score = (if (o.optInt("lastcheckok") == 1) 1000 else 0) +
                o.optInt("bitrate").coerceIn(0, 320) +
                (if (url.startsWith("https")) 40 else 0)
            Raw(
                uuid = o.optString("stationuuid"),
                name = name,
                url = url,
                favicon = o.optString("favicon").trim(),
                tags = o.optString("tags"),
                country = o.optString("countrycode"),
                score = score,
            )
        }
        return raws.groupBy { normalizeName(it.name).ifEmpty { it.uuid } }.values.map { group ->
            val sorted = group.sortedByDescending { it.score }
            val best = sorted.first()
            Station(
                id = "rb:${best.uuid}",
                name = best.name,
                streams = sorted.map { it.url }.distinct(),
                tags = sorted.firstOrNull { it.tags.isNotBlank() }?.tags.orEmpty(),
                favicon = sorted.firstOrNull { it.favicon.startsWith("http") }?.favicon,
                country = best.country.ifBlank { "MU" },
            )
        }
    }

    private suspend fun get(path: String): JSONArray = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (host in servers()) {
            try {
                return@withContext JSONArray(fetch("https://$host$path"))
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("No Radio Browser server reachable")
    }

    /** The directory runs on several mirrors; ask for the current list once, else use known ones. */
    private fun servers(): List<String> {
        hosts?.let { return it }
        val found = runCatching {
            val a = JSONArray(fetch("https://all.api.radio-browser.info/json/servers"))
            (0 until a.length()).map { a.getJSONObject(it).optString("name") }
                .filter { it.isNotBlank() }.distinct().shuffled()
        }.getOrDefault(emptyList())
        return (found + fallbackHosts).distinct().also { hosts = it }
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", USER_AGENT)
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} from $url")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
