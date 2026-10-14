package com.mradio.app

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * One list of stations from every source, without duplicates:
 * 1. MRadio's own Mauritius stations (their links stay first),
 * 2. Radio Browser (only stations its checker found working),
 * 3. Streema.
 * The same station from several sources becomes one station whose other links are fallbacks.
 * Stations found only on Streema are shown once their link has been checked and plays.
 */
object Directory {
    /** Where to look: Mauritius, worldwide, or one country by code. */
    const val MAURITIUS = "MU"
    const val WORLDWIDE = "WW"

    private fun countryOf(place: String): String? = if (place == WORLDWIDE) null else place

    /** Streema slug -> checked playable link ("" = didn't play). Kept for the app run. */
    private val checked = ConcurrentHashMap<String, String>()

    /** Stations named like [query] in [place]. Emits a first list fast, then grows as Streema stations are checked. */
    fun search(query: String, place: String): Flow<List<Station>> = merged(
        radioBrowser = { RadioBrowser.search(query, countryOf(place)) },
        streema = { Streema.search(query, countryOf(place)) },
    )

    /** The most listened stations in [place]. */
    fun popular(place: String): Flow<List<Station>> = merged(
        radioBrowser = { RadioBrowser.popular(countryOf(place)) },
        streema = { Streema.popular(countryOf(place)) },
    )

    /** Thrown on when no source could be reached at all. */
    class Unreachable : Exception("No station directory reachable")

    private fun merged(
        radioBrowser: suspend () -> List<Station>,
        streema: suspend () -> List<Station>,
    ): Flow<List<Station>> = channelFlow {
        val rbJob = async { runCatching { radioBrowser() } }
        val stJob = async { runCatching { streema() } }
        val rb = rbJob.await()
        val rbList = rb.getOrDefault(emptyList())
        // Radio Browser first (already checked), then Streema. Built-in stations are merged in by
        // the Library, which keeps their own links first.
        fun build(extra: List<Station>) = mergeStations(rbList, extra)
        if (rb.isSuccess) send(build(emptyList()))

        val st = stJob.await()
        if (rb.isFailure && st.isFailure) throw Unreachable()
        val candidates = st.getOrDefault(emptyList())
        val known = (BuiltInStations.all + rbList).map { normalizeName(it.name) }.toSet()

        // Same station as a working one: just add its Streema page (its link is a fallback).
        val sameAsKnown = candidates.filter { normalizeName(it.name) in known }
        val newOnes = candidates.filterNot { normalizeName(it.name) in known }
        val confirmed = java.util.Collections.synchronizedList(ArrayList<Station>())
        // Already checked earlier in this app run.
        newOnes.forEach { s -> s.streemaSlug?.let { checked[it] }?.takeIf { it.isNotEmpty() }?.let { confirmed += s.copy(streams = listOf(it)) } }
        send(build(sameAsKnown + ordered(newOnes, confirmed)))

        val toCheck = newOnes.filter { s -> s.streemaSlug?.let { !checked.containsKey(it) } ?: false }
        val gate = Semaphore(6)
        coroutineScope {
            toCheck.forEach { s ->
                launch {
                    gate.withPermit {
                        val slug = s.streemaSlug ?: return@withPermit
                        val link = Streema.streamUrl(slug)?.let { Streema.working(it) }
                        checked[slug] = link.orEmpty()
                        if (link != null) {
                            confirmed += s.copy(streams = listOf(link))
                            send(build(sameAsKnown + ordered(newOnes, confirmed)))
                        }
                    }
                }
            }
        }
        send(build(sameAsKnown + ordered(newOnes, confirmed)))
    }

    /** Checked stations in the directory's own order (most popular first). */
    private fun ordered(all: List<Station>, confirmed: List<Station>): List<Station> {
        val byId = confirmed.toList().associateBy { it.id }
        return all.mapNotNull { byId[it.id] }
    }

    /** Fallback links for [station] from every source, best first. Never throws. */
    suspend fun alternates(station: Station): List<String> = coroutineScope {
        val rb = async { RadioBrowser.alternates(station) }
        val st = async { Streema.alternates(station) }
        (rb.await() + st.await()).distinct()
    }

    /** Fills in the link of a Streema-only station before it plays. Never throws. */
    suspend fun withStreams(station: Station): Station {
        if (station.streams.isNotEmpty()) return station
        val slug = station.streemaSlug ?: return station
        val link = checked[slug]?.ifEmpty { null } ?: Streema.streamUrl(slug)?.let { Streema.working(it) ?: it }
        return if (link == null) station else station.copy(streams = listOf(link))
    }

    /** What the directories know about a station, for the player screen. */
    class Info(
        val country: String,
        val location: String,
        val frequency: String,
        val genres: List<String>,
        val language: String,
        val website: String,
        val slogan: String,
        val description: String,
        val listedBitrate: Int?,
        val listedCodec: String,
    )

    private val infoCache = ConcurrentHashMap<String, Info>()

    /** Details from Streema and Radio Browser combined. Never throws. */
    suspend fun info(station: Station): Info = infoCache[station.id] ?: coroutineScope {
        val st = async { Streema.findSlug(station)?.let { Streema.details(it) } }
        val rb = async { RadioBrowser.details(station) }
        val d = st.await()
        val r = rb.await()
        Info(
            country = d?.countryCode?.takeIf { it.length == 2 } ?: r?.optString("countrycode")?.takeIf { it.length == 2 }
                ?: station.country,
            location = d?.location?.ifBlank { null } ?: station.location,
            frequency = d?.frequency.orEmpty(),
            genres = d?.genres?.ifEmpty { null }
                ?: station.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            language = d?.language?.ifBlank { null }
                ?: r?.optString("language")?.split(',')?.firstOrNull()?.trim()?.replaceFirstChar { it.uppercase() }.orEmpty(),
            website = d?.website?.ifBlank { null } ?: r?.optString("homepage")?.takeIf { it.startsWith("http") }.orEmpty(),
            slogan = d?.slogan.orEmpty(),
            description = d?.description.orEmpty(),
            listedBitrate = r?.optInt("bitrate")?.takeIf { it > 0 },
            listedCodec = r?.optString("codec")?.takeIf { it.isNotBlank() && it != "UNKNOWN" }.orEmpty(),
        ).also { infoCache[station.id] = it }
    }
}
