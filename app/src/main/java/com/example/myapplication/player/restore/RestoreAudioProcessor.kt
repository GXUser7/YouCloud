package com.example.myapplication.player.restore

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
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
 * heard while it plays — and comes in and goes out over a third of a second. Where the sound is
 * already at full scale (a loud master), the copy gives way around the peak instead of clipping it.
 *
 * Once it has made up its mind about a track it holds to it: a quiet passage or a fade, where the
 * long-term spectrum says little, doesn't undo it, and a verdict kept from the last time the
 * track played ([hint]) holds from the first second.
 *
 * It keeps time: when the phone can't work it out as fast as the sound plays (a slow core, the
 * screen off), it steps down — to the [fallback] model, then to the fixed fall-off alone, then to
 * nothing — rather than let the sound break up, and steps back up once there is room again.
 *
 * [enabled] and [model] are read every frame; [onEnhancingChange] is told, on this thread, when
 * what it does starts or stops being heard.
 */
@UnstableApi
class RestoreAudioProcessor(
    private val onEnhancingChange: (Boolean) -> Unit = {}
) : BaseAudioProcessor() {
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var model: RestoreModel? = null

    /** The lighter model it steps down to when [model] can't be kept up with. */
    @Volatile
    var fallback: RestoreModel? = null

    /**
     * Told, on this thread, each time it makes up its mind about the sound: its cutoff in Hz, or 0
     * for a sound that reaches the top; [settled] once the same has held for a few seconds — what
     * is worth keeping for the next time ([hint]).
     */
    @Volatile
    var onVerdict: ((cutoffHz: Float, settled: Boolean) -> Unit)? = null

    /** Told, on this thread, when it steps down or up ([Level]) to keep time. */
    @Volatile
    var onLevel: ((Level) -> Unit)? = null

    /** How much of the work it does: all of it, down to none, as the phone keeps up. */
    enum class Level { Model, Fallback, FallOff, Off }

    // A verdict given from outside, kept from the last time the track played: NaN for none.
    @Volatile
    private var hinted = Float.NaN

    /**
     * What is known of the track starting now, from the last time it played: its cutoff, or 0 for
     * none — so it is restored from its first second, without listening first. Listening goes on,
     * but only a track that plainly isn't what was kept (its own top there, or no cliff for seconds
     * on end) changes it.
     */
    fun hint(cutoffHz: Float) {
        hinted = cutoffHz
    }

    private val frame = RestoreDsp.FRAME
    private val hop = RestoreDsp.HOP
    private val delay = frame - hop

    private var channels = 0
    private var rate = 0
    private var reconfigureOnFlush = false
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

    // One transform for both channels: the left in the real part, the right in the imaginary.
    private val zRe = FloatArray(frame)
    private val zIm = FloatArray(frame)
    // Each channel's spectrum, up to half the rate.
    private val specRe = Array(2) { FloatArray(RestoreDsp.BINS) }
    private val specIm = Array(2) { FloatArray(RestoreDsp.BINS) }
    private val midRe = FloatArray(RestoreDsp.BINS)
    private val midIm = FloatArray(RestoreDsp.BINS)
    private val power = FloatArray(RestoreDsp.BINS)

    // The long-term spectrum the cutoff is read from.
    private val longPower = FloatArray(RestoreDsp.BINS)
    private val longDb = FloatArray(RestoreDsp.BINS)
    private var heard = 0
    private var sinceCheck = 0
    private var cutoffHz: Float? = null
    // The cutoff came from the track's kept verdict, not from listening.
    private var kept = false
    private var votes = 0
    private var pendingCutoff: Float? = null
    // How much of what is made is heard: up to 1 while restoring, down to 0 while not.
    private var mix = 0f
    private var shown = false
    // Frames running with sound of their own over the cutoff: a lossy file never has any there.
    private var ownTop = 0
    // Hops passed over while only listening (see [process]).
    private var idleHops = 0
    // How much the copy may be heard just now, so that sound and copy stay under full scale.
    private var headroom = 1f
    private val need = FloatArray(hop + 1)

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

    // Keeping time: the share of the sound's own duration its working out takes, smoothed over a
    // second or two, and the level of work it does for it.
    private var load = 0f
    private var level = Level.Model
    private var roomySince = 0L
    // Sound worked out so far by this processor: the first seconds run before the phone has
    // compiled the work, slower than it will be, and don't count against it.
    private var warmFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Media3 configures the next track before draining the current one. Its channels,
        // buffered samples and end-of-stream state must stay intact until flush applies it.
        reconfigureOnFlush = true
        // Plain 16-bit sound in one or two channels, at a rate whose top reaches over 18 kHz.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2 ||
            inputAudioFormat.sampleRate !in 40_000..48_000
        ) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * channels)
        if (frames == 0) return
        val started = System.nanoTime()
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
        keepTime(System.nanoTime() - started, frames)
    }

    override fun onQueueEndOfStream() {
        // The pipeline may repeat this signal while the preceding processor is ended. Do not
        // clear the output buffer it is still consuming, or produce the same tail again.
        if (limit != Long.MAX_VALUE) return
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
        if (mix == 0f) {
            for (i in 0 until hop) {
                val position = frameStart + i
                if (position < 0 || position >= limit) continue
                // Back to 16 bits exactly as it came: with nothing made, not a bit changes.
                for (c in 0 until channels) out.putShort(kotlin.math.round(history[c][i] * 32_768f).toInt().toShort())
            }
        } else {
            giveWay()
            var room = headroom
            for (i in 0 until hop) {
                // Down at once where the next peak needs it (worked out ahead in [need]), back up
                // slowly after it.
                room = min(need[i], room + RELEASE_STEP)
                val position = frameStart + i
                if (position < 0 || position >= limit) continue
                val m = mix * room
                for (c in 0 until channels) {
                    val sample = (history[c][i] + made[c][i] * m) * 32_768f
                    out.putShort(kotlin.math.round(sample).coerceIn(-32_768f, 32_767f).toInt().toShort())
                }
            }
            headroom = room
        }
        frameStart += hop
        for (c in 0 until channels) {
            System.arraycopy(history[c], hop, history[c], 0, frame - hop)
            System.arraycopy(made[c], hop, made[c], 0, frame - hop)
            java.util.Arrays.fill(made[c], frame - hop, frame, 0f)
        }
        filled = frame - hop
    }

    /**
     * How much of the copy each sample of the hop about to go out can take and stay under full
     * scale, into [need]: where a loud master's own peak leaves no room, the copy is turned down
     * there, and eased down over the samples before it rather than cut at once.
     */
    private fun giveWay() {
        need[hop] = 1f
        for (i in hop - 1 downTo 0) {
            var n = 1f
            for (c in 0 until channels) {
                val h = history[c][i]
                val a = made[c][i] * mix
                if (abs(h + a) > FULL_SCALE && a != 0f) {
                    // Only the copy is turned down: the sound as it came is never touched.
                    val room = ((if (a > 0f) FULL_SCALE - h else -FULL_SCALE - h) / a).coerceIn(0f, 1f)
                    n = min(n, room)
                }
            }
            need[i] = min(n, need[i + 1] + ATTACK_STEP)
        }
    }

    private fun process() {
        val chosen = model
        val running = when (level) {
            Level.Model -> chosen
            Level.Fallback -> fallback ?: chosen
            Level.FallOff -> chosen
            Level.Off -> null
        }
        val restoring = enabled && chosen != null && level != Level.Off
        if (!restoring && mix == 0f) {
            setShown(false)
            return
        }
        // Nothing to restore (yet): only listening, which every fourth frame does as well — the
        // frames then lie end to end — for a quarter of the work. A lossless track costs that.
        val idle = mix == 0f && cutoffHz == null && hinted.isNaN()
        if (idle && ++idleHops < IDLE_STRIDE) return
        val stride = if (idle) IDLE_STRIDE else 1
        idleHops = 0
        analyse()
        val known = hinted
        if (!known.isNaN()) {
            hinted = Float.NaN
            val cutoff = known.takeIf { it > 0f && it < RestoreDsp.LOW_QUALITY_HZ }
            cutoffHz = cutoff
            pendingCutoff = cutoff
            votes = SETTLED_VOTES + 1
            kept = true
            ownTop = 0
        }
        listen(stride)
        val cutoff = cutoffHz
        val target = if (restoring && cutoff != null) 1f else 0f
        val step = hop / (0.33f * rate)
        mix = if (target > mix) min(target, mix + step) else max(target, mix - step)
        setShown(mix > 0.5f && target == 1f)
        if (mix == 0f || cutoff == null || running == null) return
        prepare(running, cutoff)
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
                kept = false
                votes = 1
                heard = 0
                ownTop = 0
            }
            return
        }
        ownTop = 0
        if (level == Level.FallOff) {
            running.priorOnly(pred, cutoff)
        } else {
            state.step(input, pred)
            running.blendPrior(pred, cutoff)
        }
        RestoreDsp.gains(midRe, midIm, kc, top, width, pred, anchor, bands, bandGain, previous)
        synthesize()
    }

    /**
     * The frame's spectrum per channel (windowed), and the mid's power, which the model and the
     * detector read. Two channels go through one transform, the left as its real part and the
     * right as its imaginary, and are told apart after: a real signal's spectrum is symmetric.
     */
    private fun analyse() {
        val w = RestoreDsp.window
        val l = history[0]
        if (channels == 2) {
            val r = history[1]
            for (n in 0 until frame) {
                zRe[n] = l[n] * w[n]
                zIm[n] = r[n] * w[n]
            }
        } else {
            for (n in 0 until frame) {
                zRe[n] = l[n] * w[n]
                zIm[n] = 0f
            }
        }
        fft.transform(zRe, zIm, inverse = false)
        val lRe = specRe[0]
        val lIm = specIm[0]
        if (channels == 2) {
            val rRe = specRe[1]
            val rIm = specIm[1]
            for (k in 0 until RestoreDsp.BINS) {
                val m = (frame - k) and (frame - 1)
                val ar = zRe[k]
                val ai = zIm[k]
                val br = zRe[m]
                val bi = zIm[m]
                lRe[k] = 0.5f * (ar + br)
                lIm[k] = 0.5f * (ai - bi)
                rRe[k] = 0.5f * (ai + bi)
                rIm[k] = 0.5f * (br - ar)
                midRe[k] = 0.5f * (lRe[k] + rRe[k])
                midIm[k] = 0.5f * (lIm[k] + rIm[k])
                power[k] = midRe[k] * midRe[k] + midIm[k] * midIm[k]
            }
        } else {
            for (k in 0 until RestoreDsp.BINS) {
                lRe[k] = zRe[k]
                lIm[k] = zIm[k]
                midRe[k] = zRe[k]
                midIm[k] = zIm[k]
                power[k] = midRe[k] * midRe[k] + midIm[k] * midIm[k]
            }
        }
    }

    /**
     * The long-term spectrum, over the last couple of seconds of sound (silence passed over), and,
     * every third of a second, whether it has a lossy file's cliff — said twice running before it
     * counts. Undoing a cliff found takes longer: a fade or a quiet passage blurs the long-term
     * spectrum for a while, and the verdict shouldn't come and go with it.
     */
    private fun listen(stride: Int) {
        var total = 0f
        for (k in 0 until RestoreDsp.BINS) total += power[k]
        if (RestoreDsp.db(total / RestoreDsp.BINS) < -70f) return
        // [stride] hops since the last frame heard: it stands for them all.
        val alpha = hop * stride / (2f * rate)
        for (k in 0 until RestoreDsp.BINS) {
            longPower[k] = if (heard == 0) power[k] else longPower[k] + alpha * (power[k] - longPower[k])
        }
        heard += stride
        sinceCheck += stride
        if (heard < (1.5f * rate / hop).toInt() || sinceCheck < (0.33f * rate / hop).toInt()) return
        sinceCheck = 0
        for (k in 0 until RestoreDsp.BINS) longDb[k] = RestoreDsp.db(longPower[k])
        // Too quiet to tell: no vote either way.
        if (!RestoreDsp.loudEnough(longDb, rate)) return
        val found = RestoreDsp.detectCutoff(longDb, rate)?.takeIf { it < RestoreDsp.LOW_QUALITY_HZ }
        val same = when {
            found == null -> pendingCutoff == null
            else -> pendingCutoff?.let { abs(it - found) < 400f } == true
        }
        votes = if (same) votes + 1 else 1
        pendingCutoff = found
        val current = cutoffHz
        if (found == null) {
            if (current != null && votes >= (if (kept) KEPT_CLEAR_VOTES else CLEAR_VOTES)) {
                cutoffHz = null
                kept = false
                onVerdict?.invoke(0f, false)
            } else if (current == null && votes == 2) {
                onVerdict?.invoke(0f, false)
            }
        } else if (votes >= 2 && (current == null || abs(current - found) >= 400f)) {
            cutoffHz = found
            kept = false
            onVerdict?.invoke(found, false)
        }
        // Held for a few seconds: what is kept for the next time is no passing impression.
        if (votes == SETTLED_VOTES && (found == null) == (cutoffHz == null)) onVerdict?.invoke(cutoffHz ?: 0f, true)
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

    /**
     * Whether this frame's own sound, just over the cutoff, is louder than a codec ever leaves it:
     * near the sound under the cutoff, and well over the floor of 16-bit sound — in a quiet passage
     * the floor alone came within reach, and the track was taken for another for a moment.
     */
    private fun hasOwnTop(anchor: Float): Boolean {
        val from = kc + 4
        val to = min(top, kc + 40)
        if (to <= from) return false
        var sum = 0f
        for (k in from until to) sum += power[k]
        return RestoreDsp.db(sum / (to - from)) > max(anchor - OWN_TOP_DB, OWN_TOP_FLOOR_DB)
    }

    /**
     * What is made, per channel: the copy, a little noise in its phase, at the gains — from under
     * the cutoff, over the codec's fade, rising to it, up to the top. Both channels come back
     * through one inverse transform, the left as its real part and the right as its imaginary.
     */
    private fun synthesize() {
        val start = RestoreDsp.patchStart(kc)
        // The top bin made: half the rate itself is left out, where the two channels would meet.
        val end = min(top, frame / 2)
        val outRe = zRe
        val outIm = zIm
        java.util.Arrays.fill(outRe, 0f)
        java.util.Arrays.fill(outIm, 0f)
        val stereo = channels == 2
        for (k in start until end) {
            // Over the cutoff, its own gain; under it, the gain at the cutoff.
            val j = k - kc
            val gainDb = previous[maxOf(j, 0)]
            // No gain worked out for it yet: nothing made there.
            if (gainDb.isNaN()) continue
            val s = RestoreDsp.source(k, start, width)
            val n = if (j > 0) noise[j] else RestoreDsp.NOISE_LOW
            val ramp = ((k - start + 1f) / (RestoreDsp.FILL + 1)).coerceAtMost(1f)
            // Noise in the phase takes some of the energy with it: given back.
            val g = RestoreDsp.dbToAmplitude(gainDb) * ramp / kotlin.math.sqrt((1f - n) * (1f - n) + n * n)
            // The left (or the only) channel.
            var pr = specRe[0][s]
            var pi = specIm[0][s]
            var magnitude = kotlin.math.sqrt(pr * pr + pi * pi)
            var phase = nextPhase()
            val lr = ((1f - n) * pr + n * magnitude * PHASE_COS[phase]) * g
            val li = ((1f - n) * pi + n * magnitude * PHASE_SIN[phase]) * g
            if (stereo) {
                pr = specRe[1][s]
                pi = specIm[1][s]
                magnitude = kotlin.math.sqrt(pr * pr + pi * pi)
                phase = nextPhase()
                val rr = ((1f - n) * pr + n * magnitude * PHASE_COS[phase]) * g
                val ri = ((1f - n) * pi + n * magnitude * PHASE_SIN[phase]) * g
                // L + iR at k, and its mirror, conj(L) + i·conj(R), at -k.
                outRe[k] = lr - ri
                outIm[k] = li + rr
                outRe[frame - k] = lr + ri
                outIm[frame - k] = rr - li
            } else {
                outRe[k] = lr
                outIm[k] = li
                outRe[frame - k] = lr
                outIm[frame - k] = -li
            }
        }
        fft.transform(outRe, outIm, inverse = true)
        val w = RestoreDsp.window
        // 1/frame for the inverse, 1/2 for four Hann windows overlapping.
        val scale = 0.5f / frame
        val left = made[0]
        for (n in 0 until frame) left[n] += outRe[n] * w[n] * scale
        if (stereo) {
            val right = made[1]
            for (n in 0 until frame) right[n] += outIm[n] * w[n] * scale
        }
    }

    // A random phase, as an index into the table of them.
    private fun nextPhase(): Int {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return (x ushr 20) and (PHASES - 1)
    }

    /**
     * The time a buffer took against how long it plays for, smoothed; past [SLOW] the work steps
     * down a level, and after [ROOMY_MS] under [ROOMY] it steps back up one.
     */
    private fun keepTime(nanos: Long, frames: Int) {
        if (rate == 0 || !enabled) return
        val playsNanos = frames * 1_000_000_000L / rate
        if (playsNanos <= 0) return
        if (warmFrames < WARM_SECONDS * rate) {
            warmFrames += frames
            return
        }
        val share = nanos.toFloat() / playsNanos
        val alpha = (playsNanos / (LOAD_SECONDS * 1e9f)).coerceAtMost(1f)
        load += alpha * (share - load)
        val now = System.nanoTime() / 1_000_000L
        if (load > SLOW && level != Level.Off) {
            level = when (level) {
                Level.Model -> if (fallback != null && fallback !== model) Level.Fallback else Level.FallOff
                Level.Fallback -> Level.FallOff
                else -> Level.Off
            }
            // What the new level costs is measured afresh.
            load = 0f
            roomySince = now
            running = null
            onLevel?.invoke(level)
        } else if (load < ROOMY && level != Level.Model) {
            if (now - roomySince >= ROOMY_MS) {
                level = when (level) {
                    Level.Off -> Level.FallOff
                    Level.FallOff -> if (fallback != null && fallback !== model) Level.Fallback else Level.Model
                    else -> Level.Model
                }
                load = 0f
                roomySince = now
                running = null
                onLevel?.invoke(level)
            }
        } else {
            roomySince = now
        }
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
        kept = false
        votes = 0
        mix = 0f
        load = 0f
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
        headroom = 1f
        ownTop = 0
    }

    override fun onFlush() {
        if (reconfigureOnFlush) {
            channels = if (outputAudioFormat == AudioFormat.NOT_SET) 0 else inputAudioFormat.channelCount
            rate = if (outputAudioFormat == AudioFormat.NOT_SET) 0 else inputAudioFormat.sampleRate
            forgetSound()
            reconfigureOnFlush = false
        } else {
            restart()
        }
    }

    private companion object {
        // 40 dB under the 2 kHz under the cutoff, for 8 frames running (46 ms): a lossy file's
        // floor there is 60 dB down and more. And never under -55 dB, 20 over 16-bit sound's own
        // floor (-77 dB a bin of the mid, in these units).
        const val OWN_TOP_DB = 40f
        const val OWN_TOP_FLOOR_DB = -55f
        const val OWN_TOP_FRAMES = 8

        // Checks (a third of a second each) of no cliff before a cliff found is undone: two
        // seconds; four for one kept from before.
        const val CLEAR_VOTES = 6
        const val KEPT_CLEAR_VOTES = 12
        // Checks of the same verdict before it is worth keeping: three seconds.
        const val SETTLED_VOTES = 9

        // Just under full scale, and how fast the copy gives way to a peak (all the way in 48
        // samples, 1 ms) and comes back after it (in 100 ms).
        const val FULL_SCALE = 32_766f / 32_768f
        const val ATTACK_STEP = 1f / 48f
        const val RELEASE_STEP = 1f / 4_410f

        // Keeping time: over a share of 40% of the sound's duration (smoothed over 1.5 s) it steps
        // down; under 15% for 20 s, back up.
        const val LOAD_SECONDS = 1.5f
        const val SLOW = 0.4f
        const val ROOMY = 0.15f
        const val ROOMY_MS = 20_000L
        const val WARM_SECONDS = 3

        // While only listening, one frame in this many is heard.
        const val IDLE_STRIDE = 4

        // Random phases: a table of them rather than a sine and a cosine for every bin.
        const val PHASES = 4096
        @JvmField
        val PHASE_COS = FloatArray(PHASES) { kotlin.math.cos(2.0 * Math.PI * it / PHASES).toFloat() }
        @JvmField
        val PHASE_SIN = FloatArray(PHASES) { kotlin.math.sin(2.0 * Math.PI * it / PHASES).toFloat() }
    }

    override fun onReset() {
        forgetSound()
        channels = 0
        rate = 0
        reconfigureOnFlush = false
        level = Level.Model
    }
}
