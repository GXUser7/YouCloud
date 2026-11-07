package com.example.myapplication.player.restore

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An in-place radix-2 FFT of one fixed size, its twiddles and bit reversal worked out once. Plain
 * loops over float arrays, so ART compiles them to tight code: a 1024-point transform is some
 * five thousand butterflies, well under a tenth of a millisecond on a phone.
 */
internal class Fft(val size: Int) {
    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val reversed = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "size must be a power of two" }
        for (i in 0 until size / 2) {
            cosTable[i] = cos(2.0 * PI * i / size).toFloat()
            sinTable[i] = sin(2.0 * PI * i / size).toFloat()
        }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reversed[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Forward (e^-i) when [inverse] is false; the inverse is not scaled by 1/size. */
    fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        val sign = if (inverse) 1f else -1f
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
    }
}
