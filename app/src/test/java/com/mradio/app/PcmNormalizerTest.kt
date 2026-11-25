package com.mradio.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmNormalizerTest {
    private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())

    @Test fun bypassPreservesEveryByteAcrossAllFrameSplits() {
        for (channels in 1..2) for (floating in listOf(false, true)) {
            val frame = channels * if (floating) 4 else 2
            val data = ByteArray(frame * 11) { (it * 31).toByte() }
            for (split in 0..data.size) {
                val pcm = PcmNormalizer()
                val settings = SoundSettings(false)
                pcm.configure(48000, settings)
                val out = buffer(ByteArray(data.size))
                for (part in listOf(data.copyOfRange(0, split), data.copyOfRange(split, data.size))) {
                    val expected = pcm.outputSize(part.size, frame)
                    val before = out.position()
                    pcm.process(buffer(part), out, channels, floating, settings)
                    assertEquals(expected, out.position() - before)
                }
                assertArrayEquals(data, out.array())
            }
        }
    }

    @Test fun resetDiscardsOldStreamPartialFrame() {
        val pcm = PcmNormalizer()
        val settings = SoundSettings(false)
        pcm.configure(44100, settings)
        pcm.process(buffer(byteArrayOf(99)), buffer(ByteArray(0)), 1, false, settings)
        pcm.reset()
        val out = buffer(ByteArray(2))
        pcm.process(buffer(byteArrayOf(1, 2)), out, 1, false, settings)
        assertArrayEquals(byteArrayOf(1, 2), out.array())
    }

    @Test fun activeNormalizationPreservesChannelCountAndFiniteSamples() {
        for (channels in 1..2) {
            val pcm = PcmNormalizer()
            val settings = SoundSettings(true)
            pcm.configure(48000, settings)
            val input = buffer(ByteArray(channels * 4 * 3))
            repeat(channels * 3) { input.putFloat(if (it == 0) Float.NaN else 1f) }
            input.flip()
            val out = buffer(ByteArray(input.remaining()))
            pcm.process(input, out, channels, true, settings)
            assertEquals(channels * 12, out.position())
            out.flip()
            while (out.hasRemaining()) assertTrue(out.float in -1f..1f)
        }
    }
    @Test fun activeNormalizationIsIndependentOfBufferSplits() {
        for (channels in 1..2) for (floating in listOf(false, true)) {
            val frame = channels * if (floating) 4 else 2
            val input = buffer(ByteArray(frame * 20))
            repeat(channels * 20) { i ->
                val value = if (i % 3 == 0) 0.85f else -0.23f
                if (floating) input.putFloat(value) else input.putShort((value * 32768f).toInt().toShort())
            }
            val data = input.array()
            fun render(split: Int): ByteArray {
                val pcm = PcmNormalizer()
                val settings = SoundSettings(true)
                pcm.configure(48000, settings)
                val out = buffer(ByteArray(data.size))
                for (part in listOf(data.copyOfRange(0, split), data.copyOfRange(split, data.size)))
                    pcm.process(buffer(part), out, channels, floating, settings)
                assertEquals(data.size, out.position())
                return out.array()
            }
            val reference = render(data.size)
            for (split in 0..data.size) assertArrayEquals(reference, render(split))
        }
    }
}
