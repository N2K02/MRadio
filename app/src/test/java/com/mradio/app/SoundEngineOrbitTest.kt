package com.mradio.app

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class SoundEngineOrbitTest {
    private val fs = 48000

    /** Plays [seconds] of a mono 1 kHz tone through 8D and returns per-quarter-lap L/R energy. */
    private fun orbitEnergy(seconds: Double): List<Pair<Double, Double>> {
        val engine = SoundEngine().apply {
            configure(fs)
            apply(SoundSettings(normalizeVolume = false, mode = SpaceMode.ORBIT_8D, intensity = 1f))
        }
        val frames = (seconds * fs).toInt()
        val block = 1024
        val buf = FloatArray(block * 2)
        val quarter = (9.0 * fs / 4).toInt()
        val out = MutableList(4) { 0.0 to 0.0 }
        var n = 0
        while (n < frames) {
            val len = minOf(block, frames - n)
            for (i in 0 until len) {
                val x = (0.3 * sin(2 * PI * 1000 * (n + i) / fs)).toFloat()
                buf[2 * i] = x; buf[2 * i + 1] = x
            }
            engine.process(buf, len)
            for (i in 0 until len) {
                val l = buf[2 * i]; val r = buf[2 * i + 1]
                assertTrue("finite and limited", l.isFinite() && r.isFinite() && abs(l) <= 1f && abs(r) <= 1f)
                val q = ((n + i) / quarter).coerceAtMost(3)
                val (el, er) = out[q]
                out[q] = (el + l * l) to (er + r * r)
            }
            n += len
        }
        return out
    }

    @Test fun orbitStillCirclesBothEarsWithBlockRatePan() {
        val q = orbitEnergy(9.0)
        // Quarter 1 (0..2.25 s) source swings right; quarter 3 (4.5..6.75 s) it is on the left.
        assertTrue("right first", q[0].second > q[0].first * 1.5)
        assertTrue("left later", q[2].first > q[2].second * 1.5)
    }

    @Test fun loudnessStaysEqualPowerAroundTheCircle() {
        val q = orbitEnergy(9.0)
        val totals = q.map { sqrt(it.first + it.second) }
        val min = totals.min(); val max = totals.max()
        assertTrue("no level jumps from the block pan: $totals", max / min < 1.6)
    }
}
