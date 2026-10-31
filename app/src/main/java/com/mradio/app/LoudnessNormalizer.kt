package com.mradio.app

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Smooth real-time automatic gain control for live streams.
 *
 * Uses a moving RMS loudness estimate (~350ms) with separate rise/fall times:
 * loud stations are attenuated quickly; quiet stations gain volume gradually.
 * Silence is gated so gaps between tracks cannot wind the gain up.
 *
 * This is an approximate live loudness match, not an offline EBU R128 / LUFS scan.
 * Gain is limited to -12..+10 dB and the final SoundEngine soft limiter protects
 * against clipping. No changes are made to the stream or system volume.
 */
internal class LoudnessNormalizer(sampleRate: Int) {
    init { require(sampleRate > 0) }

    private val levelStep = (1.0 - exp(-1.0 / (sampleRate * 0.35))).toFloat()
    private val reduceStep = (1.0 - exp(-1.0 / (sampleRate * 0.15))).toFloat()
    private val boostStep = (1.0 - exp(-1.0 / (sampleRate * 2.0))).toFloat()
    private var meanSquare = 0f
    private var gain = 1f

    fun reset() {
        meanSquare = 0f
        gain = 1f
    }

    fun nextGain(left: Float, right: Float): Float {
        val energy = (left * left + right * right) * 0.5f
        meanSquare += (energy - meanSquare) * levelStep
        // Gate around -46 dBFS to avoid amplifying pauses or quiet background noise.
        if (meanSquare > 0.000025f) {
            val target = (0.126f / sqrt(meanSquare)).coerceIn(0.251f, 3.162f)
            gain += (target - gain) * (if (target < gain) reduceStep else boostStep)
        }
        return gain
    }
}
