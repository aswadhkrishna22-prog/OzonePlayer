/*
 * Ozone Player (2026)
 * Biquad Filter Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.autoeq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.abs

class BiquadFilterTest {

    @Test
    fun peakingFilter_zeroGain_actsAsPassthrough() {
        val filter = BiquadFilter.peaking(
            sampleRate = 48000.0,
            freqHz = 1000.0,
            q = 1.0,
            gainDb = 0.0,
        )

        val input = doubleArrayOf(0.5, -0.25, 0.8, -0.9, 0.1)
        val output = DoubleArray(input.size)

        for (i in input.indices) {
            output[i] = filter.process(input[i])
            assertEquals(input[i], output[i], 0.0001)
        }
    }

    @Test
    fun peakingFilter_impulseResponse_isStable() {
        val filter = BiquadFilter.peaking(
            sampleRate = 48000.0,
            freqHz = 1000.0,
            q = 2.0,
            gainDb = 6.0,
        )

        // Process impulse
        var maxAmp = 0.0
        val impulseResponse = DoubleArray(512)
        impulseResponse[0] = filter.process(1.0)
        for (i in 1 until 512) {
            val sample = filter.process(0.0)
            impulseResponse[i] = sample
            assertFalse(sample.isNaN())
            assertFalse(sample.isInfinite())
            maxAmp = maxOf(maxAmp, abs(sample))
        }

        // Tail of impulse response must decay to zero
        assertEquals(0.0, impulseResponse[511], 0.001)
    }

    @Test
    fun lowShelf_impulseResponse_isStable() {
        val filter = BiquadFilter.lowShelf(
            sampleRate = 44100.0,
            freqHz = 120.0,
            gainDb = 5.0,
        )

        for (i in 0 until 200) {
            val input = if (i == 0) 1.0 else 0.0
            val out = filter.process(input)
            assertFalse(out.isNaN())
            assertFalse(out.isInfinite())
        }
    }

    @Test
    fun highShelf_impulseResponse_isStable() {
        val filter = BiquadFilter.highShelf(
            sampleRate = 44100.0,
            freqHz = 8000.0,
            gainDb = -4.0,
        )

        for (i in 0 until 200) {
            val input = if (i == 0) 1.0 else 0.0
            val out = filter.process(input)
            assertFalse(out.isNaN())
            assertFalse(out.isInfinite())
        }
    }

    @Test
    fun reset_clearsInternalState() {
        val filter = BiquadFilter.peaking(48000.0, 1000.0, 1.0, 6.0)
        filter.process(1.0)
        filter.process(0.5)

        filter.reset()

        val afterReset = filter.process(0.0)
        assertEquals(0.0, afterReset, 0.00001)
    }
}
