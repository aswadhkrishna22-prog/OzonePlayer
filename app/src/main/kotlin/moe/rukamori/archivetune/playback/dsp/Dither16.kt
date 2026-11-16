/*
 * Ozone Player (2026)
 * High-Precision TPDF Audio Dithering with High-Pass Error Feedback Noise Shaping
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.dsp

import kotlin.math.roundToInt

/**
 * Quantizes 32-bit floating point samples (-1.0f..1.0f) to 16-bit signed PCM with
 * Triangular Probability Density Function (TPDF) dither and error-feedback noise shaping.
 *
 * TPDF dither completely decorrelates quantization noise from the audio signal, eliminating
 * harsh harmonic distortion and grainy artifacts on quiet decay tails.
 * Error-feedback noise shaping shifts quantization noise upward into higher frequencies
 * where the human ear is significantly less sensitive.
 */
class Dither16(private val channelCount: Int) {
    private val error = FloatArray(channelCount.coerceAtLeast(1))
    private var lcgState = -0x61c8864680b583ebL
    private var currentDither = 0f

    /**
     * Generates a new TPDF dither value for the next audio frame.
     * Uses a fast 64-bit XorShift pseudo-random generator.
     */
    fun nextFrame() {
        var s = lcgState
        s = s xor (s shl 13)
        s = s xor (s ushr 7)
        s = s xor (s shl 17)
        lcgState = s

        val u1 = ((s ushr 40).toInt() and 0xFFFF) / 65536f
        val u2 = ((s ushr 8).toInt() and 0xFFFF) / 65536f
        // Summing two independent uniform random variables yields an exact triangular PDF (−1..1)
        currentDither = (u1 - 0.5f) + (u2 - 0.5f)
    }

    /**
     * Quantizes one float sample to 16-bit signed PCM Short.
     */
    fun encode(sample: Float, channel: Int): Short {
        if (sample == 0f) {
            if (channel in error.indices) error[channel] = 0f
            return 0
        }

        val ch = if (channel in error.indices) channel else 0
        val target = sample * 32767f + error[ch]
        val quantized = (target + currentDither).roundToInt().coerceIn(-32768, 32767)
        error[ch] = (target - quantized).coerceIn(-2f, 2f)
        return quantized.toShort()
    }

    fun reset() {
        error.fill(0f)
    }
}
