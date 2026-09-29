package com.example.myapplication.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A hall reverb worked out in the player itself — Freeverb (Jezar's): eight damped comb filters
 * side by side and four all-pass filters after them, per channel, the right one's a little longer
 * for width. Android's own reverb, an effect on the whole output, is made but never applied on
 * some phones (a Pixel's audio system leaves it idle), so it can't be relied on.
 *
 * [amount] (0 to 100) is read on every buffer, so it changes as the track plays; at 0 the sound
 * goes through untouched. Cheap: a few dozen sums a sample, well under a percent of a weak phone.
 */
@UnstableApi
class ReverbAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var amount: Int = 0

    private var channels = 0
    private var combs: Array<Array<Comb>> = emptyArray()
    private var allPasses: Array<Array<AllPass>> = emptyArray()
    // Whether the filters hold anything: a reverb turned off rings out once, then is passed over.
    private var ringing = false
    // One frame's samples, kept: made anew for every frame, it was ninety thousand arrays a second.
    private val frame = FloatArray(2)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Only plain 16-bit sound, in one or two channels; anything else goes past it.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        val scale = inputAudioFormat.sampleRate / 44_100f
        combs = Array(channels) { channel ->
            Array(COMB_TUNING.size) { Comb(((COMB_TUNING[it] + channel * STEREO_SPREAD) * scale).toInt().coerceAtLeast(1)) }
        }
        allPasses = Array(channels) { channel ->
            Array(ALLPASS_TUNING.size) { AllPass(((ALLPASS_TUNING[it] + channel * STEREO_SPREAD) * scale).toInt().coerceAtLeast(1)) }
        }
        ringing = false
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val level = amount.coerceIn(0, 100) / 100f
        if (level == 0f && !ringing) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val wet = level * WET_SCALE
        val dry = 1f - level * DRY_DUCK
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        var loudest = 0f
        while (input.remaining() >= 2 * channels) {
            // One frame: the mono sum feeds both channels' filters, as Freeverb does.
            var sum = 0f
            for (channel in 0 until channels) {
                frame[channel] = input.short.toFloat() / 32_768f
                sum += frame[channel]
            }
            val feed = sum / channels * INPUT_GAIN
            for (channel in 0 until channels) {
                var out = 0f
                for (comb in combs[channel]) out += comb.process(feed)
                for (pass in allPasses[channel]) out = pass.process(out)
                loudest = maxOf(loudest, kotlin.math.abs(out))
                val mixed = frame[channel] * dry + out * wet
                output.putShort((mixed.coerceIn(-1f, 1f) * 32_767f).toInt().toShort())
            }
        }
        // Rung out: nothing left in the filters worth hearing.
        ringing = level > 0f || loudest > SILENT
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        combs.forEach { row -> row.forEach(Comb::clear) }
        allPasses.forEach { row -> row.forEach(AllPass::clear) }
        ringing = false
    }

    override fun onReset() {
        combs = emptyArray()
        allPasses = emptyArray()
        channels = 0
        ringing = false
    }

    /** A comb filter with a low-pass in its loop: a hall's echo, dulled a little each time round. */
    private class Comb(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var filtered = 0f

        fun process(input: Float): Float {
            val out = buffer[index]
            filtered = out * (1f - DAMP) + filtered * DAMP
            buffer[index] = input + filtered * FEEDBACK
            if (++index == buffer.size) index = 0
            return out
        }

        fun clear() {
            buffer.fill(0f)
            filtered = 0f
        }
    }

    /** An all-pass filter: smears the echoes into a wash without colouring them. */
    private class AllPass(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val buffered = buffer[index]
            buffer[index] = input + buffered * 0.5f
            if (++index == buffer.size) index = 0
            return buffered - input
        }

        fun clear() = buffer.fill(0f)
    }

    private companion object {
        // Freeverb's delay lengths at 44.1 kHz, and the right channel's offset.
        val COMB_TUNING = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS_TUNING = intArrayOf(556, 441, 341, 225)
        const val STEREO_SPREAD = 23
        // A large room, lightly damped: a hall.
        const val FEEDBACK = 0.84f * 0.28f + 0.7f
        const val DAMP = 0.2f
        const val INPUT_GAIN = 0.015f
        // At full, the reverb loud and the dry sound a quarter down, so the whole doesn't clip.
        const val WET_SCALE = 3f
        const val DRY_DUCK = 0.25f
        const val SILENT = 1e-4f
    }
}
