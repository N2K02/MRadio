package com.mradio.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * The user's stations (built-in + added), favourites, and which stream last worked for each
 * station. Shared by the app screen and the playback service (same process).
 */
object Library {
    private lateinit var prefs: SharedPreferences
    private var initialized = false

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations

    private val _favourites = MutableStateFlow<Set<String>>(emptySet())
    val favourites: StateFlow<Set<String>> = _favourites

    /** Stations seen in search/discover results but not added, so they can still be played. */
    private val seen = ConcurrentHashMap<String, Station>()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("mradio", Context.MODE_PRIVATE)
        _favourites.value = prefs.getStringSet("favourites", emptySet())?.toSet() ?: emptySet()
        _stations.value = BuiltInStations.all + loadAdded()
        initialized = true
    }

    private fun loadAdded(): List<Station> = runCatching {
        val a = JSONArray(prefs.getString("added", "[]"))
        (0 until a.length()).map { Station.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveAdded() {
        val a = JSONArray()
        _stations.value.filter { !it.builtIn }.forEach { a.put(it.toJson()) }
        prefs.edit().putString("added", a.toString()).apply()
    }

    fun find(id: String): Station? = _stations.value.firstOrNull { it.id == id } ?: seen[id]

    fun remember(stations: List<Station>) = stations.forEach { seen[it.id] = it }

    /** True if this station (or one with the same name) is already in the user's list. */
    fun contains(station: Station): Boolean {
        val key = normalizeName(station.name)
        return _stations.value.any { it.id == station.id || normalizeName(it.name) == key }
    }

    fun add(station: Station) {
        if (contains(station)) return
        _stations.value = _stations.value + station.copy(builtIn = false)
        saveAdded()
    }

    /** Only stations the user added can be removed; built-in ones stay. */
    fun remove(id: String) {
        _stations.value = _stations.value.filterNot { it.id == id && !it.builtIn }
        saveAdded()
        if (id in _favourites.value) toggleFavourite(id)
    }

    fun isFavourite(id: String) = id in _favourites.value

    fun toggleFavourite(id: String) {
        val now = _favourites.value.let { if (id in it) it - id else it + id }
        _favourites.value = now
        prefs.edit().putStringSet("favourites", now).apply()
    }

    /** Favourites first, then the rest, in list order. */
    fun favouritesFirst(): List<Station> {
        val favs = _favourites.value
        return _stations.value.sortedBy { if (it.id in favs) 0 else 1 }
    }

    fun searchLocal(query: String): List<Station> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val key = normalizeName(q)
        return _stations.value.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.tags.contains(q, ignoreCase = true) ||
                (key.isNotEmpty() && normalizeName(it.name).contains(key))
        }
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

    var lastPlayed: String?
        get() = prefs.getString("lastPlayed", null)
        set(value) = prefs.edit().putString("lastPlayed", value).apply()
}
