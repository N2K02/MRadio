package com.mradio.app

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Linked station loudness normalization and peak protection. */
class SoundEngine {
    private var normalizer = LoudnessNormalizer(44100)
    private var guardGain = 1f
    private var releaseStep = 0f
    var bypass = true
        private set

    fun configure(sampleRate: Int) {
        normalizer = LoudnessNormalizer(sampleRate)
        releaseStep = (1 - exp(-1.0 / (0.02 * sampleRate))).toFloat() * 0.25f
        reset()
    }

    fun reset() {
        normalizer.reset()
        guardGain = 1f
    }

    fun resetNormalization() = reset()

    fun apply(settings: SoundSettings) {
        if (bypass == settings.normalizeVolume) reset()
        bypass = !settings.normalizeVolume
    }

    fun process(buf: FloatArray, frames: Int) {
        if (bypass) return
        for (n in 0 until frames) {
            val l = buf[2 * n].takeIf { it.isFinite() } ?: 0f
            val r = buf[2 * n + 1].takeIf { it.isFinite() } ?: 0f
            val gain = normalizer.nextGain(l, r)
            val left = l * gain
            val right = r * gain
            val peak = max(abs(left), abs(right))
            val target = if (peak > 0.98f) 0.98f / peak else 1f
            guardGain = if (target < guardGain) target else guardGain + (target - guardGain) * releaseStep
            buf[2 * n] = left * guardGain
            buf[2 * n + 1] = right * guardGain
        }
    }
}
