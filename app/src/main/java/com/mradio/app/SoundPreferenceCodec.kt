package com.mradio.app

import org.json.JSONObject

/** Retain normalization while discarding retired effects from legacy JSON. */
internal object SoundPreferenceCodec {
    fun decode(raw: String?): SoundSettings = runCatching {
        SoundSettings(JSONObject(raw ?: "{}").optBoolean("normalizeVolume", true))
    }.getOrDefault(SoundSettings())

    fun encode(settings: SoundSettings): String =
        JSONObject().put("normalizeVolume", settings.normalizeVolume).toString()
}
