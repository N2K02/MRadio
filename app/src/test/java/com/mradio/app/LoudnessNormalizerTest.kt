package com.mradio.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessNormalizerTest {
    private val fs = 48000

    private fun steadyRms(amplitude: Float): Double {
        val normalizer = LoudnessNormalizer(fs)
        var power = 0.0
        val start = fs * 5
        for (i in 0 until fs * 7) {
            val sample = (amplitude * sin(2.0 * PI * 440.0 * i / fs)).toFloat()
            val out = sample * normalizer.nextGain(sample, sample)
            if (i >= start) power += out.toDouble() * out
        }
        return sqrt(power / (fs * 2))
    }

    @Test fun quietAndLoudStreamsConvergeToSimilarLoudness() {
        val quiet = steadyRms(0.055f)
        val loud = steadyRms(0.4f)
        val deltaDb = abs(20 * log10(quiet / loud))
        assertTrue("Station loudness differs by ${deltaDb} dB", deltaDb < 3.0)
    }

    @Test fun silenceDoesNotIncreaseGain() {
        val normalizer = LoudnessNormalizer(fs)
        repeat(fs * 5) { assertEquals(1f, normalizer.nextGain(0f, 0f), 0.0001f) }
    }

    @Test fun resettingClearsPreviousStationGain() {
        val normalizer = LoudnessNormalizer(fs)
        repeat(fs * 3) { normalizer.nextGain(0.5f, 0.5f) }
        assertTrue(normalizer.nextGain(0.5f, 0.5f) < 0.9f)
        normalizer.reset()
        assertEquals(1f, normalizer.nextGain(0f, 0f), 0.0001f)
    }

    @Test fun normalizationIsIndependentOfEqualizerToggle() {
        val engine = SoundEngine()
        engine.configure(fs)
        engine.apply(SoundSettings(enabled = false, normalizeVolume = true))
        assertFalse(engine.bypass)
        engine.apply(SoundSettings(enabled = false, normalizeVolume = false))
        assertTrue(engine.bypass)
    }

    @Test fun normalizedSignalStaysWithinPcmLimits() {
        val engine = SoundEngine()
        engine.configure(fs)
        engine.apply(SoundSettings(enabled = false, normalizeVolume = true))
        val samples = FloatArray(4096)
        repeat(50) {
            for (i in samples.indices) samples[i] = if (i % 2 == 0) 0.95f else -0.95f
            engine.process(samples, samples.size / 2)
            assertTrue(samples.all { it in -1f..1f })
        }
    }
}
