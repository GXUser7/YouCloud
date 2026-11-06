package com.example.myapplication.player.restore

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.random.Random

class RestoreAudioProcessorTest {
    private val rate = 44_100
    private val size = 1 shl 18 // about six seconds

    @Test
    fun fullBandSoundPassesUntouched() {
        val input = noise(channels = 2, cutoffHz = null)
        var reported = false
        val output = run(input, channels = 2, model = flatModel()) { reported = reported || it }
        assertArrayEquals("a lossless file is not changed by a bit", input, output)
        assertFalse(reported)
    }

    @Test
    fun lossyFileGetsItsTopBack() {
        val input = noise(channels = 2, cutoffHz = 15_000f)
        var reported = false
        val output = run(input, channels = 2, model = flatModel()) { reported = reported || it }
        assertEquals("as long as it came", input.size, output.size)
        assertTrue("+AI shown", reported)
        val before = bandDb(input, 2, 16_000f, 20_000f)
        val after = bandDb(output, 2, 16_000f, 20_000f)
        val body = bandDb(input, 2, 2_000f, 8_000f)
        assertTrue("nothing over the cutoff before: $before dB", before < body - 60)
        // The model says -20 dB under the anchor: the top comes back, well under the body.
        assertTrue("the top is back: $after dB against $body dB", after > body - 45 && after < body)
    }

    @Test
    fun aLosslessTrackAfterALossyOneIsLeftAloneAtOnce() {
        // Gapless: one stream, a lossy track and then a lossless one, nothing flushed between.
        val lossy = noise(channels = 2, cutoffHz = 15_000f)
        val lossless = noise(channels = 2, cutoffHz = null, seed = 9)
        val output = run(lossy + lossless, channels = 2, model = flatModel())
        val half = rate // half a second, two channels interleaved
        val tail = output.copyOfRange(lossy.size + half, output.size)
        val expected = lossless.copyOfRange(half, lossless.size)
        assertArrayEquals("half a second in, the lossless track is as it came", expected, tail)
    }

    @Test
    fun switchedOffLeavesEvenALossyFileAlone() {
        val input = noise(channels = 2, cutoffHz = 15_000f)
        val output = run(input, channels = 2, model = flatModel(), enabled = false)
        assertArrayEquals(input, output)
    }

    @Test
    fun monoToo() {
        val input = noise(channels = 1, cutoffHz = 14_000f)
        var reported = false
        val output = run(input, channels = 1, model = flatModel()) { reported = reported || it }
        assertEquals(input.size, output.size)
        assertTrue(reported)
        assertTrue(bandDb(output, 1, 15_000f, 20_000f) > bandDb(input, 1, 15_000f, 20_000f) + 30)
    }

    private fun run(
        input: ShortArray,
        channels: Int,
        model: RestoreModel,
        enabled: Boolean = true,
        onShown: (Boolean) -> Unit = {}
    ): ShortArray {
        val processor = RestoreAudioProcessor(onShown)
        processor.enabled = enabled
        processor.model = model
        processor.configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        processor.flush()
        val out = ArrayList<Short>(input.size)
        fun drain() {
            val buffer = processor.output.order(ByteOrder.nativeOrder())
            while (buffer.remaining() >= 2) out += buffer.short
        }
        var at = 0
        val chunk = 4096 * channels
        while (at < input.size) {
            val n = minOf(chunk, input.size - at)
            val bytes = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
            for (i in at until at + n) bytes.putShort(input[i])
            bytes.flip()
            processor.queueInput(bytes)
            drain()
            at += n
        }
        processor.queueEndOfStream()
        while (!processor.isEnded) drain()
        return out.toShortArray()
    }

    /** Noise, low-passed at [cutoffHz] as a codec would (a brick wall), interleaved 16-bit. */
    private fun noise(channels: Int, cutoffHz: Float?, seed: Int = 5): ShortArray {
        val random = Random(seed)
        val fft = Fft(size)
        val result = ShortArray(size * channels)
        for (c in 0 until channels) {
            val re = FloatArray(size) { (random.nextFloat() * 2 - 1) * 0.25f }
            val im = FloatArray(size)
            if (cutoffHz != null) {
                fft.transform(re, im, inverse = false)
                val cut = (cutoffHz / rate * size).toInt()
                for (k in cut..size - cut) {
                    re[k] = 0f
                    im[k] = 0f
                }
                fft.transform(re, im, inverse = true)
                for (i in 0 until size) re[i] /= size
            }
            for (i in 0 until size) result[i * channels + c] = (re[i].coerceIn(-1f, 1f) * 32_767f).toInt().toShort()
        }
        return result
    }

    /** The mean power between [fromHz] and [toHz] over the last two seconds, of the first channel, in dB. */
    private fun bandDb(samples: ShortArray, channels: Int, fromHz: Float, toHz: Float): Double {
        val n = 1024
        val fft = Fft(n)
        val frames = samples.size / channels
        var sum = 0.0
        var count = 0
        var start = frames - 2 * rate
        while (start + n <= frames) {
            val re = FloatArray(n) { samples[(start + it) * channels] / 32_768f * RestoreDsp.window[it] }
            val im = FloatArray(n)
            fft.transform(re, im, inverse = false)
            for (k in (fromHz / rate * n).toInt() until (toHz / rate * n).toInt()) {
                sum += re[k] * re[k] + im[k] * im[k]
                count++
            }
            start += n
        }
        return 10 * log10(sum / count + 1e-12)
    }

    /** A model that says every band over the cutoff is 20 dB under the anchor, whatever it hears. */
    private fun flatModel(): RestoreModel {
        val edges = listOf(0, 250, 500, 1000, 1500, 2000, 3000) + (4000..21_500 step 500) + 22_050
        val bands = edges.size - 1
        val hidden = 4
        val inputs = 2 * bands + 2
        val tensors = listOf(
            "inp.weight" to FloatArray(hidden * inputs), "inp.bias" to FloatArray(hidden),
            "gru.weight_ih_l0" to FloatArray(3 * hidden * hidden), "gru.weight_hh_l0" to FloatArray(3 * hidden * hidden),
            "gru.bias_ih_l0" to FloatArray(3 * hidden), "gru.bias_hh_l0" to FloatArray(3 * hidden),
            "out.weight" to FloatArray(bands * hidden), "out.bias" to FloatArray(bands) { -1f }
        )
        val shapes = mapOf(
            "inp.weight" to "[$hidden,$inputs]", "inp.bias" to "[$hidden]",
            "gru.weight_ih_l0" to "[${3 * hidden},$hidden]", "gru.weight_hh_l0" to "[${3 * hidden},$hidden]",
            "gru.bias_ih_l0" to "[${3 * hidden}]", "gru.bias_hh_l0" to "[${3 * hidden}]",
            "out.weight" to "[$bands,$hidden]", "out.bias" to "[$bands]"
        )
        val header = """{"name":"flat","edges":$edges,"hidden":$hidden,"layers":1,"frame":1024,"hop":256,""" +
            """"tensors":[${tensors.joinToString(",") { """{"name":"${it.first}","shape":${shapes[it.first]}}""" }}]}"""
        val blob = header.toByteArray()
        val bytes = ByteArrayOutputStream()
        bytes.write("YCRM".toByteArray())
        bytes.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(blob.size).array())
        bytes.write(blob)
        for ((_, values) in tensors) {
            val b = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(b::putFloat)
            bytes.write(b.array())
        }
        return RestoreModel.read(ByteArrayInputStream(bytes.toByteArray()))
    }
}
