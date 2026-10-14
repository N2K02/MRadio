package com.mradio.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater

/**
 * Streema (streema.com): the directory behind the Simple Radio app. Its website reads
 * stations from a small JSON API; each station's stream link comes zlib-compressed and
 * base64-encoded ("encoded_stream_url"), which is decoded here.
 *
 * Search and browse results come without links, so a link is looked up (and checked)
 * per station only when needed.
 */
object Streema {
    private const val BASE = "https://streema.com/api"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"

    /** slug -> decoded stream link ("" when the station has none). */
    private val links = ConcurrentHashMap<String, String>()

    /** slug -> details, for the player screen. */
    private val detailCache = ConcurrentHashMap<String, Details>()

    /** Country code -> Streema's country name in its URLs ("US" -> "United_States"). */
    @Volatile
    private var countrySlugs: Map<String, String>? = null

    class Details(
        val slug: String,
        val name: String,
        val stream: String?,
        val logo: String?,
        val frequency: String,
        val slogan: String,
        val description: String,
        val genres: List<String>,
        val language: String,
        val location: String,
        val countryCode: String,
        val countryName: String,
        val website: String,
    )

    /** Stations matching [query], worldwide, or in one country ([country] is a code like "FR"). */
    suspend fun search(query: String, country: String?): List<Station> {
        val q = enc(query.trim())
        val path = if (country == null) {
            "/radios/search?q=$q&limit=60"
        } else {
            "/radios/browse?country=${enc(slugFor(country))}&q=$q&limit=60"
        }
        return parseList(getObject(path))
    }

    /** Most popular stations worldwide, or in one country. */
    suspend fun popular(country: String?): List<Station> {
        val path = if (country == null) "/radios/browse?limit=80" else "/radios/browse?country=${enc(slugFor(country))}&limit=80"
        return parseList(getObject(path))
    }

    /** The station's details and decoded stream link. Null if Streema doesn't have it. */
    suspend fun details(slug: String): Details? {
        detailCache[slug]?.let { return it }
        val o = runCatching { getObject("/radios/${enc(slug)}") }.getOrNull() ?: return null
        if (o.optString("slug").isBlank()) return null
        val stream = decode(o.optString("encoded_stream_url"))
            ?: o.optString("external_url").takeIf { it.startsWith("http") }
        links[slug] = stream.orEmpty()
        val d = Details(
            slug = slug,
            name = o.optString("name"),
            stream = stream,
            logo = o.optString("logo_url").takeIf { it.startsWith("http") },
            frequency = o.optString("frequency_label"),
            slogan = o.optString("slogan"),
            description = o.optString("description"),
            genres = o.optJSONArray("genres").strings(),
            language = o.optString("language"),
            location = o.optString("location_line"),
            countryCode = o.optString("country_code"),
            countryName = o.optString("country_name"),
            website = o.optString("website").takeIf { it.startsWith("http") }.orEmpty(),
        )
        detailCache[slug] = d
        return d
    }

    /** The decoded stream link for a Streema station, or null. Never throws. */
    suspend fun streamUrl(slug: String): String? {
        links[slug]?.let { return it.ifEmpty { null } }
        return details(slug)?.stream
    }

    /** The Streema page for the same station (matched by name), if any. Never throws. */
    suspend fun findSlug(station: Station): String? {
        station.streemaSlug?.let { return it }
        val key = normalizeName(station.name)
        if (key.length < 3) return null
        val found = runCatching { search(station.name, null) }.getOrDefault(emptyList())
        return found.firstOrNull { normalizeName(it.name) == key }?.streemaSlug
            ?: found.firstOrNull {
                val k = normalizeName(it.name)
                k.length >= 3 && (k.contains(key) || key.contains(k)) &&
                    it.country.equals(station.country, ignoreCase = true)
            }?.streemaSlug
    }

    /** Streema's link for the same station, as a fallback. Never throws. */
    suspend fun alternates(station: Station): List<String> =
        listOfNotNull(findSlug(station)?.let { streamUrl(it) })

    /**
     * Checks that a link really plays: it answers quickly with audio (or a playlist, which is
     * opened to get the real link). Returns the playable link, or null. Never throws.
     */
    suspend fun working(url: String): String? = withContext(Dispatchers.IO) {
        runCatching { check(url, depth = 0) }.getOrNull()
    }

    private fun check(start: String, depth: Int): String? {
        var url = start
        repeat(4) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.setRequestProperty("User-Agent", "MRadio/1.0 (Android)")
                c.setRequestProperty("Icy-MetaData", "0")
                val code = c.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), c.getHeaderField("Location") ?: return null).toString()
                    return@repeat
                }
                if (code != 200 && code != 206) return null
                val type = c.contentType.orEmpty().lowercase()
                val path = URL(url).path.lowercase()
                val isPlaylist = type.contains("scpls") || path.endsWith(".pls") ||
                    (path.endsWith(".m3u") && !path.endsWith(".m3u8")) || type == "audio/x-mpegurl" && !path.endsWith(".m3u8")
                if (isPlaylist) {
                    if (depth > 0) return null
                    val body = c.inputStream.bufferedReader().use { r -> r.readText().take(4000) }
                    val inner = Regex("""https?://[^\s"'<>]+""").find(body)?.value ?: return null
                    return check(inner, depth + 1)
                }
                if (type.startsWith("text/html")) return null
                // Read a few bytes: a dead server often accepts the request but sends nothing.
                val got = c.inputStream.use { it.read(ByteArray(512)) }
                return if (got > 0) url else null
            } finally {
                c.disconnect()
            }
        }
        return null
    }

    // ---------------------------------------------------------------- countries

    /** Every country Streema lists: code -> its name in Streema's URLs. Never throws. */
    private suspend fun countrySlugs(): Map<String, String> {
        countrySlugs?.let { return it }
        val map = runCatching {
            val regions = getObject("/browse/regions").optJSONArray("regions") ?: JSONArray()
            buildMap {
                for (i in 0 until regions.length()) {
                    val countries = regions.getJSONObject(i).optJSONArray("countries") ?: continue
                    for (j in 0 until countries.length()) {
                        val c = countries.getJSONObject(j)
                        val code = c.optString("code").takeIf { it.length == 2 } ?: continue
                        putIfAbsent(code.uppercase(), c.optString("slug"))
                    }
                }
            }
        }.getOrDefault(emptyMap())
        if (map.isNotEmpty()) countrySlugs = map
        return map
    }

    private suspend fun slugFor(code: String): String =
        countrySlugs()[code.uppercase()]
            ?: Locale("", code.uppercase()).getDisplayCountry(Locale.ENGLISH).replace(' ', '_')

    // ---------------------------------------------------------------- parsing

    private fun parseList(o: JSONObject): List<Station> {
        val a = o.optJSONArray("radios") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val r = a.getJSONObject(i)
            val slug = r.optString("slug").ifBlank { return@mapNotNull null }
            val name = r.optString("name").trim().ifBlank { return@mapNotNull null }
            Station(
                id = "st:$slug",
                name = name,
                streams = emptyList(),
                tags = r.optJSONArray("genres").strings().joinToString(", ").lowercase(),
                favicon = r.optString("imageSrc").takeIf { it.startsWith("http") },
                country = r.optString("countryCode").takeIf { it != "null" }.orEmpty(),
                streemaSlug = slug,
                location = r.optString("locationLine").takeIf { it != "null" }.orEmpty(),
            )
        }
    }

    /** "eJzL…" -> "https://…": base64, then zlib. */
    private fun decode(encoded: String): String? = runCatching {
        if (encoded.isBlank()) return null
        val inflater = Inflater()
        inflater.setInput(Base64.getDecoder().decode(encoded))
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        out.toString("UTF-8").trim().takeIf { it.startsWith("http") }
    }.getOrNull()

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun getObject(path: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} from Streema")
            JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }
}
