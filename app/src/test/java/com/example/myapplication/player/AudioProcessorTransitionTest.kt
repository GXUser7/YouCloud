package com.example.myapplication.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.example.myapplication.player.restore.RestoreAudioProcessor
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class AudioProcessorTransitionTest {
    private val youtube = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
    private val mp3 = AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
    private val monoMp3 = AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT)

    @Test
    fun preparingMp3DoesNotDiscardDownloadedAudioStillBuffered() {
        assertPendingFormatDoesNotChangeAudio(youtube, mp3) {
            AudioProcessingPipeline(ImmutableList.of(RestoreAudioProcessor()))
        }
    }

    @Test
    fun reverbKeepsCurrentChannelsAndEchoUntilTheNextFormatIsApplied() {
        assertPendingFormatDoesNotChangeAudio(youtube, monoMp3) {
            AudioProcessingPipeline(ImmutableList.of(ReverbAudioProcessor().apply { amount = 60 }))
        }
    }

    @Test
    fun gainKeepsCurrentChannelLayoutUntilTheNextFormatIsApplied() {
        assertPendingFormatDoesNotChangeAudio(youtube, monoMp3) {
            AudioProcessingPipeline(ImmutableList.of(GainAudioProcessor().apply { percent = 175 }))
        }
    }

    @Test
    fun restorationPreservesItsTailWhenUpstreamSignalsEndAgain() {
        val processor = RestoreAudioProcessor()
        processor.configure(youtube)
        processor.flush()
        val input = audio(youtube.channelCount)
        val buffer = ByteBuffer.allocateDirect(input.size).order(ByteOrder.nativeOrder()).put(input)
        buffer.flip()
        processor.queueInput(buffer)
        val beginning = processor.output.remaining()
        processor.queueEndOfStream()
        val tail = processor.output
        assertEquals("the buffered tail must complete the old track", input.size - beginning, tail.remaining())
        val expected = ByteArray(tail.remaining()).also { tail.duplicate().get(it) }
        // AudioProcessingPipeline can signal EOS again while its preceding processor is ended.
        // A tail already handed to the pipeline must remain valid until it is consumed.
        processor.queueEndOfStream()
        val actual = ByteArray(tail.remaining()).also(tail::get)
        assertArrayEquals("a repeated end signal must not clear an outstanding output buffer", expected, actual)
        assertTrue(processor.isEnded)
    }

    @Test
    fun downloadedAudioAndMp3CanAlternateIncludingMonoAndRestorationBypass() {
        val formats = listOf(youtube, mp3, monoMp3, AudioFormat(22_050, 1, C.ENCODING_PCM_16BIT), youtube)
        val pipeline = pipeline()
        pipeline.configure(formats.first())
        pipeline.flush()
        for ((index, format) in formats.withIndex()) {
            val input = audio(format.channelCount)
            val expected = pipeline().let { reference ->
                reference.configure(format)
                reference.flush()
                play(reference, input)
            }
            val actual = play(pipeline, input, formats.getOrNull(index + 1))
            assertEquals("track $index must retain its PCM frame count", input.size, actual.size)
            assertArrayEquals("track $index must not inherit the preceding track's format", expected, actual)
            if (index < formats.lastIndex) pipeline.flush()
        }
    }

    private fun pipeline() = AudioProcessingPipeline(
        // A passthrough before restoration also exercises repeated EOS propagation from an
        // upstream processor, as in the sink's resampling/channel-mapping/trimming chain.
        ImmutableList.of(GainAudioProcessor(), RestoreAudioProcessor(), ReverbAudioProcessor(), GainAudioProcessor())
    )

    private fun assertPendingFormatDoesNotChangeAudio(
        current: AudioFormat,
        next: AudioFormat,
        makePipeline: () -> AudioProcessingPipeline
    ) {
        val input = audio(current.channelCount)
        fun render(nextFormat: AudioFormat?): ByteArray {
            val pipeline = makePipeline()
            pipeline.configure(current)
            pipeline.flush()
            return play(pipeline, input, nextFormat)
        }
        assertArrayEquals("configure must leave the current stream intact until flush", render(null), render(next))
    }

    private fun audio(channels: Int): ByteArray {
        // Not a whole restoration hop: there must still be a tail to drain at the transition.
        val buffer = ByteBuffer.allocate(4097 * channels * 2).order(ByteOrder.nativeOrder())
        val random = Random(7)
        repeat(4097 * channels) { buffer.putShort(random.nextInt(-28_000, 28_000).toShort()) }
        return buffer.array()
    }

    private fun play(pipeline: AudioProcessingPipeline, input: ByteArray, next: AudioFormat? = null): ByteArray {
        val out = ByteArrayOutputStream()
        fun drain() {
            val buffer = pipeline.output
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            out.write(bytes)
        }
        val buffer = ByteBuffer.allocateDirect(input.size).order(ByteOrder.nativeOrder()).put(input)
        buffer.flip()
        // Media3 configures the next stream before draining the old one. Audio queued in between
        // still has the old format, including its buffered restoration/reverb samples.
        val tail = buffer.limit()
        buffer.limit(tail - 1024)
        pipeline.queueInput(buffer)
        drain()
        next?.let(pipeline::configure)
        buffer.limit(tail)
        pipeline.queueInput(buffer)
        assertTrue("the old-format input must be consumed", !buffer.hasRemaining())
        drain()
        pipeline.queueEndOfStream()
        repeat(16) {
            if (pipeline.isEnded) return out.toByteArray()
            drain()
        }
        throw AssertionError("audio processors did not drain at the track transition")
    }
}
