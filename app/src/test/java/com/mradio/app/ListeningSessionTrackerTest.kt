package com.mradio.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningSessionTrackerTest {
    private data class Interval(val millis: Long, val bitrate: Int?, val transport: String)

    @Test fun pauseAndBufferDoNotCount() {
        var clock = 1_000L
        val items = mutableListOf<Interval>()
        val tracker = ListeningSessionTracker({ clock }) { ms, bitrate, transport ->
            items.add(Interval(ms, bitrate, transport))
        }
        tracker.start("wifi", 128)
        clock += 12_000
        tracker.stop()
        clock += 100_000 // Paused time.
        tracker.stop()
        tracker.start("wifi", 128)
        clock += 3_000
        tracker.stop()
        assertEquals(15_000L, items.sumOf { it.millis })
    }

    @Test fun splitsByNetworkAndBitrateWithoutDoubleCounting() {
        var clock = 100L
        val items = mutableListOf<Interval>()
        val tracker = ListeningSessionTracker({ clock }) { ms, bitrate, transport ->
            items.add(Interval(ms, bitrate, transport))
        }
        tracker.start("wifi", 128)
        clock += 4_000
        tracker.update("mobile", 64)
        clock += 6_000
        tracker.checkpoint()
        tracker.stop()
        assertEquals(listOf(Interval(4_000, 128, "wifi"), Interval(6_000, 64, "mobile")), items)
    }

    @Test fun longSessionsAreChunkedAndNegativeClockMovementIsIgnored() {
        var clock = 50_000L
        val items = mutableListOf<Interval>()
        val tracker = ListeningSessionTracker({ clock }) { ms, bitrate, transport ->
            items.add(Interval(ms, bitrate, transport))
        }
        tracker.start("unknown", null)
        clock += 125_000
        tracker.stop()
        clock -= 10_000
        tracker.stop()
        assertEquals(listOf(60_000L, 60_000L, 5_000L), items.map { it.millis })
    }
}
