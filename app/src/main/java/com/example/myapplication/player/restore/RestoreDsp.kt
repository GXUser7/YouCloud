package com.example.myapplication.player.restore

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What the restoration does to one frame, as tools/restore/dsp.py does it in training: the bands,
 * the cutoff a lossy codec leaves, the model's input, and the band over the cutoff made from the
 * one under it. The two are kept alike by hand; RestoreDspTest holds them to the same numbers.
 */
internal object RestoreDsp {
    const val FRAME = 1024
    const val HOP = 256
    const val BINS = FRAME / 2 + 1
    private const val EPS = 1e-10f

    // The cutoff: the steepest fall of the long-term spectrum, if it is a cliff the sound never
    // climbs back over.
    private const val DETECT_SPAN = 8
    private const val DETECT_CLIFF_DB = 25f
    private const val DETECT_STAYS_DB = 15f

    /** A cutoff lower than this is a lossy file's, and worth restoring; higher, the sound is left alone. */
    const val LOW_QUALITY_HZ = 18_000f

    const val NOISE_LOW = 0.1f
    const val NOISE_HIGH = 0.4f
    const val MAX_BOOST_DB = 24f
    private const val ATTACK = 0.6f
    private const val RELEASE = 0.25f

    /** The square root of a periodic Hann: for analysis and synthesis, together a Hann. */
    val window = FloatArray(FRAME) { n -> sqrt(0.5 - 0.5 * cos(2.0 * PI * n / FRAME)).toFloat() }

    fun db(power: Float): Float = 10f * log10(power + EPS)

    /** Whether there is enough sound in [longDb] (2–8 kHz) to tell a cutoff from none. */
    fun loudEnough(longDb: FloatArray, rate: Int): Boolean {
        val hz = rate.toFloat() / FRAME
        var ref = 0f
        val refFrom = (2000 / hz).toInt()
        val refTo = (8000 / hz).toInt()
        for (k in refFrom until refTo) ref += longDb[k]
        return ref / (refTo - refFrom) >= -90f
    }

    /**
     * [longDb]: the long-term mean power of each bin, in dB. The cutoff in Hz, or null when the sound
     * reaches the top — or there is too little of it to tell.
     */
    fun detectCutoff(longDb: FloatArray, rate: Int): Float? {
        val hz = rate.toFloat() / FRAME
        var ref = 0f
        val refFrom = (2000 / hz).toInt()
        val refTo = (8000 / hz).toInt()
        for (k in refFrom until refTo) ref += longDb[k]
        ref /= (refTo - refFrom)
        if (ref < -90f) return null
        val top = min((22050 / hz).toInt(), longDb.size - 1)
        val span = DETECT_SPAN
        var best = -1e9f
        var edge = -1
        for (k in (6000 / hz).toInt() until top - span - 2) {
            var before = 0f
            var after = 0f
            for (i in 0 until span) {
                before += longDb[k - span + i]
                after += longDb[k + 2 + i]
            }
            val drop = (before - after) / span
            if (drop > best) {
                best = drop
                edge = k
            }
        }
        if (best < DETECT_CLIFF_DB) return null
        var before = 0f
        for (i in 0 until span) before += longDb[edge - span + i]
        before /= span
        for (k in edge + 2 until top) if (longDb[k] > before - DETECT_STAYS_DB) return null
        var k = edge - span
        while (k < edge + 2 && longDb[k] >= before - 10f) k++
        return k * hz
    }

    /** A model's bands at one sample rate: each band's first bin and the one past its last. */
    class Bands(edges: FloatArray, rate: Int) {
        val count = edges.size - 1
        val lo = IntArray(count)
        val hi = IntArray(count)
        val center = FloatArray(count)

        init {
            val hz = rate.toFloat() / FRAME
            for (b in 0 until count) {
                lo[b] = kotlin.math.ceil(edges[b] / hz).toInt()
                hi[b] = kotlin.math.ceil(edges[b + 1] / hz).toInt()
            }
            hi[count - 1] = min(hi[count - 1], BINS)
            for (b in 0 until count) center[b] = (lo[b] + hi[b] - 1) / 2f
        }
    }

    /** The bin the cutoff falls on. */
    fun cutoffBin(cutoffHz: Float, rate: Int): Int = (cutoffHz / (rate.toFloat() / FRAME)).roundToInt()

    /** The 2 kHz under the cutoff, in dB: what every band is measured against. */
    fun anchorDb(power: FloatArray, kc: Int, rate: Int): Float {
        val hz = rate.toFloat() / FRAME
        val from = max(1, kc - (2000 / hz).toInt())
        var sum = 0f
        for (k in from until kc) sum += power[k]
        return db(sum / (kc - from))
    }

    /** The model's input for one frame into [out]; returns the anchor. */
    fun features(power: FloatArray, cutoffHz: Float, kc: Int, bands: Bands, rate: Int, out: FloatArray): Float {
        val anchor = anchorDb(power, kc, rate)
        val n = bands.count
        for (b in 0 until n) {
            val valid = bands.hi[b] <= kc
            if (valid) {
                var sum = 0f
                for (k in bands.lo[b] until bands.hi[b]) sum += power[k]
                val rel = (db(sum / (bands.hi[b] - bands.lo[b])) - anchor) / 20f
                out[b] = rel.coerceIn(-4f, 4f)
                out[n + b] = 1f
            } else {
                out[b] = 0f
                out[n + b] = 0f
            }
        }
        out[2 * n] = ((anchor + 50f) / 30f).coerceIn(-3f, 3f)
        out[2 * n + 1] = cutoffHz / 22050f
        return anchor
    }

    /**
     * A codec's low-pass doesn't stop dead at the cutoff: the half kilohertz under it is already
     * fading. The copy starts this many bins under it, rising over them, and is copied from under
     * them too — a multiple of 4, so the shift still keeps the phase.
     */
    const val FILL = 12

    /** The bin the copy starts at. */
    fun patchStart(kc: Int): Int = kc - FILL

    /** How wide the copied band is, in bins: a multiple of 4, so it keeps its phase. */
    fun patchWidth(kc: Int, rate: Int): Int {
        val hz = rate.toFloat() / FRAME
        var w = min(patchStart(kc) / 2, (5000 / hz).toInt())
        w -= w % 4
        return w
    }

    /** The top bin made: 22.05 kHz, or the top of the spectrum under it. */
    fun topBin(rate: Int): Int = min(BINS, (22050 / (rate.toFloat() / FRAME)).toInt() + 1)

    /**
     * One frame's gains, in dB, for every bin from the cutoff up to [top]: what each band should be
     * ([pred], 20 dB units over [anchor]) against what the copy of the mid brings — the same over
     * the whole band, bins past the last band holding its gain — eased from the frame before
     * ([previous], updated in place; NaN at first).
     */
    fun gains(
        midRe: FloatArray, midIm: FloatArray, kc: Int, top: Int, width: Int,
        pred: FloatArray, anchor: Float, bands: Bands,
        bandGain: FloatArray, previous: FloatArray
    ) {
        val count = top - kc
        val g = scratch(count)
        java.util.Arrays.fill(g, 0, count, Float.NaN)
        var any = false
        for (b in 0 until bands.count) {
            val a = max(bands.lo[b], kc)
            val z = min(bands.hi[b], top)
            if (a >= z) {
                bandGain[b] = Float.NaN
                continue
            }
            var sum = 0f
            for (k in a until z) {
                val s = source(k, patchStart(kc), width)
                sum += midRe[s] * midRe[s] + midIm[s] * midIm[s]
            }
            val have = db(sum / (z - a))
            val want = anchor + 20f * pred[b]
            bandGain[b] = (want - have).coerceIn(-60f, MAX_BOOST_DB)
            for (k in a until z) g[k - kc] = bandGain[b]
            any = true
        }
        if (!any) return
        // Bins no band covers hold the gain of the one before them (the first, of the one after).
        for (i in 1 until count) if (g[i].isNaN()) g[i] = g[i - 1]
        var first = 0
        while (first < count && g[first].isNaN()) first++
        for (i in 0 until first) g[i] = g[first]
        for (i in 0 until count) {
            val old = previous[i]
            val v = g[i]
            previous[i] = when {
                old.isNaN() -> v
                v > old -> old + ATTACK * (v - old)
                else -> old + RELEASE * (v - old)
            }
        }
    }

    // A frame's gains before easing, kept: not an array made for every frame. One playback
    // thread per processor at a time, so one per thread.
    private val scratchHolder = ThreadLocal<FloatArray>()

    private fun scratch(size: Int): FloatArray {
        val have = scratchHolder.get()
        if (have != null && have.size >= size) return have
        return FloatArray(size).also(scratchHolder::set)
    }

    /** The bin [k] (at or over [start]) is copied from: a band's width at a time, down under it. */
    fun source(k: Int, start: Int, width: Int): Int = k - width * (1 + (k - start) / width)

    fun dbToAmplitude(gainDb: Float): Float = 10f.pow(gainDb / 20f)
}
