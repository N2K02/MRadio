package com.mradio.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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

    /** Directory entries never shown in the app (normalized names). */
    private val hidden = setOf("abdulbasitabdulsamad")

    @Volatile
    private var hosts: List<String>? = null

    /** All working Mauritius stations, most listened first. */
    suspend fun mauritius(): List<Station> = popular("MU")

    /** Working stations most listened to, in one country ([country] like "FR") or worldwide (null). */
    suspend fun popular(country: String?): List<Station> =
        if (country == null) {
            stations("/json/stations/topclick/100?hidebroken=true")
        } else {
            val limit = if (country.equals("MU", ignoreCase = true)) 300 else 120
            stations("/json/stations/bycountrycodeexact/${URLEncoder.encode(country.uppercase(), "UTF-8")}?hidebroken=true&order=clickcount&reverse=true&limit=$limit")
        }

    /** Working stations named like [query], in one country ([country] like "MU") or worldwide (null). */
    suspend fun search(query: String, country: String?): List<Station> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val filter = if (country != null) "&countrycode=${country.uppercase()}" else ""
        return stations("/json/stations/search?name=$q$filter&hidebroken=true&order=clickcount&reverse=true&limit=80")
    }

    /** The directory's raw entry for the same station (bitrate, codec, language, homepage…). Never throws. */
    suspend fun details(station: Station): JSONObject? {
        val key = normalizeName(station.name)
        if (key.length < 3) return null
        val country = station.country.takeIf { it.length == 2 }
        val q = URLEncoder.encode(station.name, "UTF-8")
        val filter = if (country != null) "&countrycode=$country" else ""
        val a = runCatching { get("/json/stations/search?name=$q$filter&order=clickcount&reverse=true&limit=20") }.getOrNull()
            ?: return null
        return (0 until a.length()).map { a.getJSONObject(it) }
            .firstOrNull { normalizeName(it.optString("name")) == key }
    }

    suspend fun byUuid(uuid: String): Station? =
        stations("/json/stations/byuuid/${URLEncoder.encode(uuid, "UTF-8")}").firstOrNull()

    /** Other streams for the same station, best first. Never throws. */
    suspend fun alternates(station: Station): List<String> =
        sameStation(station).flatMap { it.streams }.distinct()

    /** A logo for the same station, if the directory has one. Never throws. */
    suspend fun logo(station: Station): String? =
        sameStation(station).firstNotNullOfOrNull { it.favicon }

    /**
     * Directory entries for the same station. Searches by the full name, then by the name
     * without generic words ("Tamizen Radio" is listed as "Tamizen"). Never throws.
     */
    private suspend fun sameStation(station: Station): List<Station> {
        val key = normalizeName(station.name)
        if (key.length < 3) return emptyList()
        val country = station.country.takeIf { it.length == 2 }
        val generic = setOf("radio", "fm", "mauritius", "maurice", "mbc", "the")
        val short = station.name.split(Regex("\\s+")).filter { it.lowercase() !in generic }.joinToString(" ")
        for (query in listOf(station.name, short).filter { it.length >= 3 }.distinct()) {
            val found = runCatching { search(query, country) }.getOrDefault(emptyList())
                .filter {
                    val k = normalizeName(it.name)
                    k.length >= 3 && (k == key || k.contains(key) || key.contains(k))
                }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private suspend fun stations(path: String): List<Station> = parse(get(path))

    private class Raw(
        val uuid: String,
        val name: String,
        val url: String,
        val favicon: String,
        val tags: String,
        val country: String,
        val state: String,
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
            if (hidden.any { normalizeName(name).contains(it) }) return@mapNotNull null
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
                state = o.optString("state"),
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
                country = best.country,
                location = listOf(best.state, countryName(best.country)).filter { it.isNotBlank() }.joinToString(", "),
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
