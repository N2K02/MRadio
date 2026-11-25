package com.mradio.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class SoundPreferenceCodecTest {
    @Test fun retainsDisabledNormalizationAndDropsRetiredEffects() {
        val old = """{"normalizeVolume":false,"enabled":true,"bands":[3,4],"preset":"Rock","bass":0.8,"mode":"WIDE"}"""
        val migrated = SoundPreferenceCodec.decode(old)
        assertFalse(migrated.normalizeVolume)
        val json = JSONObject(SoundPreferenceCodec.encode(migrated))
        assertEquals(1, json.length())
        assertFalse(json.getBoolean("normalizeVolume"))
    }

    @Test fun defaultsSafelyForMissingOrInvalidPreferences() {
        for (raw in listOf(null, "", "{broken", "{}", """{"normalizeVolume":null}""")) {
            assertTrue(SoundPreferenceCodec.decode(raw).normalizeVolume)
        }
    }

    @Test fun canonicalRepresentationIsIdempotent() {
        for (enabled in listOf(false, true)) {
            val state = SoundSettings(enabled)
            val encoded = SoundPreferenceCodec.encode(state)
            assertEquals(state, SoundPreferenceCodec.decode(encoded))
            assertEquals(encoded, SoundPreferenceCodec.encode(SoundPreferenceCodec.decode(encoded)))
        }
    }
}
