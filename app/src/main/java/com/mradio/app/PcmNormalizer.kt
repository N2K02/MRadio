package com.mradio.app

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Frame assembly and linked PCM gain; independent of Android for regression tests. */
internal class PcmNormalizer {
    private val engine = SoundEngine()
    private val partial = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())

    fun configure(rate: Int, settings: SoundSettings) {
        partial.clear()
        engine.configure(rate)
        engine.apply(settings)
        engine.reset()
    }

    fun reset() {
        partial.clear()
        engine.reset()
    }

    fun outputSize(inputBytes: Int, frameBytes: Int): Int =
        (partial.position() + inputBytes) / frameBytes * frameBytes

    fun process(inputBuffer: ByteBuffer, out: ByteBuffer, channels: Int, isFloat: Boolean, settings: SoundSettings) {
        require(channels in 1..2)
        engine.apply(settings)
        val mono = channels == 1
        val frameBytes = (if (isFloat) 4 else 2) * channels
        if (partial.position() > 0) {
            copyBytes(inputBuffer, partial, minOf(frameBytes - partial.position(), inputBuffer.remaining()))
            if (partial.position() == frameBytes) {
                partial.flip()
                renderFrame(partial, out, mono, isFloat, frameBytes)
                partial.clear()
            }
        }
        while (inputBuffer.remaining() >= frameBytes) {
            if (engine.bypass) {
                val count = inputBuffer.remaining() / frameBytes * frameBytes
                copyBytes(inputBuffer, out, count)
            } else renderFrame(inputBuffer, out, mono, isFloat, frameBytes)
        }
        copyBytes(inputBuffer, partial, inputBuffer.remaining())
    }

    private fun copyBytes(source: ByteBuffer, target: ByteBuffer, count: Int) {
        val limit = source.limit()
        source.limit(source.position() + count)
        target.put(source)
        source.limit(limit)
    }

    private fun renderFrame(input: ByteBuffer, output: ByteBuffer, mono: Boolean, isFloat: Boolean, frameBytes: Int) {
        if (engine.bypass) {
            copyBytes(input, output, frameBytes)
            return
        }
        val l = readSample(input, isFloat)
        val r = if (mono) l else readSample(input, isFloat)
        val gain = engine.nextGain(l, r)
        writeSample(output, l * gain, isFloat)
        if (!mono) writeSample(output, r * gain, isFloat)
    }

    private fun readSample(b: ByteBuffer, isFloat: Boolean): Float {
        val value = if (isFloat) b.getFloat() else b.getShort() / 32768f
        return if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    }

    private fun writeSample(b: ByteBuffer, value: Float, isFloat: Boolean) {
        if (isFloat) b.putFloat(value)
        else b.putShort((value * 32768f).toInt().coerceIn(-32768, 32767).toShort())
    }

}
