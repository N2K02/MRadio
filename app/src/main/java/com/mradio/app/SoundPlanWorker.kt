package com.mradio.app

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A complete settings/headroom pair; never apply new boosts with an old gain. */
internal data class PreparedSoundSettings(val settings: SoundSettings, val rate: Int, val gain: Float)

/** Latest-wins preparation. The render thread only publishes requests and reads results. */
internal class SoundPlanWorker(
    private val executor: Executor = background,
    private val prepare: (SoundSettings, Int) -> Float = { s, rate ->
        MusicHeadroom.gain(s.bands, s.bass * 9.0, rate)
    },
) {
    private data class Request(val settings: SoundSettings, val rate: Int)
    @Volatile private var requested: Request? = null
    @Volatile var ready: PreparedSoundSettings? = null
        private set
    private val scheduled = AtomicBoolean(false)

    fun request(settings: SoundSettings, rate: Int) {
        val old = requested
        if (old?.settings === settings && old.rate == rate) return
        requested = Request(settings, rate)
        schedule()
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        executor.execute {
            var completed: Request? = null
            try {
                while (true) {
                    val next = requested ?: break
                    completed = next
                    val gain = prepare(next.settings, next.rate)
                    // A route or slider change invalidates the entire old result.
                    if (requested === next) {
                        ready = PreparedSoundSettings(next.settings, next.rate, gain)
                        break
                    }
                }
            } finally {
                scheduled.set(false)
                // Close the race with a request arriving just before releasing scheduled.
                if (requested !== completed) schedule()
            }
        }
    }

    companion object {
        private val background = Executors.newSingleThreadExecutor { task ->
            Thread(task, "mradio-eq-plan").apply { isDaemon = true }
        }
    }
}
