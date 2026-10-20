package com.mradio.app

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Sits in the player's audio path and runs every buffer through [SoundEngine] with the current
 * [SoundFx] settings. Mono stations are turned into stereo first, so the equalizer and the 3D
 * modes work on them too. Anything else (surround, non-PCM) passes through untouched.
 */
@OptIn(UnstableApi::class)
class SoundProcessor : BaseAudioProcessor() {
    private val engine = SoundEngine()
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val pcm = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!pcm || inputAudioFormat.channelCount !in 1..2) return AudioFormat.NOT_SET
        engine.configure(inputAudioFormat.sampleRate)
        return AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        engine.apply(SoundFx.settings.value)

        val mono = inputAudioFormat.channelCount == 1
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytes = if (isFloat) 4 else 2
        val frames = size / (bytes * if (mono) 1 else 2)
        val out = replaceOutputBuffer(frames * 2 * bytes)

        if (engine.bypass && !mono) {
            // Nothing to change: hand the audio on exactly as it came.
            val end = inputBuffer.position() + frames * 2 * bytes
            val keepLimit = inputBuffer.limit()
            inputBuffer.limit(end)
            out.put(inputBuffer)
            inputBuffer.limit(keepLimit)
        } else {
            val samples = frames * 2
            if (scratch.size < samples) scratch = FloatArray(samples)
            for (n in 0 until frames) {
                val l = readSample(inputBuffer, isFloat)
                val r = if (mono) l else readSample(inputBuffer, isFloat)
                scratch[2 * n] = l
                scratch[2 * n + 1] = r
            }
            engine.process(scratch, frames) // does nothing when bypassed
            for (i in 0 until samples) {
                if (isFloat) {
                    out.putFloat(scratch[i])
                } else {
                    out.putShort((scratch[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit()) // a partial frame, if any, is dropped
        out.flip()
    }

    private fun readSample(b: ByteBuffer, isFloat: Boolean): Float =
        if (isFloat) b.getFloat() else b.getShort() / 32768f

    override fun onFlush() = engine.reset()

    override fun onReset() {
        scratch = FloatArray(0)
    }
}
