package com.mradio.app

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Normalizes PCM without altering the source channel count or encoding. */
@OptIn(UnstableApi::class)
class SoundProcessor : BaseAudioProcessor() {
    private val engine = SoundEngine()
    private val partial = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
    private var lastStreamGeneration = SoundFx.streamGeneration

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val pcm = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!pcm || inputAudioFormat.channelCount !in 1..2) return AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val generation = SoundFx.streamGeneration
        if (generation != lastStreamGeneration) {
            lastStreamGeneration = generation
            engine.resetNormalization()
            partial.clear()
        }
        engine.apply(SoundFx.settings.value)
        val mono = inputAudioFormat.channelCount == 1
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val frameBytes = (if (isFloat) 4 else 2) * inputAudioFormat.channelCount
        val out = replaceOutputBuffer((partial.position() + inputBuffer.remaining()) / frameBytes * frameBytes)
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
        out.flip()
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
        return if (value.isFinite()) value else 0f
    }

    private fun writeSample(b: ByteBuffer, value: Float, isFloat: Boolean) {
        if (isFloat) b.putFloat(value)
        else b.putShort((value * 32768f).toInt().coerceIn(-32768, 32767).toShort())
    }

    override fun onFlush() {
        partial.clear()
        if (inputAudioFormat.sampleRate > 0) engine.configure(inputAudioFormat.sampleRate)
        engine.apply(SoundFx.settings.value)
        engine.reset()
    }

    override fun onReset() {
        partial.clear()
    }
}
