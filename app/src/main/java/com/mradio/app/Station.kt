package com.mradio.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * A radio station. [streams] is ordered best first; the player falls back to the next
 * one when a stream fails (see PlaybackService).
 */
data class Station(
    val id: String,
    val name: String,
    val streams: List<String>,
    val tags: String = "",
    val favicon: String? = null,
    val country: String = "MU",
    val builtIn: Boolean = false,
    /** Streema page name (e.g. "Top_FM_8"); its stream link is looked up when needed. */
    val streemaSlug: String? = null,
    /** "Port Louis, Mauritius" when known. */
    val location: String = "",
) {
    /** Short line under the name: up to three tags, or the country. */
    val subtitle: String
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
            .joinToString(" · ")
            .ifEmpty { location.ifEmpty { countryName(country) } }

    /** The same station with [other]'s links added as fallbacks (this station's links stay first). */
    fun mergedWith(other: Station): Station = copy(
        streams = (streams + other.streams).distinct(),
        tags = tags.ifBlank { other.tags },
        favicon = favicon ?: other.favicon,
        streemaSlug = streemaSlug ?: other.streemaSlug,
        location = location.ifBlank { other.location },
        country = if (country.isBlank()) other.country else country,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("streams", JSONArray(streams))
        .put("tags", tags)
        .put("favicon", favicon ?: "")
        .put("country", country)
        .put("streema", streemaSlug ?: "")
        .put("location", location)

    companion object {
        fun fromJson(o: JSONObject): Station {
            val a = o.optJSONArray("streams") ?: JSONArray()
            return Station(
                id = o.getString("id"),
                name = o.getString("name"),
                streams = (0 until a.length()).map { a.getString(it) },
                tags = o.optString("tags"),
                favicon = o.optString("favicon").takeIf { it.startsWith("http") },
                country = o.optString("country", "MU"),
                streemaSlug = o.optString("streema").ifBlank { null },
                location = o.optString("location"),
            )
        }
    }
}

/** "MBC Kool FM" -> "mbckoolfm": used to spot the same station under slightly different names. */
fun normalizeName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

/** "MU" -> "Mauritius" (in the phone's language). */
fun countryName(code: String): String =
    if (code.length == 2) Locale("", code.uppercase()).getDisplayCountry().ifBlank { code } else code

/** "MU" -> 🇲🇺 */
fun flagEmoji(code: String): String =
    if (code.length != 2 || !code.all { it.isLetter() }) "🌐"
    else code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

/**
 * Merges station lists from several sources into one: the same station (same name) appears once.
 * The first one seen keeps its links first (built-in, then Radio Browser, then Streema); the
 * others' links become its fallbacks.
 */
fun mergeStations(vararg lists: List<Station>): List<Station> {
    val byKey = LinkedHashMap<String, Station>()
    for (list in lists) for (s in list) {
        val key = normalizeName(s.name).ifEmpty { s.id }
        byKey[key] = byKey[key]?.mergedWith(s) ?: s
    }
    return byKey.values.toList()
}

/** Letters shown on the station badge: "Radio Plus Fever" -> "PF", "Top FM" -> "TF", "Tamizen" -> "T". */
fun initials(name: String): String {
    val skip = setOf("radio", "mbc", "the", "mauritius", "maurice")
    val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val main = words.filter { it.lowercase() !in skip }.ifEmpty { words }
    return when {
        main.size >= 2 -> "${main[0][0]}${main[1][0]}"
        main.size == 1 -> main[0].take(1)
        else -> "?"
    }.uppercase()
}

/**
 * Stations shipped with the app. Each stream list is ordered best first:
 * the station's own website player, then directory-checked links, then the same
 * stream on the provider's alternate hosts.
 */
object BuiltInStations {
    private fun mbc(slug: String) = "https://radio.mbconline.xyz/hls/$slug.m3u8"
    private fun mbcLogo(channel: String) = "https://www.mbc.mu/sites/default/files/2026-06/$channel.png"
    private fun radioCo(id: String, firstHost: String = "streamer") =
        listOf("https://$firstHost.radio.co/$id/listen") +
            listOf("streamer", "stream", "s3").filter { it != firstHost }
                .map { "https://$it.radio.co/$id/listen" }

    private const val RADIOPLUS_THUMBS = "https://live.radioplus.mu/wp-content/uploads/elementor/thumbs"

    // Logos come from each station's own website. Stations without one (OUi, Tamizen)
    // get theirs from Radio Browser at runtime (see Logos), else show their initials.
    val all: List<Station> = listOf(
        Station(
            "mu:radioplus", "Radio Plus",
            radioCo("s8cc7a5da4", firstHost = "stream") + "https://stream.radioplus.mu:8000/radio.mp3",
            "news, talk, hits",
            favicon = "https://live.radioplus.mu/wp-content/uploads/2022/11/cropped-1-3-270x270.jpg",
            builtIn = true,
        ),
        Station(
            "mu:radioplus-hits", "Radio Plus Hits", radioCo("s1769ca1ac", firstHost = "s3"), "hits, pop",
            favicon = "$RADIOPLUS_THUMBS/1-qofpr0pnue142b8pskaslm2p1htbl0q090e2a9kgsq.jpg",
            builtIn = true,
        ),
        Station(
            "mu:radioplus-fever", "Radio Plus Fever", radioCo("s3fe39a4a4"), "dance, party",
            favicon = "$RADIOPLUS_THUMBS/1-1-qofprushx36adu10wxautehg1tp2fc1f159ln4bv9m.jpg",
            builtIn = true,
        ),
        Station(
            "mu:radioplus-indiz", "Radio Plus Indiz", radioCo("sed5c5a375"), "bollywood, oriental",
            favicon = "$RADIOPLUS_THUMBS/1-2-qofpshcmh4164h49971wh8sib2lvk2iz48x95ref4a.jpg",
            builtIn = true,
        ),
        Station(
            "mu:topfm", "Top FM", listOf("https://s3.voscast.com:10153/stream"), "news, talk, hits",
            favicon = "https://www.topfm.mu/themes/custom/topfm/logo.png",
            builtIn = true,
        ),
        Station(
            "mu:nrj", "NRJ Maurice", listOf("https://streaming.nrjaudio.fm/oudbea2h9r57"), "dance, pop, hits",
            favicon = "https://static.wixstatic.com/media/49f14e_ba841613bef84b1697665442367ef9c8~mv2.jpg",
            builtIn = true,
        ),
        Station(
            "mu:radioone", "Radio One",
            listOf("https://centova57.instainternet.com/proxy/vivavoceltd?mp=/stream"),
            "news, talk, pop",
            favicon = "https://radio1.mu/wp-content/uploads/2024/04/R1_logo-Square_1875x1875_transparent-768x768.png",
            builtIn = true,
        ),
        Station(
            "mu:mbc-radiomaurice", "MBC Radio Maurice", listOf(mbc("radiomaurice")), "news, talk",
            favicon = mbcLogo("am1"), builtIn = true,
        ),
        Station("mu:mbc-koolfm", "MBC Kool FM", listOf(mbc("koolfm")), "hits, local", favicon = mbcLogo("rm1"), builtIn = true),
        Station("mu:mbc-bestfm", "MBC Best FM", listOf(mbc("bestfm")), "hits, pop", favicon = mbcLogo("rm2"), builtIn = true),
        Station(
            "mu:mbc-taalfm", "MBC Taal FM", listOf(mbc("tallfm")), "bollywood, oriental",
            favicon = mbcLogo("rm3"), builtIn = true,
        ),
        Station(
            "mu:mbc-radiomauritius", "MBC Radio Mauritius", listOf(mbc("radiomauritus")), "news, culture",
            favicon = mbcLogo("am2"), builtIn = true,
        ),
        Station("mu:oui", "OUi Radio", listOf("https://mu1stream.mushost.com:8443/"), "hits, local", builtIn = true),
        Station("mu:tamizen", "Tamizen Radio", listOf("https://c34.radioboss.fm:18023/stream"), "tamil", builtIn = true),
        Station(
            "mu:radio7", "Radio 7 Mauritius", listOf("https://c2.radioboss.fm/stream/1056"), "hits, local",
            favicon = "https://radio7mauritius.com/images/logo3.png",
            builtIn = true,
        ),
        Station(
            "mu:sensuelle", "Sensuelle Radio",
            listOf("https://radio10.pro-fhi.net/radio/9169/stream.mp3"),
            "love songs, soul",
            favicon = "https://www.sensuelleradio.com/wp-content/uploads/2025/03/cropped-Sensuelle-Radio-Logo-1024x1024-1-270x270.png",
            builtIn = true,
        ),
    )
}
