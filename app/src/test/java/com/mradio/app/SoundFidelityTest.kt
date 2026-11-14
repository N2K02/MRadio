package com.mradio.app

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class SoundFidelityTest {
    private fun distortion(b: FloatArray, rate: Int, hz: Double): Double {
        var sinSum=0.0; var cosSum=0.0; var power=0.0
        val frames=b.size/2; val start=frames/2; val count=frames-start
        for (i in start until frames) {
            val x=b[2*i].toDouble(); val phase=2*PI*hz*i/rate
            sinSum+=x*sin(phase); cosSum+=x*cos(phase); power+=x*x
        }
        val fundamental=2*(sinSum*sinSum+cosSum*cosSum)/count
        return sqrt(max(0.0,power-fundamental)/fundamental)
    }
    @Test fun allPresetsWithExtraBassKeepHighLevelTonesClean() {
        for (rate in listOf(44100,48000)) for ((name,bands) in SoundFx.PRESETS) for (hz in listOf(60.0,230.0,910.0,3600.0,14000.0)) {
            val engine=SoundEngine().apply { configure(rate); apply(SoundSettings(normalizeVolume=false,bands=bands,bass=1f)) }
            val b=FloatArray(rate*4) { i -> (0.95*sin(2*PI*hz*(i/2)/rate)).toFloat() }
            engine.process(b,rate*2)
            assertTrue("$name $rate $hz peak", b.all { it.isFinite() && abs(it)<=0.981f })
            assertTrue("$name $rate $hz harmonic distortion",distortion(b,rate,hz)<0.005)
        }
    }
    @Test fun zeroIntensityMeansUnchangedStereoForEveryMode() {
        for (mode in SpaceMode.entries) {
            val e=SoundEngine().apply { configure(48000); apply(SoundSettings(normalizeVolume=false,mode=mode,intensity=0f)) }
            val b=FloatArray(2000) { if (it%2==0) .9f else -.3f }; val original=b.copyOf()
            e.process(b,b.size/2)
            assertArrayEquals(mode.name,original,b,0f)
        }
    }
    @Test fun concertPreservesDryStereoAndHasNoArtificialHarmonics() {
        val rate=48000
        val e=SoundEngine().apply { configure(rate); apply(SoundSettings(normalizeVolume=false,mode=SpaceMode.HALL,intensity=1f)) }
        val b=FloatArray(rate*4) { if (it%2==0) (.95*sin(2*PI*1000*(it/2)/rate)).toFloat() else 0f }
        e.process(b,rate*2)
        assertTrue(b.indices.filter { it%2==1 }.all { b[it]==0f })
        assertTrue(distortion(b,rate,1000.0)<0.005)
    }
    @Test fun changingModesFadesAndNeverReplaysAnOldTail() {
        val e=SoundEngine().apply { configure(48000); apply(SoundSettings(normalizeVolume=false,mode=SpaceMode.HALL)) }
        val signal=FloatArray(4800) { .3f }; e.process(signal,2400)
        e.apply(SoundSettings(normalizeVolume=false,mode=SpaceMode.OFF))
        val silence=FloatArray(4800); e.process(silence,2400)
        e.apply(SoundSettings(normalizeVolume=false,mode=SpaceMode.HALL))
        val after=FloatArray(4800); e.process(after,2400)
        assertTrue(after.all { abs(it)<1e-6f })
    }
    @Test fun corruptSamplesDoNotPoisonEqOrSpatialState() {
        for (mode in SpaceMode.entries) {
            val e = SoundEngine().apply {
                configure(48000)
                apply(SoundSettings(bands = listOf(6f, 4f, 0f, 0f, 0f), mode = mode, intensity = 1f))
            }
            val corrupt = FloatArray(1024)
            corrupt[0] = Float.NaN; corrupt[1] = Float.POSITIVE_INFINITY; corrupt[9] = Float.NEGATIVE_INFINITY
            e.process(corrupt, corrupt.size / 2)
            assertTrue(mode.name, corrupt.all { it.isFinite() })
            val after = FloatArray(48000) { (.3 * sin(it / 2 * .13)).toFloat() }
            e.process(after, after.size / 2)
            assertTrue(mode.name, after.all { it.isFinite() && abs(it) <= .981f })
            assertTrue("$mode recovers", after.any { abs(it) > .01f })
        }
    }

}
