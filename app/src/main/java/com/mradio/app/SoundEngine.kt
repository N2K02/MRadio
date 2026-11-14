package com.mradio.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The sound engine: plain Kotlin, no Android classes, so it can be tested on a computer.
 *
 * Signal path for every stereo frame:
 *   5-band equalizer + bass shelf  ->  3D mode  ->  linked peak guard
 *
 * The 3D modes are built from three classic tricks:
 *  - mid/side widening: turn up what differs between left and right;
 *  - cross-feed with a small delay and a low-pass: each ear also hears a muffled, slightly
 *    late copy of the other channel, like speakers in a room (this is what makes headphone
 *    sound feel "in front" instead of "inside the head");
 *  - a filtered, gain-normalised stereo room for space.
 * 8D orbit moves the sound around the head with level, delay and muffling cues.
 */

/** Second-order filter (RBJ cookbook). Coefficients are set per use; state is kept per instance. */
internal class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1
        z1 = b1 * xd - a1 * y + z2
        z2 = b2 * xd - a2 * y
        return y.toFloat()
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0
    }

    fun peaking(fs: Int, hz: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * hz / fs
        val alpha = sin(w) / (2 * q)
        val c = cos(w)
        set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun lowShelf(fs: Int, hz: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * hz / fs
        val c = cos(w)
        val beta = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
        set(
            a * ((a + 1) - (a - 1) * c + beta),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - beta),
            (a + 1) + (a - 1) * c + beta,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - beta,
        )
    }

    fun lowPass(fs: Int, hz: Double, q: Double) {
        val w = 2 * PI * hz / fs
        val alpha = sin(w) / (2 * q)
        val c = cos(w)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }
}

/** A delay that can be read at a fractional number of samples back (linear interpolation). */
internal class DelayLine(minSize: Int) {
    private val size = Integer.highestOneBit(max(4, minSize) - 1) shl 1
    private val mask = size - 1
    private val buf = FloatArray(size)
    private var w = 0

    fun write(x: Float) {
        buf[w] = x
        w = (w + 1) and mask
    }

    /** The sample written [delay] samples ago (0 = the latest). */
    fun read(delay: Float): Float {
        val d = delay.coerceIn(0f, (size - 3).toFloat())
        val i = d.toInt()
        val frac = d - i
        val a = buf[(w - 1 - i) and mask]
        val b = buf[(w - 2 - i) and mask]
        return a + (b - a) * frac
    }

    fun reset() = buf.fill(0f)
}

class SoundEngine {
    private companion object { const val ORBIT_BLOCK = 16 }

    private var fs = 44100
    private var reverb = MusicRoom(fs)
    private val eq = Array(2) { Array(SoundFx.BAND_HZ.size) { Biquad() } }
    private val eqOn = BooleanArray(SoundFx.BAND_HZ.size)
    private val shelf = Array(2) { Biquad() }
    private var shelfOn = false
    private var preamp = 1f
    private var currentPreamp = 1f
    private var guardGain = 1f
    private var gainStep = 0.002f
    private var soundEffectsEnabled = true
    private var normalizationEnabled = true
    private var normalizer = LoudnessNormalizer(fs)

    private var mode = SpaceMode.OFF
    private var applied: SoundSettings? = null
    private var pending: SoundSettings? = null
    private var neutralAfterTransition = false
    private var transition = 1f

    /** True when nothing would change the sound: the player then passes the audio through untouched. */
    var bypass = true
        private set

    // 3D state
    private var sideGain = 1f
    private var crossGain = 0f
    private var crossNorm = 1f
    private var crossSamples = 13f
    private val crossDelay = arrayOf(DelayLine(512), DelayLine(512))
    private val crossLp = arrayOf(Biquad(), Biquad())
    private var reverbWet = 0f

    // 8D orbit state
    private var depth = 0f
    private var theta = 0.0
    private var thetaStep = 0.0
    private var maxItd = 20f
    private var orbitCountdown = 0
    private var orbitGl = 1f
    private var orbitGr = 1f
    private var orbitDl = 0f
    private var orbitDr = 0f
    private var orbitRear = 0f
    private var orbitFront = 1f
    private val orbitDelay = DelayLine(512)
    private val orbitLp = Biquad()

    private var outL = 0f
    private var outR = 0f

    /** Call when the stream's sample rate is known or changes. */
    fun configure(sampleRate: Int) {
        fs = sampleRate
        gainStep = (1 - kotlin.math.exp(-1.0 / (0.02 * fs))).toFloat()
        reverb = MusicRoom(fs)
        normalizer = LoudnessNormalizer(fs)
        applied = null
        pending = null
        transition = 1f
        reset()
    }

    fun reset() {
        eq.forEach { ch -> ch.forEach { it.reset() } }
        shelf.forEach { it.reset() }
        crossDelay.forEach { it.reset() }
        crossLp.forEach { it.reset() }
        orbitDelay.reset()
        orbitLp.reset()
        reverb.reset()
        normalizer.reset()
        currentPreamp = preamp
        guardGain = 1f
    }

    /** Keep the EQ and 3D filter state when only the selected stream changes. */
    fun resetNormalization() = normalizer.reset()

    /** Picks up new settings (cheap when nothing changed). Call from the audio thread only. */
    fun apply(s: SoundSettings) {
        if (s === pending || (s === applied && pending == null)) return
        if (applied == null || (!s.enabled && !soundEffectsEnabled)) {
            applyNow(s)
            currentPreamp = preamp
        } else {
            pending = s
            bypass = false
        }
    }

    private fun applyNow(s: SoundSettings) {
        if (s === applied) return
        applied = s
        soundEffectsEnabled = s.enabled
        if (normalizationEnabled != s.normalizeVolume) normalizer.reset()
        normalizationEnabled = s.normalizeVolume

                for (b in SoundFx.BAND_HZ.indices) {
            val db = s.bands.getOrElse(b) { 0f }
            eqOn[b] = abs(db) >= 0.1f && SoundFx.BAND_HZ[b] < fs * 0.45
            if (eqOn[b]) {
                for (ch in 0..1) eq[ch][b].peaking(fs, SoundFx.BAND_HZ[b].toDouble(), 0.9, db.toDouble())
            }
        }
        val bassDb = s.bass * 9.0
        shelfOn = bassDb >= 0.1
        if (shelfOn) {
            for (ch in 0..1) shelf[ch].lowShelf(fs, 120.0, bassDb)
        }
        // Make room for the boost so the loudest parts don't clip.
        preamp = MusicHeadroom.gain(s.bands, bassDb, fs)

        val nextMode = if (s.intensity <= 0f) SpaceMode.OFF else s.mode
        if (mode != nextMode) { reverb.reset(); crossDelay.forEach { it.reset() } }
        mode = nextMode
        val i = s.intensity.coerceIn(0f, 1f)
        sideGain = 1f
        crossGain = 0f
        var mix = 0f
        when (mode) {
            SpaceMode.OFF -> {}
            SpaceMode.WIDE -> sideGain = 1f + 1.2f * i
            SpaceMode.HEADPHONE_3D -> {
                sideGain = 1f + 0.5f * i
                crossGain = 0.45f * i
                crossSamples = 0.00027f * fs
                for (ch in 0..1) crossLp[ch].lowPass(fs, 700.0, 0.7)
                mix = 0.04f * i
                setReverb(0.65f)
            }
            SpaceMode.HALL -> {
                sideGain = 1f
                mix = 0.15f * i
                setReverb(0.70f)
            }
            SpaceMode.CINEMA -> {
                sideGain = 1f + 0.8f * i
                mix = 0.08f * i
                setReverb(0.65f)
            }
            SpaceMode.ORBIT_8D -> {
                depth = i
                thetaStep = 2 * PI / (9.0 * fs) // one lap every 9 seconds
                maxItd = 0.0006f * fs
                orbitLp.lowPass(fs, 2500.0, 0.7)
                mix = 0.04f * i
                setReverb(0.65f)
            }
        }
        crossNorm = 1f / (1f + crossGain)
        reverbWet = mix
        reverb.wet = mix
        val spatialBound = if (mode == SpaceMode.ORBIT_8D) 1f + 1.1f * i else sideGain
        preamp /= spatialBound * (1f + 2f * mix)

        bypass = !normalizationEnabled && (!soundEffectsEnabled ||
            (mode == SpaceMode.OFF && !shelfOn && eqOn.none { it }))
    }

    private fun setReverb(feedback: Float) { reverb.feedback = feedback }

    /** Processes [frames] interleaved stereo frames in place (samples in -1..1). */
    fun process(buf: FloatArray, frames: Int) {
        if (bypass) return
        val transitionStep = 1f / (fs * 0.01f)
        for (n in 0 until frames) {
            if (pending != null) {
                transition = max(0f, transition - transitionStep)
                if (transition == 0f) {
                    val next = pending!!; pending = null
                    // Change filter topology only while inaudible.
                    eq.forEach { ch -> ch.forEach { it.reset() } }; shelf.forEach { it.reset() }
                    reverb.reset(); crossDelay.forEach { it.reset() }; orbitDelay.reset()
                    applyNow(next); currentPreamp = preamp
                    neutralAfterTransition = bypass; bypass = false
                }
            } else transition = minOf(1f, transition + transitionStep)
            var l = buf[2 * n]
            var r = buf[2 * n + 1]

            if (soundEffectsEnabled) {
                for (b in eqOn.indices) if (eqOn[b]) {
                    l = eq[0][b].process(l)
                    r = eq[1][b].process(r)
                }
                if (shelfOn) {
                    l = shelf[0].process(l)
                    r = shelf[1].process(r)
                }
                currentPreamp += (preamp - currentPreamp) * gainStep
                l *= currentPreamp
                r *= currentPreamp

                space(l, r)
                l = outL
                r = outR
            }
            if (normalizationEnabled) {
                // Normalise post-EQ/3D loudness; preserve stereo balance with one gain.
                val gain = normalizer.nextGain(l, r)
                l *= gain
                r *= gain
            }

            // Linked gain reduction preserves stereo and is linear below the ceiling.
            val peak = max(abs(l), abs(r))
            val target = if (peak > 0.98f) 0.98f / peak else 1f
            guardGain = if (target < guardGain) target else guardGain + (target - guardGain) * gainStep * 0.25f
            buf[2 * n] = l * guardGain * transition
            buf[2 * n + 1] = r * guardGain * transition
        }
        if (transition >= 1f && pending == null && neutralAfterTransition) { bypass = true; neutralAfterTransition = false }
    }

    /** The 3D stage. Result in [outL] / [outR]. */
    private fun space(l: Float, r: Float) {
        when (mode) {
            SpaceMode.OFF -> {
                outL = l
                outR = r
                return
            }
            SpaceMode.ORBIT_8D -> orbit(l, r)
            else -> {
                val mid = (l + r) * 0.5f
                val side = (l - r) * 0.5f * sideGain
                var wl = mid + side
                var wr = mid - side
                if (crossGain > 0f) {
                    crossDelay[0].write(wl)
                    crossDelay[1].write(wr)
                    val toLeft = crossLp[0].process(crossDelay[1].read(crossSamples)) * crossGain
                    val toRight = crossLp[1].process(crossDelay[0].read(crossSamples)) * crossGain
                    wl = (wl + toLeft) * crossNorm
                    wr = (wr + toRight) * crossNorm
                }
                outL = wl
                outR = wr
            }
        }
        if (reverbWet > 0f) {
            reverb.process(outL, outR)
            outL = reverb.outL
            outR = reverb.outR
        }
    }

    /**
     * 8D: the centre of the mix travels in a circle around the head. Side to side is level plus a
     * small delay for the far ear; behind is muffled. A little of the original width stays.
     */
    private fun orbit(l: Float, r: Float) {
        // The source takes 9 s per lap, so the pan only needs updating every
        // ORBIT_BLOCK samples (~0.3 ms): ~16x fewer sin/cos than per sample, inaudible.
        if (orbitCountdown <= 0) {
            orbitCountdown = ORBIT_BLOCK
            theta += thetaStep * ORBIT_BLOCK
            if (theta > 2 * PI) theta -= 2 * PI
            val s = sin(theta).toFloat() // +1 = right ear, -1 = left ear
            val c = cos(theta).toFloat() // +1 = in front, -1 = behind
            val angle = (s * depth * 0.8f + 1f) * (PI.toFloat() / 4f) // equal-power pan, never fully hard
            orbitGl = cos(angle) * 1.4142135f
            orbitGr = sin(angle) * 1.4142135f
            orbitDl = max(0f, s) * maxItd * depth // source on the right: the left ear hears it later
            orbitDr = max(0f, -s) * maxItd * depth
            orbitRear = max(0f, -c) * 0.7f * depth
            orbitFront = 1f + 0.1f * c * depth
        }
        orbitCountdown--

        val mid = (l + r) * 0.5f
        val side = (l - r) * 0.5f
        orbitDelay.write(mid)

        val dull = orbitLp.process(mid) // keep the filter running so it never glitches
        val xl = orbitDelay.read(orbitDl)
        val xr = orbitDelay.read(orbitDr)
        val rear = orbitRear
        val blendL = xl * (1 - rear) + dull * rear
        val blendR = xr * (1 - rear) + dull * rear

        val keep = 1f - depth * 0.5f
        outL = blendL * orbitGl * orbitFront + side * keep
        outR = blendR * orbitGr * orbitFront - side * keep
    }

}
