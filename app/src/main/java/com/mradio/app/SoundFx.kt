package com.mradio.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** The "3D" part of the sound: how the stereo picture is reshaped. */
enum class SpaceMode(val label: String, val blurb: String) {
    OFF("Off", "Plain stereo, as the station sends it."),
    WIDE("Wide", "Pushes the sound out beyond the speakers or earbuds."),
    HEADPHONE_3D("Headphone 3D", "Puts the music in front of you, like speakers in a room. Best on headphones."),
    HALL("Concert hall", "Adds the space and echo of a large hall."),
    CINEMA("Cinema", "Wide, deep and enveloping."),
    ORBIT_8D("8D orbit", "The sound slowly circles around your head. Best on headphones."),
}

/** Everything the equalizer screen can change. Saved between launches. */
data class SoundSettings(
    val enabled: Boolean = true,
    /** Automatically match the perceived loudness of quiet and loud stations. */
    val normalizeVolume: Boolean = true,
    /** Gain in dB for [SoundFx.BAND_HZ], each -12..+12. */
    val bands: List<Float> = List(SoundFx.BAND_HZ.size) { 0f },
    /** Extra low-end, 0..1. */
    val bass: Float = 0f,
    val mode: SpaceMode = SpaceMode.OFF,
    /** How strong the [mode] is, 0..1. */
    val intensity: Float = 0.6f,
    /** Name of the preset the bands came from, or "Custom". */
    val preset: String = "Flat",
)

/**
 * The sound settings, shared by the equalizer screen and the player (same process).
 * [SoundProcessor] reads [settings] while playing, so a change is heard within a fraction of a second.
 */
object SoundFx {
    val BAND_HZ = intArrayOf(60, 230, 910, 3600, 14000)

    val PRESETS: List<Pair<String, List<Float>>> = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass boost" to listOf(6f, 4f, 0f, 0f, 0f),
        "Vocal" to listOf(-2f, 0f, 3f, 3f, 0f),
        "Pop" to listOf(-1f, 2f, 4f, 2f, -1f),
        "Rock" to listOf(4f, 2f, -1f, 3f, 4f),
        "Dance" to listOf(6f, 3f, 0f, 2f, 3f),
        "Hip-hop" to listOf(5f, 3f, 0f, 1f, 3f),
        "Jazz" to listOf(3f, 2f, -2f, 2f, 4f),
        "Classical" to listOf(4f, 3f, -2f, 3f, 4f),
        "Treble boost" to listOf(0f, 0f, 0f, 3f, 6f),
    )

    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(SoundSettings())
    val settings: StateFlow<SoundSettings> = _settings

    // Updated when a station or its fallback stream changes. Read on the audio thread.
    @Volatile var streamGeneration: Int = 0
        private set

    fun onNewStream() { streamGeneration++ }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("mradio_sound", Context.MODE_PRIVATE)
        runCatching {
            val o = JSONObject(prefs.getString("settings", null) ?: return@runCatching)
            val a = o.getJSONArray("bands")
            _settings.value = SoundSettings(
                enabled = o.optBoolean("enabled", true),
                normalizeVolume = o.optBoolean("normalizeVolume", true),
                bands = List(BAND_HZ.size) { a.optDouble(it, 0.0).toFloat().coerceIn(-12f, 12f) },
                bass = o.optDouble("bass", 0.0).toFloat().coerceIn(0f, 1f),
                mode = runCatching { SpaceMode.valueOf(o.optString("mode")) }.getOrDefault(SpaceMode.OFF),
                intensity = o.optDouble("intensity", 0.6).toFloat().coerceIn(0f, 1f),
                preset = o.optString("preset", "Custom"),
            )
        }
    }

    fun update(change: (SoundSettings) -> SoundSettings) {
        _settings.update(change)
        save()
    }

    fun setBand(index: Int, db: Float) = update { s ->
        s.copy(bands = s.bands.toMutableList().also { it[index] = db.coerceIn(-12f, 12f) }, preset = "Custom")
    }

    fun applyPreset(name: String) {
        val bands = PRESETS.firstOrNull { it.first == name }?.second ?: return
        update { it.copy(bands = bands, preset = name) }
    }

    fun reset() = update { SoundSettings(enabled = it.enabled, normalizeVolume = it.normalizeVolume) }

    private fun save() {
        if (!::prefs.isInitialized) return
        val s = _settings.value
        val o = JSONObject()
            .put("enabled", s.enabled)
            .put("normalizeVolume", s.normalizeVolume)
            .put("bands", JSONArray(s.bands.map { it.toDouble() }))
            .put("bass", s.bass.toDouble())
            .put("mode", s.mode.name)
            .put("intensity", s.intensity.toDouble())
            .put("preset", s.preset)
        prefs.edit().putString("settings", o.toString()).apply()
    }
}
