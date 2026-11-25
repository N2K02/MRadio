package com.mradio.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

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
        runCatching {
            val o = JSONObject(prefs.getString("settings", null) ?: return@runCatching)
            _settings.value = SoundSettings(normalizeVolume = o.optBoolean("normalizeVolume", true))
        }
        if (prefs.getString("settings", null) != JSONObject().put("normalizeVolume", _settings.value.normalizeVolume).toString()) save()
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
        val o = JSONObject().put("normalizeVolume", s.normalizeVolume)
        prefs.edit().putString("settings", o.toString()).apply()
    }
}
