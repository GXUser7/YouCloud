package com.example.myapplication.player.restore

import org.json.JSONObject
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.tanh

/**
 * A restoration model as tools/restore/train.py writes it (YCRM v1): every 5.8 ms it reads the
 * bands of the sound under a lossy file's cutoff and says how loud each band over it was before
 * the file was made. A small GRU — a linear layer into it, one or two GRU layers, a linear layer
 * out — worked out here in plain Kotlin: no runtime to ship, nothing but a few hundred thousand
 * multiply-adds a frame.
 */
class RestoreModel private constructor(
    val name: String,
    /** Band edges in Hz, as many as the bands and one more. */
    val edges: FloatArray,
    private val hidden: Int,
    private val layers: Int,
    private val inW: FloatArray,
    private val inB: FloatArray,
    private val wih: Array<FloatArray>,
    private val whh: Array<FloatArray>,
    private val bih: Array<FloatArray>,
    private val bhh: Array<FloatArray>,
    private val outW: FloatArray,
    private val outB: FloatArray,
    /** How far off it was, in dB a band, on songs it never heard (and a fixed fall-off's, to compare). */
    val heldOutErrorDb: Float?,
    val fixedFallOffErrorDb: Float?,
    /** The fixed fall-off its answer is blended with: dB at the cutoff, dB an octave over it, and the model's share. */
    private val priorAtCutoffDb: Float = 0f,
    private val priorPerOctaveDb: Float = 0f,
    private val modelWeight: Float = 1f
) {
    val bands: Int get() = edges.size - 1
    val inputs: Int get() = 2 * bands + 2

    /** What one stream of sound keeps between frames: the GRU's memory, and room to work in. */
    inner class State {
        internal val h = Array(layers) { FloatArray(hidden) }
        private val x = FloatArray(hidden)
        private val gates = FloatArray(3 * hidden)
        private val recurrent = FloatArray(3 * hidden)

        fun reset() {
            for (layer in h) layer.fill(0f)
        }

        /** [input]: [inputs] values; [output]: [bands] values, in 20 dB units over the anchor. */
        fun step(input: FloatArray, output: FloatArray) {
            val n = inputs
            for (i in 0 until hidden) {
                var s = inB[i]
                val row = i * n
                for (j in 0 until n) s += inW[row + j] * input[j]
                x[i] = tanh(s)
            }
            for (layer in 0 until layers) {
                val prev = h[layer]
                val wi = wih[layer]
                val wh = whh[layer]
                val bi = bih[layer]
                val bh = bhh[layer]
                for (r in 0 until 3 * hidden) {
                    var a = bi[r]
                    var b = bh[r]
                    val row = r * hidden
                    for (j in 0 until hidden) {
                        a += wi[row + j] * x[j]
                        b += wh[row + j] * prev[j]
                    }
                    gates[r] = a
                    recurrent[r] = b
                }
                // PyTorch's order: reset, update, new.
                for (i in 0 until hidden) {
                    val reset = sigmoid(gates[i] + recurrent[i])
                    val update = sigmoid(gates[hidden + i] + recurrent[hidden + i])
                    val candidate = tanh(gates[2 * hidden + i] + reset * recurrent[2 * hidden + i])
                    prev[i] = (1f - update) * candidate + update * prev[i]
                }
                System.arraycopy(prev, 0, x, 0, hidden)
            }
            for (o in 0 until bands) {
                var s = outB[o]
                val row = o * hidden
                for (j in 0 until hidden) s += outW[row + j] * x[j]
                output[o] = s
            }
        }
    }

    /**
     * [pred] (as [State.step] left it) half and half with the fixed fall-off its file carries, in
     * place: on songs neither heard, the two together come closer than either alone.
     */
    fun blendPrior(pred: FloatArray, cutoffHz: Float) {
        if (modelWeight >= 1f) return
        for (b in 0 until bands) {
            val center = (edges[b] + edges[b + 1]) / 2f
            val octaves = log2(maxOf(center, cutoffHz + 1f) / cutoffHz)
            val prior = (priorAtCutoffDb + priorPerOctaveDb * octaves) / 20f
            pred[b] = modelWeight * pred[b] + (1f - modelWeight) * prior
        }
    }

    companion object {
        private val MAGIC = byteArrayOf('Y'.code.toByte(), 'C'.code.toByte(), 'R'.code.toByte(), 'M'.code.toByte())

        private fun sigmoid(v: Float): Float = 1f / (1f + exp(-v))

        fun read(stream: InputStream): RestoreModel {
            val input = DataInputStream(stream.buffered())
            val magic = ByteArray(4).also(input::readFully)
            if (!magic.contentEquals(MAGIC)) throw IOException("not a restoration model")
            val ints = ByteArray(8).also(input::readFully)
            val head = ByteBuffer.wrap(ints).order(ByteOrder.LITTLE_ENDIAN)
            val version = head.int
            if (version != 1) throw IOException("restoration model version $version")
            val header = JSONObject(String(ByteArray(head.int).also(input::readFully), Charsets.UTF_8))
            val tensors = mutableMapOf<String, FloatArray>()
            val list = header.getJSONArray("tensors")
            for (i in 0 until list.length()) {
                val t = list.getJSONObject(i)
                val shape = t.getJSONArray("shape")
                var count = 1
                for (d in 0 until shape.length()) count *= shape.getInt(d)
                val bytes = ByteArray(count * 4).also(input::readFully)
                val floats = FloatArray(count)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                tensors[t.getString("name")] = floats
            }
            val edgesJson = header.getJSONArray("edges")
            val layers = header.getInt("layers")
            val metrics = header.optJSONObject("metrics")
            val prior = header.optJSONObject("prior")
            fun tensor(name: String) = tensors[name] ?: throw IOException("restoration model lacks $name")
            if (header.getInt("frame") != RestoreDsp.FRAME || header.getInt("hop") != RestoreDsp.HOP) {
                throw IOException("restoration model made for other frames")
            }
            return RestoreModel(
                name = header.getString("name"),
                edges = FloatArray(edgesJson.length()) { edgesJson.getDouble(it).toFloat() },
                hidden = header.getInt("hidden"),
                layers = layers,
                inW = tensor("inp.weight"),
                inB = tensor("inp.bias"),
                wih = Array(layers) { tensor("gru.weight_ih_l$it") },
                whh = Array(layers) { tensor("gru.weight_hh_l$it") },
                bih = Array(layers) { tensor("gru.bias_ih_l$it") },
                bhh = Array(layers) { tensor("gru.bias_hh_l$it") },
                outW = tensor("out.weight"),
                outB = tensor("out.bias"),
                heldOutErrorDb = metrics?.optDouble("heldOutBandErrorDb")?.takeIf { !it.isNaN() }?.toFloat(),
                fixedFallOffErrorDb = metrics?.optDouble("fixedFallOffErrorDb")?.takeIf { !it.isNaN() }?.toFloat(),
                priorAtCutoffDb = prior?.optDouble("atCutoffDb", 0.0)?.toFloat() ?: 0f,
                priorPerOctaveDb = prior?.optDouble("perOctaveDb", 0.0)?.toFloat() ?: 0f,
                modelWeight = prior?.optDouble("weight", 1.0)?.toFloat() ?: 1f
            ).also { model ->
                if (model.inW.size != model.hidden * model.inputs || model.outW.size != model.bands * model.hidden) {
                    throw IOException("restoration model's tensors don't fit its bands")
                }
            }
        }
    }
}
