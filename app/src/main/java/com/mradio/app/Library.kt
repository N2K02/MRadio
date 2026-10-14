package com.mradio.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Every station the app knows, split into two sections:
 * - **Favourite Radios** ([mine]): the user's own list, ordered, hearted stations first.
 * - **More from Mauritius**: all other known stations plus new ones from the directory.
 *
 * Also keeps hearts, which stream last worked for each station, and found logos.
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

    /** Hearted stations: top of Favourite Radios and Android Auto's list. */
    private val _favourites = MutableStateFlow<Set<String>>(emptySet())
    val favourites: StateFlow<Set<String>> = _favourites

    /** Stations seen in search/directory results, so they can be played without adding them. */
    private val seen = ConcurrentHashMap<String, Station>()

    /** The latest directory list for More from Mauritius (used by next/previous and Android Auto). */
    @Volatile
    private var discovered: List<Station> = emptyList()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("mradio", Context.MODE_PRIVATE)
        _favourites.value = prefs.getStringSet("favourites", emptySet())?.toSet() ?: emptySet()
        val saved = loadSaved()
        _stations.value = BuiltInStations.all + saved
        val ids = _stations.value.map { it.id }.toSet()
        val storedMine = prefs.getString("mine", null)?.let { json ->
            runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
        }
        // First launch: the top 3. Updating from 1.3: also keep what was added or hearted before.
        _mine.value = (storedMine ?: (TOP_3 + saved.map { it.id } + _favourites.value))
            .filter { it in ids }.distinct()
        if (storedMine == null) saveMine()
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

    private fun saveFavourites() {
        prefs.edit().putStringSet("favourites", _favourites.value).apply()
    }

    fun find(id: String): Station? = _stations.value.firstOrNull { it.id == id } ?: seen[id]

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

    fun isFavourite(id: String) = id in _favourites.value

    /** Favourite Radios: hearted first, then the rest, each in list order. */
    fun myStations(): List<Station> {
        val byId = _stations.value.associateBy { it.id }
        val favs = _favourites.value
        return _mine.value.mapNotNull { byId[it] }.sortedBy { if (it.id in favs) 0 else 1 }
    }

    /** More from Mauritius: known stations not in Favourite Radios, then new ones from the directory. */
    fun moreStations(fromDirectory: List<Station> = discovered): List<Station> {
        val mine = _mine.value.toSet()
        return _stations.value.filter { it.id !in mine } + fromDirectory.filterNot { known(it) }
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

    /** Moves the station to More from Mauritius (and takes its heart away). */
    fun removeFromMine(id: String) {
        if (id in _mine.value) {
            _mine.value = _mine.value - id
            saveMine()
        }
        if (id in _favourites.value) {
            _favourites.value = _favourites.value - id
            saveFavourites()
        }
    }

    /** Hearting a station also puts it in Favourite Radios. */
    fun toggleFavourite(station: Station) {
        if (station.id in _favourites.value) {
            _favourites.value = _favourites.value - station.id
        } else {
            val id = addToMine(station)
            _favourites.value = _favourites.value + id
        }
        saveFavourites()
    }

    /** Next/previous order: within the section the current station is in. */
    fun playOrder(currentId: String?): List<Station> {
        val mine = myStations()
        return if (currentId == null || mine.any { it.id == currentId }) mine.ifEmpty { moreStations() } else moreStations()
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
        }.sortedBy { if (it.id in mine) 0 else 1 }
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
    }

    /** The station's logo: its own, or one found on Radio Browser earlier. */
    fun logo(station: Station): String? = station.favicon ?: prefs.getString("logo:${station.id}", null)

    private val logoLookups = ConcurrentHashMap.newKeySet<String>()

    /** Look up a missing logo at most once per app run, and only for known stations. */
    fun shouldLookUpLogo(station: Station): Boolean =
        _stations.value.any { it.id == station.id } && logoLookups.add(station.id)

    fun saveLogo(stationId: String, url: String) {
        prefs.edit().putString("logo:$stationId", url).apply()
    }

    var lastPlayed: String?
        get() = prefs.getString("lastPlayed", null)
        set(value) = prefs.edit().putString("lastPlayed", value).apply()
}
