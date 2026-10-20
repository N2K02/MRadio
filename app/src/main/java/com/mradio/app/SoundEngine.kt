package com.mradio.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/*
 * The sound engine: plain Kotlin, no Android classes, so it can be tested on a computer.
 *
 * Signal path for every stereo frame:
 *   5-band equalizer + bass shelf  ->  3D mode  ->  soft limiter
 *
 * The 3D modes are built from three classic tricks:
 *  - mid/side widening: turn up what differs between left and right;
 *  - cross-feed with a small delay and a low-pass: each ear also hears a muffled, slightly
 *    late copy of the other channel, like speakers in a room (this is what makes headphone
 *    sound feel "in front" instead of "inside the head");
 *  - a small reverb (Freeverb-style combs and all-passes) for space.
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

private class Comb(size: Int) {
    private val buf = FloatArray(size)
    private var i = 0
    private var store = 0f

    fun process(input: Float, feedback: Float, damp: Float): Float {
        val out = buf[i]
        store = out * (1 - damp) + store * damp
        if (abs(store) < 1e-20f) store = 0f // no denormals when the music stops
        buf[i] = input + store * feedback
        if (++i == buf.size) i = 0
        return out
    }

    fun reset() {
        buf.fill(0f)
        store = 0f
    }
}

private class AllPass(size: Int) {
    private val buf = FloatArray(size)
    private var i = 0

    fun process(input: Float): Float {
        val held = buf[i]
        val out = -input + held
        buf[i] = input + held * 0.5f
        if (++i == buf.size) i = 0
        return out
    }

    fun reset() = buf.fill(0f)
}

/** A small stereo reverb (4 combs + 2 all-passes per side, the right side slightly longer for width). */
internal class Reverb(private val fs: Int) {
    private fun scaled(n: Int) = max(1, (n * fs / 44100.0).roundToInt())
    private val combLengths = intArrayOf(1116, 1188, 1277, 1356)
    private val combL = Array(4) { Comb(scaled(combLengths[it])) }
    private val combR = Array(4) { Comb(scaled(combLengths[it] + 23)) }
    private val apL = arrayOf(AllPass(scaled(556)), AllPass(scaled(441)))
    private val apR = arrayOf(AllPass(scaled(556 + 23)), AllPass(scaled(441 + 23)))
    private val pre = DelayLine(fs / 10)

    var feedback = 0.84f
    var damp = 0.4f
    var preSamples = 0f
    var outL = 0f
    var outR = 0f

    fun process(inL: Float, inR: Float) {
        pre.write((inL + inR) * 0.5f * 0.03f)
        val x = pre.read(preSamples)
        var l = 0f
        var r = 0f
        for (k in 0 until 4) {
            l += combL[k].process(x, feedback, damp)
            r += combR[k].process(x, feedback, damp)
        }
        for (k in 0 until 2) {
            l = apL[k].process(l)
            r = apR[k].process(r)
        }
        outL = l
        outR = r
    }

    fun reset() {
        combL.forEach { it.reset() }
        combR.forEach { it.reset() }
        apL.forEach { it.reset() }
        apR.forEach { it.reset() }
        pre.reset()
        outL = 0f
        outR = 0f
    }
}

class SoundEngine {
    private var fs = 44100
    private var reverb = Reverb(fs)
    private val eq = Array(2) { Array(SoundFx.BAND_HZ.size) { Biquad() } }
    private val eqOn = BooleanArray(SoundFx.BAND_HZ.size)
    private val shelf = Array(2) { Biquad() }
    private var shelfOn = false
    private var preamp = 1f

    private var mode = SpaceMode.OFF
    private var applied: SoundSettings? = null

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
    private val orbitDelay = DelayLine(512)
    private val orbitLp = Biquad()

    private var outL = 0f
    private var outR = 0f

    /** Call when the stream's sample rate is known or changes. */
    fun configure(sampleRate: Int) {
        fs = sampleRate
        reverb = Reverb(fs)
        applied = null
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
    }

    /** Picks up new settings (cheap when nothing changed). Call from the audio thread only. */
    fun apply(s: SoundSettings) {
        if (s === applied) return
        applied = s

        var maxBoost = 0f
        for (b in SoundFx.BAND_HZ.indices) {
            val db = s.bands.getOrElse(b) { 0f }
            eqOn[b] = abs(db) >= 0.1f && SoundFx.BAND_HZ[b] < fs * 0.45
            if (eqOn[b]) {
                for (ch in 0..1) eq[ch][b].peaking(fs, SoundFx.BAND_HZ[b].toDouble(), 0.9, db.toDouble())
                maxBoost = max(maxBoost, db)
            }
        }
        val bassDb = s.bass * 9.0
        shelfOn = bassDb >= 0.1
        if (shelfOn) {
            for (ch in 0..1) shelf[ch].lowShelf(fs, 120.0, bassDb)
            maxBoost = max(maxBoost, bassDb.toFloat())
        }
        // Make room for the boost so the loudest parts don't clip.
        preamp = 10f.pow(-maxBoost * 0.5f / 20f)

        mode = s.mode
        val i = s.intensity.coerceIn(0f, 1f)
        sideGain = 1f
        crossGain = 0f
        var mix = 0f
        when (mode) {
            SpaceMode.OFF -> {}
            SpaceMode.WIDE -> sideGain = 1f + 1.2f * i
            SpaceMode.HEADPHONE_3D -> {
                sideGain = 1f + 0.5f * i
                crossGain = 0.25f + 0.35f * i
                crossSamples = 0.00027f * fs
                for (ch in 0..1) crossLp[ch].lowPass(fs, 700.0, 0.7)
                mix = 0.05f + 0.08f * i
                setReverb(0.80f, 0.5f, 0.010f)
            }
            SpaceMode.HALL -> {
                sideGain = 1f + 0.2f * i
                mix = 0.12f + 0.28f * i
                setReverb(0.88f, 0.3f, 0.018f)
            }
            SpaceMode.CINEMA -> {
                sideGain = 1f + 0.8f * i
                mix = 0.08f + 0.14f * i
                setReverb(0.84f, 0.4f, 0.012f)
            }
            SpaceMode.ORBIT_8D -> {
                depth = i
                thetaStep = 2 * PI / (9.0 * fs) // one lap every 9 seconds
                maxItd = 0.0006f * fs
                orbitLp.lowPass(fs, 2500.0, 0.7)
                mix = 0.05f + 0.10f * i
                setReverb(0.80f, 0.5f, 0.008f)
            }
        }
        crossNorm = 1f / (1f + crossGain * 0.6f)
        reverbWet = mix * 3f

        bypass = !s.enabled || (mode == SpaceMode.OFF && !shelfOn && eqOn.none { it })
    }

    private fun setReverb(feedback: Float, damp: Float, preSeconds: Float) {
        reverb.feedback = feedback
        reverb.damp = damp
        reverb.preSamples = preSeconds * fs
    }

    /** Processes [frames] interleaved stereo frames in place (samples in -1..1). */
    fun process(buf: FloatArray, frames: Int) {
        if (bypass) return
        for (n in 0 until frames) {
            var l = buf[2 * n]
            var r = buf[2 * n + 1]

            for (b in eqOn.indices) if (eqOn[b]) {
                l = eq[0][b].process(l)
                r = eq[1][b].process(r)
            }
            if (shelfOn) {
                l = shelf[0].process(l)
                r = shelf[1].process(r)
            }
            l *= preamp
            r *= preamp

            space(l, r)

            buf[2 * n] = soft(outL)
            buf[2 * n + 1] = soft(outR)
        }
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
            outL += reverb.outL * reverbWet
            outR += reverb.outR * reverbWet
        }
    }

    /**
     * 8D: the centre of the mix travels in a circle around the head. Side to side is level plus a
     * small delay for the far ear; behind is muffled. A little of the original width stays.
     */
    private fun orbit(l: Float, r: Float) {
        theta += thetaStep
        if (theta > 2 * PI) theta -= 2 * PI
        val s = sin(theta).toFloat() // +1 = right ear, -1 = left ear
        val c = cos(theta).toFloat() // +1 = in front, -1 = behind

        val mid = (l + r) * 0.5f
        val side = (l - r) * 0.5f
        orbitDelay.write(mid)

        val angle = (s * depth * 0.8f + 1f) * (PI.toFloat() / 4f) // equal-power pan, never fully hard
        val gl = cos(angle) * 1.4142135f
        val gr = sin(angle) * 1.4142135f
        val dl = max(0f, s) * maxItd * depth // source on the right: the left ear hears it later
        val dr = max(0f, -s) * maxItd * depth

        val rear = max(0f, -c) * 0.7f * depth
        val front = 1f + 0.1f * c * depth
        val dull = orbitLp.process(mid) // keep the filter running so it never glitches
        val xl = orbitDelay.read(dl)
        val xr = orbitDelay.read(dr)
        val blendL = xl * (1 - rear) + dull * rear
        val blendR = xr * (1 - rear) + dull * rear

        val keep = 0.5f * (1f - depth * 0.5f)
        outL = blendL * gl * front + side * keep
        outR = blendR * gr * front - side * keep
    }

    private fun soft(x: Float): Float {
        val a = abs(x)
        if (a <= 0.8f) return x
        val y = 0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)
        return if (x < 0f) -y else y
    }
}
