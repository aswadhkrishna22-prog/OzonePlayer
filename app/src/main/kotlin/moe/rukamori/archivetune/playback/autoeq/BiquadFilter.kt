/*
 * Ozone Player (2026)
 * High-Precision Biquad IIR Filter (Robert Bristow-Johnson Audio EQ Cookbook)
 * Transposed Direct Form II Architecture
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.autoeq

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class BiquadFilter(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    // Transposed Direct Form II state variables (double-precision)
    private var z1 = 0.0
    private var z2 = 0.0

    /**
     * Processes one input sample through the filter.
     */
    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    /**
     * Clears internal state delays (e.g. on seek or flush).
     */
    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    companion object {
        private const val DEFAULT_SHELF_Q = 0.7071067811865476

        /**
         * Second-order Peaking EQ filter.
         */
        fun peaking(sampleRate: Double, freqHz: Double, q: Double, gainDb: Double): BiquadFilter {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.05))
            val cw = cos(w0)
            val a0 = 1.0 + alpha / a

            return BiquadFilter(
                b0 = (1.0 + alpha * a) / a0,
                b1 = (-2.0 * cw) / a0,
                b2 = (1.0 - alpha * a) / a0,
                a1 = (-2.0 * cw) / a0,
                a2 = (1.0 - alpha / a) / a0,
            )
        }

        /**
         * Second-order Low Shelf filter.
         */
        fun lowShelf(sampleRate: Double, freqHz: Double, gainDb: Double, q: Double = DEFAULT_SHELF_Q): BiquadFilter {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.05))
            val cw = cos(w0)
            val sa = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) + (a - 1.0) * cw + sa

            return BiquadFilter(
                b0 = (a * ((a + 1.0) - (a - 1.0) * cw + sa)) / a0,
                b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cw)) / a0,
                b2 = (a * ((a + 1.0) - (a - 1.0) * cw - sa)) / a0,
                a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cw)) / a0,
                a2 = ((a + 1.0) + (a - 1.0) * cw - sa) / a0,
            )
        }

        /**
         * Second-order High Shelf filter.
         */
        fun highShelf(sampleRate: Double, freqHz: Double, gainDb: Double, q: Double = DEFAULT_SHELF_Q): BiquadFilter {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * freqHz / sampleRate
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.05))
            val cw = cos(w0)
            val sa = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) - (a - 1.0) * cw + sa

            return BiquadFilter(
                b0 = (a * ((a + 1.0) + (a - 1.0) * cw + sa)) / a0,
                b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cw)) / a0,
                b2 = (a * ((a + 1.0) + (a - 1.0) * cw - sa)) / a0,
                a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cw)) / a0,
                a2 = ((a + 1.0) - (a - 1.0) * cw - sa) / a0,
            )
        }
    }
}
