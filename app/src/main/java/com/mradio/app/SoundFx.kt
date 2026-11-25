package com.mradio.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Station loudness preference, retained across upgrades. */
data class SoundSettings(val normalizeVolume: Boolean = true)

object SoundFx {
    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(SoundSettings())
    val settings: StateFlow<SoundSettings> = _settings

    // Updated when a station or its fallback stream changes. Read on the audio thread.
    @Volatile var streamGeneration: Int = 0
        private set

    fun onNewStream() { streamGeneration++ }

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("mradio_sound", Context.MODE_PRIVATE)
        val saved = runCatching { prefs.getString("settings", null) }.getOrNull()
        _settings.value = SoundPreferenceCodec.decode(saved)
        if (saved != SoundPreferenceCodec.encode(_settings.value)) save()
    }

    @Synchronized
    fun update(change: (SoundSettings) -> SoundSettings) {
        val next = change(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        save()
    }

    private fun save() {
        if (!::prefs.isInitialized) return
        val s = _settings.value
        prefs.edit().putString("settings", SoundPreferenceCodec.encode(s)).apply()
    }
}
