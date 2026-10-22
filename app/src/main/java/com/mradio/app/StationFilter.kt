package com.mradio.app

import java.util.Locale

/** Pure station-list transformations; never performs IO or mutates Library state. */
internal object StationFilter {
    fun apply(
        stations: List<Station>,
        genre: String = "All genres",
        workingOnly: Boolean = false,
        brokenIds: Set<String> = emptySet(),
        sortByName: Boolean = false,
    ): List<Station> {
        val filtered = stations.filter { station ->
            (genre == "All genres" ||
                station.tags.split(',').any { it.trim().equals(genre, ignoreCase = true) }) &&
                (!workingOnly || station.id !in brokenIds)
        }
        return if (sortByName) filtered.sortedWith(
            compareBy<Station> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id }
        ) else filtered
    }
}
