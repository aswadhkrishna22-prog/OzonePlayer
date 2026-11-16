/*
 * Ozone Player (2026)
 * Dither16 Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Dither16Test {

    @Test
    fun zeroSample_encodesToZero() {
        val dither = Dither16(2)
        dither.nextFrame()
        val out0 = dither.encode(0f, 0)
        val out1 = dither.encode(0f, 1)

        assertEquals(0.toShort(), out0)
        assertEquals(0.toShort(), out1)
    }

    @Test
    fun samples_encodeWithinValidShortBounds() {
        val dither = Dither16(2)
        val testValues = floatArrayOf(0.999f, -0.999f, 0.5f, -0.5f, 0.001f, -0.001f, 1.0f, -1.0f)

        for (v in testValues) {
            dither.nextFrame()
            val enc0 = dither.encode(v, 0)
            val enc1 = dither.encode(v, 1)

            assertTrue(enc0 in -32768..32767)
            assertTrue(enc1 in -32768..32767)
        }
    }

    @Test
    fun reset_clearsErrorFeedback() {
        val dither = Dither16(1)
        dither.nextFrame()
        dither.encode(0.333f, 0)

        dither.reset()
        dither.nextFrame()
        val out = dither.encode(0f, 0)
        assertEquals(0.toShort(), out)
    }
}
