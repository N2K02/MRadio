package com.mradio.app

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Normalizes PCM without altering the source channel count or encoding. */
@OptIn(UnstableApi::class)
class SoundProcessor : BaseAudioProcessor() {
    private val pcm = PcmNormalizer()
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
            pcm.reset()
        }
        val channels = inputAudioFormat.channelCount
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val frameBytes = (if (isFloat) 4 else 2) * channels
        val out = replaceOutputBuffer(pcm.outputSize(inputBuffer.remaining(), frameBytes))
        pcm.process(inputBuffer, out, channels, isFloat, SoundFx.settings.value)
        out.flip()
    }

    override fun onFlush() {
        lastStreamGeneration = SoundFx.streamGeneration
        if (inputAudioFormat.sampleRate > 0)
            pcm.configure(inputAudioFormat.sampleRate, SoundFx.settings.value)
        else pcm.reset()
    }

    override fun onReset() = pcm.reset()
}
