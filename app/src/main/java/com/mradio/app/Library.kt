package com.mradio.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Every station the app knows, split into two sections:
 * - **Favourite Radios** ([mine]): the user's own list, in order. The heart adds a station here.
 * - **More from …**: all other known stations plus new ones from the directories.
 *
 * Also keeps which stream last worked for each station, stations that stopped working,
 * stations the user removed completely, and found logos.
 * Shared by the app screen and the playback service (same process).
 */
object Library {
    /** The most popular stations, in Favourite Radios on first launch. */
    private val TOP_3 = listOf("mu:radioplus", "mu:topfm", "mu:radioone")

    private lateinit var prefs: SharedPreferences
    private var initialized = false

    /** Built-in stations plus directory stations the user has added at some point. */
    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations

    /** Ids in Favourite Radios, in order. */
    private val _mine = MutableStateFlow<List<String>>(emptyList())
    val mine: StateFlow<List<String>> = _mine

    /** Stations that stopped working: id -> when it last failed (ms). Cleared when one plays again. */
    private val _broken = MutableStateFlow<Map<String, Long>>(emptyMap())
    val broken: StateFlow<Map<String, Long>> = _broken

    /** Stations removed completely (normalized names, so every source's copy stays hidden). */
    private val _recent = MutableStateFlow<List<Station>>(emptyList())
    val recent: StateFlow<List<Station>> = _recent
    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    val pinned: StateFlow<Set<String>> = _pinned
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    private val _hidden = MutableStateFlow<Map<String, String>>(emptyMap())
    /** normalized name -> display name */
    val hidden: StateFlow<Map<String, String>> = _hidden

    /** The playing stream's sound quality, filled in by the player (same process). */
    val audio = MutableStateFlow<AudioInfo?>(null)

    /** Persisted listening time and approximate stream bytes; unknown bitrate is excluded. */
    data class ListeningStats(
        val listeningMs: Long = 0L,
        val wifiEstimatedBytes: Long = 0L,
        val mobileEstimatedBytes: Long = 0L,
        val unknownNetworkEstimatedBytes: Long = 0L,
        val unestimatedMs: Long = 0L,
    )
    private val _listeningStats = MutableStateFlow(ListeningStats())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats

    /**
     * Called only while actual playback is active, with time measured using elapsed realtime.
     * No arbitrary default bitrate: missing bitrate stays unestimated.
     * Radio data consumption is estimated, not a measured network-byte total.
     */
    fun recordListening(durationMs: Long, bitrateKbps: Int?, transport: String) {
        if (durationMs <= 0L || durationMs > 15_000L) return
        val previous = _listeningStats.value
        val bytes = bitrateKbps?.takeIf { it in 8..2_000 }
            ?.let { it.toLong() * durationMs / 8L } ?: 0L
        val next = previous.copy(
            listeningMs = previous.listeningMs + durationMs,
            wifiEstimatedBytes = previous.wifiEstimatedBytes + if (transport == "wifi") bytes else 0L,
            mobileEstimatedBytes = previous.mobileEstimatedBytes + if (transport == "mobile") bytes else 0L,
            unknownNetworkEstimatedBytes = previous.unknownNetworkEstimatedBytes +
                if (transport != "wifi" && transport != "mobile") bytes else 0L,
            unestimatedMs = previous.unestimatedMs + if (bitrateKbps == null || bitrateKbps !in 8..2_000) durationMs else 0L,
        )
        _listeningStats.value = next
        prefs.edit()
            .putLong("stats:listeningMs", next.listeningMs)
            .putLong("stats:wifiBytes", next.wifiEstimatedBytes)
            .putLong("stats:mobileBytes", next.mobileEstimatedBytes)
            .putLong("stats:otherBytes", next.unknownNetworkEstimatedBytes)
            .putLong("stats:unestimatedMs", next.unestimatedMs)
            .apply()
    }

    fun clearListeningStats() {
        _listeningStats.value = ListeningStats()
        prefs.edit()
            .remove("stats:listeningMs")
            .remove("stats:wifiBytes")
            .remove("stats:mobileBytes")
            .remove("stats:otherBytes")
            .remove("stats:unestimatedMs")
            .apply()
    }

    /** When the sleep timer stops the radio (ms), or null. Set by the player. */
    val sleepAt = MutableStateFlow<Long?>(null)

    /** The list the user last played from in the app (for next/previous). */
    @Volatile
    private var browsing: List<Station> = emptyList()

    /** Stations seen in search/directory results, so they can be played without adding them. */
    private val seen = ConcurrentHashMap<String, Station>()

    /** The latest directory list for More from Mauritius (used by next/previous and Android Auto). */
    @Volatile
    private var discovered: List<Station> = emptyList()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("mradio", Context.MODE_PRIVATE)
        val oldHearts = prefs.getStringSet("favourites", emptySet())?.toSet() ?: emptySet()
        _broken.value = runCatching {
            val o = JSONObject(prefs.getString("broken", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
        _hidden.value = runCatching {
            val o = JSONObject(prefs.getString("hidden", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        _recent.value = runCatching {
            val a = JSONArray(prefs.getString("recent", "[]"))
            (0 until a.length()).map { Station.fromJson(a.getJSONObject(it)) }.distinctBy { it.id }.take(20)
        }.getOrDefault(emptyList())
        _pinned.value = prefs.getStringSet("pinned", emptySet())?.toSet() ?: emptySet()
        val saved = loadSaved()
        _stations.value = BuiltInStations.all + saved
        val ids = _stations.value.map { it.id }.toSet()
        val storedMine = prefs.getString("mine", null)?.let { json ->
            runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
        }
        // First launch: the top 3. Updating from 1.3: also keep what was added or hearted before.
        val list = (storedMine ?: (TOP_3 + saved.map { it.id } + oldHearts)).filter { it in ids }.distinct()
        // Hearts used to pin stations to the top; now the heart just means "in Favourite Radios".
        // Keep the old order once: hearted stations first.
        _mine.value = list.sortedBy { if (it in oldHearts) 0 else 1 }
        if (storedMine == null || oldHearts.isNotEmpty()) {
            saveMine()
            prefs.edit().remove("favourites").apply()
        }
        _listeningStats.value = ListeningStats(
            listeningMs = prefs.getLong("stats:listeningMs", 0L),
            wifiEstimatedBytes = prefs.getLong("stats:wifiBytes", 0L),
            mobileEstimatedBytes = prefs.getLong("stats:mobileBytes", 0L),
            unknownNetworkEstimatedBytes = prefs.getLong("stats:otherBytes", 0L),
            unestimatedMs = prefs.getLong("stats:unestimatedMs", 0L),
        )
        autoPlayPhone.value = prefs.getBoolean("autoPlayPhone", false)
        autoPlayAuto.value = prefs.getBoolean("autoPlayAuto", true)
        initialized = true
    }

    private fun loadSaved(): List<Station> = runCatching {
        val a = JSONArray(prefs.getString("added", "[]"))
        (0 until a.length()).map { Station.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveStations() {
        val a = JSONArray()
        _stations.value.filter { !it.builtIn }.forEach { a.put(it.toJson()) }
        prefs.edit().putString("added", a.toString()).apply()
    }

    private fun saveMine() {
        prefs.edit().putString("mine", JSONArray(_mine.value).toString()).apply()
    }

    private fun saveBroken() {
        prefs.edit().putString("broken", JSONObject(_broken.value as Map<*, *>).toString()).apply()
    }

    private fun saveHidden() {
        prefs.edit().putString("hidden", JSONObject(_hidden.value as Map<*, *>).toString()).apply()
    }

    fun find(id: String): Station? = _stations.value.firstOrNull { it.id == id }
        ?: seen[id] ?: _recent.value.firstOrNull { it.id == id }

    /** Called only on station selection, not retries or automatic reconnection. */
    fun recordPlayed(station: Station) {
        val known = find(station.id) ?: station
        _recent.value = (listOf(known) + _recent.value.filterNot { it.id == known.id }).take(20)
        prefs.edit().putString("recent", JSONArray().apply {
            _recent.value.forEach { put(it.toJson()) }
        }.toString()).apply()
    }

    fun togglePinned(id: String) {
        if (id !in _mine.value) return
        _pinned.value = if (id in _pinned.value) _pinned.value - id else _pinned.value + id
        prefs.edit().putStringSet("pinned", _pinned.value).apply()
    }

    fun logPlayback(message: String) {
        val clean = message.take(220).replace(Regex("https?://\\S+"), "[stream]")
        val entry = "${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM).format(java.util.Date())} · $clean"
        _events.value = (listOf(entry) + _events.value).take(60)
    }

    fun clearPlaybackLog() { _events.value = emptyList() }

    /** Saves links found for a known station (e.g. a Streema station's link once it played). */
    fun update(station: Station) {
        if (_stations.value.none { it.id == station.id && !it.builtIn }) {
            seen[station.id] = station
            return
        }
        _stations.value = _stations.value.map { if (it.id == station.id) station else it }
        saveStations()
    }

    fun setBrowsing(stations: List<Station>) {
        browsing = stations
        remember(stations)
    }

    fun isHidden(station: Station) = normalizeName(station.name) in _hidden.value

    /** Removes the station from every section and from search results. */
    fun hide(station: Station) {
        removeFromMine(station.id)
        _hidden.value = _hidden.value + (normalizeName(station.name) to station.name)
        saveHidden()
    }

    fun unhide(key: String) {
        _hidden.value = _hidden.value - key
        saveHidden()
    }

    fun markBroken(stationId: String) {
        _broken.value = _broken.value + (stationId to System.currentTimeMillis())
        saveBroken()
    }

    fun isBroken(id: String) = id in _broken.value

    fun remember(stations: List<Station>) = stations.forEach { seen[it.id] = it }

    fun setDiscovered(stations: List<Station>) {
        discovered = stations
        remember(stations)
    }

    /** The known station with this id or the same name, if any. */
    private fun knownVersion(station: Station): Station? {
        val key = normalizeName(station.name)
        return _stations.value.firstOrNull { it.id == station.id } ?: _stations.value.firstOrNull { normalizeName(it.name) == key }
    }

    /** True if the app already knows this station (built-in or added before), by id or name. */
    fun known(station: Station): Boolean = knownVersion(station) != null

    fun isMine(id: String) = id in _mine.value

    /** Favourite Radios, in list order. */
    fun myStations(): List<Station> {
        val byId = _stations.value.associateBy { it.id }
        return _mine.value.sortedBy { if (it in _pinned.value) 0 else 1 }.mapNotNull { byId[it] }
    }

    /**
     * More from Mauritius: known stations not in Favourite Radios, then new ones from the directory.
     * For another country or worldwide ([mauritius] false), only the directory's stations.
     */
    fun moreStations(fromDirectory: List<Station> = discovered, mauritius: Boolean = true): List<Station> {
        val mine = _mine.value.toSet()
        val local = if (mauritius) _stations.value.filter { it.id !in mine } else emptyList()
        return (local + fromDirectory.filterNot { known(it) }).filterNot { isHidden(it) }
    }

    /** Adds the station to Favourite Radios (at the end). Returns the id it is kept under. */
    fun addToMine(station: Station): String {
        val keep = knownVersion(station) ?: station.copy(builtIn = false).also {
            _stations.value = _stations.value + it
            saveStations()
        }
        if (keep.id !in _mine.value) {
            _mine.value = _mine.value + keep.id
            saveMine()
        }
        return keep.id
    }

    /** Moves a station up ([offset] -1) or down (+1) in Favourite Radios. */
    fun moveInMine(id: String, offset: Int) {
        val list = _mine.value.toMutableList()
        val from = list.indexOf(id)
        if (from < 0) return
        val to = (from + offset).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        _mine.value = list
        saveMine()
    }

    /** Takes the station out of Favourite Radios. */
    fun removeFromMine(id: String) {
        if (id in _mine.value) {
            if (id in _pinned.value) togglePinned(id)
            _mine.value = _mine.value - id
            saveMine()
        }
    }

    /** The heart: adds the station to Favourite Radios, or takes it out. */
    fun toggleMine(station: Station) {
        val known = knownVersion(station)
        if (known != null && known.id in _mine.value) removeFromMine(known.id) else addToMine(station)
    }

    /** True if the station (or the same station under another id) is in Favourite Radios. */
    fun inMine(station: Station): Boolean = (knownVersion(station)?.id ?: station.id) in _mine.value

    /** Next/previous order: within the list the current station is in. */
    fun playOrder(currentId: String?): List<Station> {
        val mine = myStations()
        if (currentId == null || mine.any { it.id == currentId }) return mine.ifEmpty { moreStations() }
        return browsing.takeIf { list -> list.any { it.id == currentId } } ?: moreStations()
    }

    /** Searches every known station, Favourite Radios first. */
    fun searchLocal(query: String): List<Station> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val key = normalizeName(q)
        val mine = _mine.value.toSet()
        return _stations.value.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.tags.contains(q, ignoreCase = true) ||
                (key.isNotEmpty() && normalizeName(it.name).contains(key))
        }.filterNot { isHidden(it) }.sortedBy { if (it.id in mine) 0 else 1 }
    }

    /**
     * The order to try streams in: the one that last played fine first (it may also be a
     * fallback found online), then the station's own list, best first.
     */
    fun orderedStreams(station: Station): List<String> {
        val good = prefs.getString("good:${station.id}", null)
        return (listOfNotNull(good) + station.streams).distinct()
    }

    fun markGood(stationId: String, url: String) {
        if (prefs.getString("good:$stationId", null) != url) {
            prefs.edit().putString("good:$stationId", url).apply()
        }
        prefs.edit().putLong("worked:$stationId", System.currentTimeMillis()).apply()
        if (stationId in _broken.value) {
            _broken.value = _broken.value - stationId
            saveBroken()
        }
    }

    /** When the station last played fine (ms), or null. */
    fun lastWorked(stationId: String): Long? = prefs.getLong("worked:$stationId", 0L).takeIf { it > 0 }

    /** The station's logo: its own, or one found on Radio Browser earlier. */
    fun logo(station: Station): String? = station.favicon ?: prefs.getString("logo:${station.id}", null)

    private val logoLookups = ConcurrentHashMap.newKeySet<String>()

    /** Look up a missing logo at most once per app run, and only for known stations. */
    fun shouldLookUpLogo(station: Station): Boolean =
        _stations.value.any { it.id == station.id } && logoLookups.add(station.id)

    fun saveLogo(stationId: String, url: String) {
        prefs.edit().putString("logo:$stationId", url).apply()
    }

    /** Where search and "More from" look: "MU", "WW" (worldwide) or a country code. */
    var place: String
        get() = prefs.getString("place", Directory.MAURITIUS) ?: Directory.MAURITIUS
        set(value) = prefs.edit().putString("place", value).apply()

    /** Phone launch defaults off; Android Auto browser connection defaults on. */
    val autoPlayPhone = MutableStateFlow(false)
    val autoPlayAuto = MutableStateFlow(true)

    fun setAutoPlayPhone(enabled: Boolean) {
        autoPlayPhone.value = enabled
        prefs.edit().putBoolean("autoPlayPhone", enabled).apply()
    }

    fun setAutoPlayAuto(enabled: Boolean) {
        autoPlayAuto.value = enabled
        prefs.edit().putBoolean("autoPlayAuto", enabled).apply()
    }

    var lastPlayed: String?
        get() = prefs.getString("lastPlayed", null)
        set(value) = prefs.edit().putString("lastPlayed", value).apply()
}

/** What the playing stream sends, for the player screen. */
data class AudioInfo(
    val codec: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val host: String?,
    val stationName: String?,
    val genre: String?,
)
