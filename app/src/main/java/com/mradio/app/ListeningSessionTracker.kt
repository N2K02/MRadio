package com.mradio.app

/**
 * Monotonic, allocation-light accounting for time when Media3 actually reports playback.
 * Calling checkpoint/stop more than once at the same instant never double-counts an interval.
 * The transport and bitrate apply to the interval *before* metadata changes.
 */
internal class ListeningSessionTracker(
    private val nowMs: () -> Long,
    private val record: (durationMs: Long, bitrateKbps: Int?, transport: String) -> Unit,
) {
    private var startedAt: Long? = null
    private var bitrate: Int? = null
    private var transport = "unknown"

    fun start(transport: String, bitrateKbps: Int?) {
        if (startedAt != null) return
        this.transport = transport
        this.bitrate = bitrateKbps
        startedAt = nowMs()
    }

    fun checkpoint() {
        val start = startedAt ?: return
        val now = nowMs()
        val elapsed = (now - start).coerceAtLeast(0)
        startedAt = now
        var left = elapsed
        while (left > 0L) {
            val chunk = minOf(left, 60_000L)
            record(chunk, bitrate, transport)
            left -= chunk
        }
    }

    fun update(transport: String, bitrateKbps: Int?) {
        if (startedAt == null) return
        if (this.transport == transport && this.bitrate == bitrateKbps) return
        checkpoint()
        this.transport = transport
        this.bitrate = bitrateKbps
    }

    fun stop() {
        checkpoint()
        startedAt = null
    }

    val isActive: Boolean get() = startedAt != null
}
