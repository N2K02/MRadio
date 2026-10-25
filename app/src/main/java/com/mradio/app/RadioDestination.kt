package com.mradio.app

/** Compile-time checked app destinations; persisted through Compose's enum Saver. */
internal enum class RadioDestination {
    HOME, SEARCH;

    companion object {
        fun fromSaved(value: String): RadioDestination =
            when (value) {
                "Search" -> SEARCH
                else -> HOME // Includes removed legacy "Favorites" tab.
            }
    }
}

/** Settings subpages, intentionally independent of the bottom navigation destination. */
internal enum class SettingsDestination {
    MAIN, PLAYBACK, ALARM, STATISTICS, CUSTOM_STATIONS, BACKUP, UPDATES, DIAGNOSTICS;

    val title: String get() = when (this) {
        MAIN -> "Settings"
        PLAYBACK -> "Playback & audio"
        ALARM -> "Radio alarm"
        STATISTICS -> "Listening statistics"
        CUSTOM_STATIONS -> "Custom stations"
        BACKUP -> "Backup & restore"
        UPDATES -> "About & updates"
        DIAGNOSTICS -> "Connection diagnostics"
    }
}
