package com.mradio.app

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Linked station normalization with a smooth wet/dry transition. */
class SoundEngine {
    private var rate = 44100
    private var normalizer = LoudnessNormalizer(rate)
    private var guardGain = 1f
    private var releaseStep = 0f
    private var mixStep = 1f / 2205f
    private var mix = 0f
    private var enabled = false
    val bypass: Boolean get() = !enabled && mix == 0f

    fun configure(sampleRate: Int) {
        require(sampleRate > 0)
        if (rate != sampleRate) {
            rate = sampleRate
            normalizer = LoudnessNormalizer(rate)
        }
        releaseStep = (1 - exp(-1.0 / (0.02 * sampleRate))).toFloat() * 0.25f
        mixStep = 1f / max(1f, sampleRate * 0.05f)
        reset()
    }

    fun reset() {
        normalizer.reset()
        guardGain = 1f
        mix = if (enabled) 1f else 0f
    }

    fun resetNormalization() {
        normalizer.reset()
        guardGain = 1f
        mix = 0f // introduce normalization smoothly on a new stream
    }

    fun apply(settings: SoundSettings) {
        enabled = settings.normalizeVolume
    }

    /** Returns a linked gain, preserving the native channel count and stereo balance. */
    fun nextGain(left: Float, right: Float): Float {
        if (bypass) return 1f
        return nextValidatedGain(sanitize(left), sanitize(right))
    }

    internal fun nextValidatedGain(l: Float, r: Float): Float {
        if (bypass) return 1f
        val gain = normalizer.nextGain(l, r)
        val peak = max(abs(l), abs(r)) * gain
        val target = if (peak > 0.98f) 0.98f / peak else 1f
        guardGain = if (target < guardGain) target else guardGain + (target - guardGain) * releaseStep
        mix = if (enabled) (mix + mixStep).coerceAtMost(1f) else (mix - mixStep).coerceAtLeast(0f)
        // Ensure the fade terminates despite floating point accumulation.
        if (!enabled && mix < mixStep * 0.001f) mix = 0f
        val result = 1f + (gain * guardGain - 1f) * mix
        if (bypass) {
            normalizer.reset()
            guardGain = 1f
        }
        return result
    }

    fun process(buf: FloatArray, frames: Int) {
        if (bypass) return
        for (n in 0 until frames) {
            if (bypass) break
            val l = sanitize(buf[2 * n])
            val r = sanitize(buf[2 * n + 1])
            val gain = nextValidatedGain(l, r)
            buf[2 * n] = l * gain
            buf[2 * n + 1] = r * gain
        }
    }
    companion object {
        internal fun sanitize(value: Float): Float = if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    }
}
