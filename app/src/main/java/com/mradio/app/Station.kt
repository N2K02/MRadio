package com.mradio.app

import org.json.JSONArray
import org.json.JSONObject

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
) {
    /** Short line under the name: up to three tags, or the country. */
    val subtitle: String
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
            .joinToString(" · ")
            .ifEmpty { if (country.equals("MU", ignoreCase = true)) "Mauritius" else country }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("streams", JSONArray(streams))
        .put("tags", tags)
        .put("favicon", favicon ?: "")
        .put("country", country)

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
            )
        }
    }
}

/** "MBC Kool FM" -> "mbckoolfm": used to spot the same station under slightly different names. */
fun normalizeName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

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
    private fun radioCo(id: String, firstHost: String = "streamer") =
        listOf("https://$firstHost.radio.co/$id/listen") +
            listOf("streamer", "stream", "s3").filter { it != firstHost }
                .map { "https://$it.radio.co/$id/listen" }

    val all: List<Station> = listOf(
        Station(
            "mu:radioplus", "Radio Plus",
            radioCo("s8cc7a5da4", firstHost = "stream") + "https://stream.radioplus.mu:8000/radio.mp3",
            "news, talk, hits", builtIn = true,
        ),
        Station("mu:radioplus-hits", "Radio Plus Hits", radioCo("s1769ca1ac", firstHost = "s3"), "hits, pop", builtIn = true),
        Station("mu:radioplus-fever", "Radio Plus Fever", radioCo("s3fe39a4a4"), "dance, party", builtIn = true),
        Station("mu:radioplus-indiz", "Radio Plus Indiz", radioCo("sed5c5a375"), "bollywood, oriental", builtIn = true),
        Station("mu:topfm", "Top FM", listOf("https://s3.voscast.com:10153/stream"), "news, talk, hits", builtIn = true),
        Station("mu:nrj", "NRJ Maurice", listOf("https://streaming.nrjaudio.fm/oudbea2h9r57"), "dance, pop, hits", builtIn = true),
        Station(
            "mu:radioone", "Radio One",
            listOf("https://centova57.instainternet.com/proxy/vivavoceltd?mp=/stream"),
            "news, talk, pop", builtIn = true,
        ),
        Station("mu:mbc-radiomaurice", "MBC Radio Maurice", listOf(mbc("radiomaurice")), "news, talk", builtIn = true),
        Station("mu:mbc-koolfm", "MBC Kool FM", listOf(mbc("koolfm")), "hits, local", builtIn = true),
        Station("mu:mbc-bestfm", "MBC Best FM", listOf(mbc("bestfm")), "hits, pop", builtIn = true),
        Station("mu:mbc-taalfm", "MBC Taal FM", listOf(mbc("tallfm")), "bollywood, oriental", builtIn = true),
        Station("mu:mbc-radiomauritius", "MBC Radio Mauritius", listOf(mbc("radiomauritus")), "news, culture", builtIn = true),
        Station("mu:oui", "OUi Radio", listOf("https://mu1stream.mushost.com:8443/"), "hits, local", builtIn = true),
        Station("mu:tamizen", "Tamizen Radio", listOf("https://c34.radioboss.fm:18023/stream"), "tamil", builtIn = true),
        Station("mu:radio7", "Radio 7 Mauritius", listOf("https://c2.radioboss.fm/stream/1056"), "hits, local", builtIn = true),
        Station(
            "mu:sensuelle", "Sensuelle Radio",
            listOf("https://radio10.pro-fhi.net/radio/9169/stream.mp3"),
            "love songs, soul", builtIn = true,
        ),
    )
}
