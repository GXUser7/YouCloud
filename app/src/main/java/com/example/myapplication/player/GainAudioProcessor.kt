package com.example.myapplication.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp

/**
 * A track's own volume ([com.example.myapplication.data.TrackFx.volume]), worked out in the player
 * itself, apart from the phone's: a track mastered too quiet made louder, one too loud made
 * quieter. The player's own volume can't go past 1, so louder is done here, on the samples.
 *
 * Made louder, a track's peaks would clip — crackle — so a limiter follows: where a sample would go
 * past the ceiling the whole frame is turned down at once, just enough, and comes back up over a
 * fraction of a second. A change of [percent] glides over a few hundredths of a second rather than
 * jumping. At 100 the sound goes through untouched.
 */
@UnstableApi
class GainAudioProcessor : BaseAudioProcessor() {
    /** The track's volume in percent of its own: 100 as recorded. Read on every buffer. */
    @Volatile
    var percent: Int = 100

    private var channels = 0
    // The gain heard now, gliding to the one asked for.
    private var gain = 1f
    // How much the limiter has the sound turned down, 1 for not at all.
    private var reduction = 1f
    private var glide = 0f
    private var release = 0f
    // One frame's samples, kept: not an array made for every frame.
    private val frame = FloatArray(2)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Only plain 16-bit sound, in one or two channels; anything else goes past it.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate.toFloat()
        glide = 1f - exp(-1f / (GLIDE_SECONDS * rate))
        release = 1f - exp(-1f / (RELEASE_SECONDS * rate))
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val target = percent.coerceIn(10, 1_000) / 100f
        if (target == 1f && gain == 1f && reduction >= 1f) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        while (input.remaining() >= 2 * channels) {
            gain += (target - gain) * glide
            if (abs(target - gain) < 1e-4f) gain = target
            var peak = 0f
            for (channel in 0 until channels) {
                val sample = input.short.toFloat() / 32_768f * gain
                frame[channel] = sample
                peak = maxOf(peak, abs(sample))
            }
            // Down at once as far as this frame needs, back up slowly.
            val needed = if (peak > CEILING) CEILING / peak else 1f
            reduction = if (needed < reduction) needed else reduction + (needed - reduction) * release
            if (reduction > 0.9999f) reduction = 1f
            for (channel in 0 until channels) {
                val out = (frame[channel] * reduction).coerceIn(-1f, 1f)
                output.putShort((out * 32_767f).toInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        reduction = 1f
        gain = percent.coerceIn(10, 1_000) / 100f
    }

    override fun onReset() {
        channels = 0
        gain = 1f
        reduction = 1f
    }

    private companion object {
        // Just under full scale: what the limiter keeps the peaks to.
        const val CEILING = 0.97f
        // How long a change of volume takes, and the limiter to let go.
        const val GLIDE_SECONDS = 0.04f
        const val RELEASE_SECONDS = 0.25f
    }
}
