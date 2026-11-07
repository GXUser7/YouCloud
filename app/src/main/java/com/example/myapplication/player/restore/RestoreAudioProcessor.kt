package com.example.myapplication.player.restore

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Brings back what a lossy file lost over its cutoff — the cymbals' and the voice's air that MP3
 * and AAC at 96–160 kbps cut off at 15–17 kHz — and only for such a file: the sound's long-term
 * spectrum is watched for the cliff a codec's low-pass leaves, and while there is none (a lossless
 * file, MP3 at 192 kbps and over, AAC at 256) not a sample is changed.
 *
 * Over the cliff it does what HE-AAC's band replication does, with a model's ear: the band just
 * under the cutoff is copied up in its own steps (by a multiple of 4 bins, so its phase runs on as
 * the frames do), and each band of the copy set to the level the [model] says the lossless
 * original had there, given what it hears under the cutoff. The same gains on both channels: the
 * stereo picture of the copy is the one under it. All of it is added to the sound as it was, which
 * is delayed by 768 samples (17 ms) to meet it — constant, so the delay never changes what is
 * heard while it plays — and comes in and goes out over a third of a second.
 *
 * [enabled] and [model] are read every frame; [onEnhancingChange] is told, on this thread, when
 * what it does starts or stops showing.
 */
@UnstableApi
class RestoreAudioProcessor(
    private val onEnhancingChange: (Boolean) -> Unit = {}
) : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var model: RestoreModel? = null

    /**
     * Told, on this thread, each time it makes up its mind about the sound: its cutoff in Hz, or 0
     * for a sound that reaches the top. The service keeps it per track ([hint]).
     */
    @Volatile
    var onVerdict: ((Float) -> Unit)? = null

    // A verdict given from outside, kept from the last time the track played: NaN for none.
    @Volatile
    private var hinted = Float.NaN

    /**
     * What is known of the track starting now, from the last time it played: its cutoff, or 0 for
     * none — so it is restored from its first second, without listening first. Listening goes on,
     * and corrects it if the file has changed.
     */
    fun hint(cutoffHz: Float) {
        hinted = cutoffHz
    }

    private val frame = RestoreDsp.FRAME
    private val hop = RestoreDsp.HOP
    private val delay = frame - hop

    private var channels = 0
    private var rate = 0
    private val fft = Fft(frame)

    // The last frame of input, per channel, as it came; and what is made over the cutoff, added up
    // frame over frame, waiting for its hop to be done.
    private var history = Array(2) { FloatArray(frame) }
    private var made = Array(2) { FloatArray(frame) }
    private var filled = 0
    // Where the frame held starts, counted from the first input sample: the first frames start
    // before it, in silence that is never let out.
    private var frameStart = 0L
    private var received = 0L
    // How far output may go: all of it, until the stream has ended, then the input's own length.
    private var limit = Long.MAX_VALUE

    private val re = Array(2) { FloatArray(frame) }
    private val im = Array(2) { FloatArray(frame) }
    private val midRe = FloatArray(RestoreDsp.BINS)
    private val midIm = FloatArray(RestoreDsp.BINS)
    private val power = FloatArray(RestoreDsp.BINS)

    // The long-term spectrum the cutoff is read from.
    private val longPower = FloatArray(RestoreDsp.BINS)
    private val longDb = FloatArray(RestoreDsp.BINS)
    private var heard = 0
    private var sinceCheck = 0
    private var cutoffHz: Float? = null
    private var votes = 0
    private var pendingCutoff: Float? = null
    // How much of what is made is heard: up to 1 while restoring, down to 0 while not.
    private var mix = 0f
    private var shown = false
    // Frames running with sound of their own over the cutoff: a lossy file never has any there.
    private var ownTop = 0

    // The model as it was set up for the cutoff and rate in use.
    private var running: RestoreModel? = null
    private var state: RestoreModel.State? = null
    private var bands: RestoreDsp.Bands? = null
    private var input = FloatArray(0)
    private var pred = FloatArray(0)
    private var bandGain = FloatArray(0)
    private var previous = FloatArray(0)
    private var kc = 0
    private var width = 0
    private var top = 0
    private var noise = FloatArray(0)
    private var seed = 0x9E3779B9.toInt()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Plain 16-bit sound in one or two channels, at a rate whose top reaches over 18 kHz.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2 ||
            inputAudioFormat.sampleRate !in 40_000..48_000
        ) {
            return AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        forgetSound()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * channels)
        if (frames == 0) return
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        // Each hop of input lets out a hop of output (the first ones nothing).
        val out = replaceOutputBuffer((frames + hop) * 2 * channels).order(ByteOrder.nativeOrder())
        repeat(frames) {
            for (c in 0 until channels) history[c][filled] = input.short / 32_768f
            filled++
            received++
            if (filled == frame) hopDone(out)
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onQueueEndOfStream() {
        // What is still held back, let out by silence after it.
        limit = received
        val out = replaceOutputBuffer((frame + hop) * 2 * channels).order(ByteOrder.nativeOrder())
        while (frameStart < limit) {
            for (c in 0 until channels) history[c][filled] = 0f
            filled++
            if (filled == frame) hopDone(out)
        }
        out.flip()
    }

    /** A frame is full: work it out, and let out the hop at its start, now complete. */
    private fun hopDone(out: ByteBuffer) {
        process()
        for (i in 0 until hop) {
            val position = frameStart + i
            if (position < 0 || position >= limit) continue
            for (c in 0 until channels) {
                // Back to 16 bits exactly as it came: with nothing made, not a bit changes.
                val sample = (history[c][i] + made[c][i] * mix) * 32_768f
                out.putShort(kotlin.math.round(sample).coerceIn(-32_768f, 32_767f).toInt().toShort())
            }
        }
        frameStart += hop
        for (c in 0 until channels) {
            System.arraycopy(history[c], hop, history[c], 0, frame - hop)
            System.arraycopy(made[c], hop, made[c], 0, frame - hop)
            java.util.Arrays.fill(made[c], frame - hop, frame, 0f)
        }
        filled = frame - hop
    }

    private fun process() {
        val model = model
        val restoring = enabled && model != null
        if (!restoring && mix == 0f) {
            setShown(false)
            return
        }
        analyse()
        val known = hinted
        if (!known.isNaN()) {
            hinted = Float.NaN
            val cutoff = known.takeIf { it > 0f && it < RestoreDsp.LOW_QUALITY_HZ }
            cutoffHz = cutoff
            pendingCutoff = cutoff
            votes = 2
        }
        listen()
        val cutoff = cutoffHz
        val target = if (restoring && cutoff != null) 1f else 0f
        val step = hop / (0.33f * rate)
        mix = if (target > mix) min(target, mix + step) else max(target, mix - step)
        setShown(mix > 0.5f && target == 1f)
        if (mix == 0f || cutoff == null || model == null) return
        prepare(model, cutoff)
        val state = state ?: return
        val bands = bands ?: return
        if (width < 8) return
        val anchor = RestoreDsp.features(power, cutoff, kc, bands, rate, input)
        // The next track, gapless, isn't lossy: its own top is there at once, and is left alone at
        // once — not after the seconds the long-term spectrum takes to forget the last one's cliff.
        if (hasOwnTop(anchor)) {
            if (++ownTop >= OWN_TOP_FRAMES) {
                cutoffHz = null
                pendingCutoff = null
                votes = 1
                heard = 0
                ownTop = 0
            }
            return
        }
        ownTop = 0
        state.step(input, pred)
        model.blendPrior(pred, cutoff)
        RestoreDsp.gains(midRe, midIm, kc, top, width, pred, anchor, bands, bandGain, previous)
        synthesize()
    }

    /** The frame's spectrum per channel (windowed), and the mid's power, which the model and the detector read. */
    private fun analyse() {
        val w = RestoreDsp.window
        for (c in 0 until channels) {
            val h = history[c]
            val r = re[c]
            val i = im[c]
            for (n in 0 until frame) {
                r[n] = h[n] * w[n]
                i[n] = 0f
            }
            fft.transform(r, i, inverse = false)
        }
        for (k in 0 until RestoreDsp.BINS) {
            if (channels == 2) {
                midRe[k] = (re[0][k] + re[1][k]) * 0.5f
                midIm[k] = (im[0][k] + im[1][k]) * 0.5f
            } else {
                midRe[k] = re[0][k]
                midIm[k] = im[0][k]
            }
            power[k] = midRe[k] * midRe[k] + midIm[k] * midIm[k]
        }
    }

    /**
     * The long-term spectrum, over the last couple of seconds of sound (silence passed over), and,
     * every third of a second, whether it has a lossy file's cliff — said twice running before it
     * counts, either way.
     */
    private fun listen() {
        var total = 0f
        for (k in 0 until RestoreDsp.BINS) total += power[k]
        if (RestoreDsp.db(total / RestoreDsp.BINS) < -70f) return
        val alpha = hop / (2f * rate)
        for (k in 0 until RestoreDsp.BINS) {
            longPower[k] = if (heard == 0) power[k] else longPower[k] + alpha * (power[k] - longPower[k])
        }
        heard++
        if (heard < (1.5f * rate / hop).toInt() || ++sinceCheck < (0.33f * rate / hop).toInt()) return
        sinceCheck = 0
        for (k in 0 until RestoreDsp.BINS) longDb[k] = RestoreDsp.db(longPower[k])
        val found = RestoreDsp.detectCutoff(longDb, rate)?.takeIf { it < RestoreDsp.LOW_QUALITY_HZ }
        val same = when {
            found == null -> pendingCutoff == null
            else -> pendingCutoff?.let { kotlin.math.abs(it - found) < 400f } == true
        }
        votes = if (same) votes + 1 else 1
        pendingCutoff = found
        if (votes >= 2) {
            val current = cutoffHz
            if (found == null) {
                cutoffHz = null
            } else if (current == null || kotlin.math.abs(current - found) >= 400f) {
                cutoffHz = found
            }
            if (votes == 2) onVerdict?.invoke(cutoffHz ?: 0f)
        }
    }

    /** The model's bands and memory for this cutoff: a new cutoff, or a new model, starts it afresh. */
    private fun prepare(model: RestoreModel, cutoff: Float) {
        val bin = RestoreDsp.cutoffBin(cutoff, rate)
        if (model === running && bin == kc) return
        running = model
        kc = bin
        width = RestoreDsp.patchWidth(kc, rate)
        top = RestoreDsp.topBin(rate)
        bands = RestoreDsp.Bands(model.edges, rate)
        state = model.State()
        input = FloatArray(model.inputs)
        pred = FloatArray(model.bands)
        bandGain = FloatArray(model.bands)
        previous = FloatArray(max(0, top - kc)) { Float.NaN }
        noise = FloatArray(max(0, top - kc)) { i -> RestoreDsp.NOISE_LOW + (RestoreDsp.NOISE_HIGH - RestoreDsp.NOISE_LOW) * i / max(1, top - kc) }
    }

    /** Whether this frame's own sound, just over the cutoff, is louder than a codec ever leaves it. */
    private fun hasOwnTop(anchor: Float): Boolean {
        val from = kc + 4
        val to = min(top, kc + 40)
        if (to <= from) return false
        var sum = 0f
        for (k in from until to) sum += power[k]
        return RestoreDsp.db(sum / (to - from)) > anchor - OWN_TOP_DB
    }

    /**
     * What is made, per channel: the copy, a little noise in its phase, at the gains — from under
     * the cutoff, over the codec's fade, rising to it, up to the top.
     */
    private fun synthesize() {
        val start = RestoreDsp.patchStart(kc)
        for (c in 0 until channels) {
            val r = re[c]
            val i = im[c]
            val outRe = scratchRe
            val outIm = scratchIm
            java.util.Arrays.fill(outRe, 0f)
            java.util.Arrays.fill(outIm, 0f)
            for (k in start until top) {
                // Over the cutoff, its own gain; under it, the gain at the cutoff.
                val j = k - kc
                val gainDb = previous[maxOf(j, 0)]
                // No gain worked out for it yet: nothing made there.
                if (gainDb.isNaN()) continue
                val s = RestoreDsp.source(k, start, width)
                val pr = r[s]
                val pi = i[s]
                val magnitude = kotlin.math.sqrt(pr * pr + pi * pi)
                val angle = nextRandom() * (2f * Math.PI.toFloat())
                val n = if (j > 0) noise[j] else RestoreDsp.NOISE_LOW
                val ramp = ((k - start + 1f) / (RestoreDsp.FILL + 1)).coerceAtMost(1f)
                // Noise in the phase takes some of the energy with it: given back.
                val g = RestoreDsp.dbToAmplitude(gainDb) * ramp / kotlin.math.sqrt((1f - n) * (1f - n) + n * n)
                outRe[k] = ((1f - n) * pr + n * magnitude * kotlin.math.cos(angle)) * g
                outIm[k] = ((1f - n) * pi + n * magnitude * kotlin.math.sin(angle)) * g
            }
            // A real signal's spectrum: the upper half mirrors the lower.
            for (k in 1 until frame / 2) {
                outRe[frame - k] = outRe[k]
                outIm[frame - k] = -outIm[k]
            }
            fft.transform(outRe, outIm, inverse = true)
            val w = RestoreDsp.window
            val m = made[c]
            // 1/frame for the inverse, 1/2 for four Hann windows overlapping.
            val scale = 0.5f / frame
            for (n in 0 until frame) m[n] += outRe[n] * w[n] * scale
        }
    }

    private val scratchRe = FloatArray(frame)
    private val scratchIm = FloatArray(frame)

    private fun nextRandom(): Float {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return (x ushr 8) / 16_777_216f
    }

    private fun setShown(value: Boolean) {
        if (value != shown) {
            shown = value
            onEnhancingChange(value)
        }
    }

    /** A new stream: nothing of the last one's sound held, its cutoff forgotten. */
    private fun forgetSound() {
        restart()
        java.util.Arrays.fill(longPower, 0f)
        heard = 0
        sinceCheck = 0
        cutoffHz = null
        pendingCutoff = null
        votes = 0
        mix = 0f
        setShown(false)
    }

    /** From another place in the same sound (a seek): the frames start over, its cutoff is kept. */
    private fun restart() {
        for (c in 0 until 2) {
            history[c].fill(0f)
            made[c].fill(0f)
        }
        filled = delay
        frameStart = -delay.toLong()
        received = 0
        limit = Long.MAX_VALUE
        running = null
        state = null
        kc = 0
    }

    override fun onFlush() {
        restart()
    }

    private companion object {
        // 40 dB under the 2 kHz under the cutoff, for 6 frames running (35 ms): a lossy file's
        // floor there is 60 dB down and more.
        const val OWN_TOP_DB = 40f
        const val OWN_TOP_FRAMES = 6
    }

    override fun onReset() {
        forgetSound()
        channels = 0
        rate = 0
    }
}
