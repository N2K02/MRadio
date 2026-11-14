package com.mradio.app

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class SoundPlanWorkerTest {
    private class Queue : Executor {
        val tasks = java.util.ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun runNext() = tasks.remove().run()
    }

    @Test fun renderRequestsNeverCalculateHeadroomAndRapidChangesCoalesce() {
        val queue = Queue()
        var scans = 0
        val worker = SoundPlanWorker(queue) { s, rate ->
            scans++
            MusicHeadroom.gain(s.bands, s.bass * 9.0, rate)
        }
        var latest = SoundSettings()
        repeat(100) {
            latest = SoundSettings(bass = it / 100f)
            worker.request(latest, 48000)
        }
        assertEquals(0, scans)
        assertNull(worker.ready)
        assertEquals(1, queue.tasks.size)
        queue.runNext()
        assertEquals(1, scans)
        assertSame(latest, worker.ready!!.settings)
        assertEquals(MusicHeadroom.gain(latest.bands, latest.bass * 9.0), worker.ready!!.gain, 0f)
        repeat(100) { worker.request(latest, 48000) }
        assertTrue(queue.tasks.isEmpty())
    }

    @Test fun settingsAndRouteChangesDuringPreparationDiscardTheStaleResult() {
        val queue = Queue()
        val old = SoundSettings(bass = 1f)
        val latest = SoundSettings(bands = listOf(6f, 4f, 0f, 0f, 0f))
        lateinit var worker: SoundPlanWorker
        var scans = 0
        worker = SoundPlanWorker(queue) { _, rate ->
            scans++
            if (scans == 1) worker.request(latest, 44100)
            rate / 48000f
        }
        worker.request(old, 48000)
        queue.runNext()
        assertEquals(2, scans)
        assertSame(latest, worker.ready!!.settings)
        assertEquals(44100, worker.ready!!.rate)
        assertEquals(44100f / 48000f, worker.ready!!.gain, 0f)
        assertTrue(queue.tasks.isEmpty())
    }

    @Test fun preparedPresetProducesTheSameAudioAsSynchronousPreparation() {
        for (rate in listOf(44100, 48000)) for ((_, bands) in SoundFx.PRESETS) {
            val settings = SoundSettings(bands = bands, bass = 1f, normalizeVolume = false)
            val direct = SoundEngine().apply { configure(rate); apply(settings) }
            val prepared = SoundEngine().apply {
                configure(rate)
                applyPrepared(settings, MusicHeadroom.gain(bands, 9.0, rate))
            }
            val a = FloatArray(4096) { kotlin.math.sin(it * 0.1).toFloat() * .9f }
            val b = a.copyOf()
            direct.process(a, a.size / 2); prepared.process(b, b.size / 2)
            assertArrayEquals(a, b, 0f)
        }
    }
}
