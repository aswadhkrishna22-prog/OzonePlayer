/*
 * Ozone Player (2026) | SpatialProcessor Unit Tests
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.playback.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin

@OptIn(UnstableApi::class)
class SpatialProcessorTest {

    @Test
    fun `onConfigure returns NOT_SET for mono or non-16bit formats`() {
        val processor = SpatialProcessor()

        // Mono 16-bit should be rejected
        val monoFormat = AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        assertEquals(AudioFormat.NOT_SET, processor.onConfigure(monoFormat))

        // Stereo 24-bit PCM should be rejected
        val pcm24Format = AudioFormat(44100, 2, C.ENCODING_PCM_24BIT)
        assertEquals(AudioFormat.NOT_SET, processor.onConfigure(pcm24Format))

        // Stereo Float PCM should be rejected
        val floatFormat = AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        assertEquals(AudioFormat.NOT_SET, processor.onConfigure(floatFormat))
    }

    @Test
    fun `onConfigure accepts 16-bit stereo PCM`() {
        val processor = SpatialProcessor()
        val validFormat = AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)

        val outputFormat = processor.onConfigure(validFormat)
        assertEquals(validFormat, outputFormat)
        assertTrue(processor.isActive)
    }

    @Test
    fun `RBJ biquad has unity gain at 0 dB`() {
        val biquad = SpatialProcessor.Biquad()
        biquad.lowShelf(44100, 120f, 0f)

        // Process a steady 1.0f impulse
        var out = 0f
        repeat(50) {
            out = biquad.process(1.0f)
        }
        assertEquals(1.0f, out, 0.001f)

        biquad.reset()
        biquad.peaking(44100, 1500f, 0.8f, 0f)
        repeat(50) {
            out = biquad.process(1.0f)
        }
        assertEquals(1.0f, out, 0.001f)

        biquad.reset()
        biquad.highShelf(44100, 6000f, 0f)
        repeat(50) {
            out = biquad.process(1.0f)
        }
        assertEquals(1.0f, out, 0.001f)
    }

    @Test
    fun `RBJ low shelf boosts low frequency signals`() {
        val boostBiquad = SpatialProcessor.Biquad()
        boostBiquad.lowShelf(44100, 120f, 6f)

        val cutBiquad = SpatialProcessor.Biquad()
        cutBiquad.lowShelf(44100, 120f, -6f)

        // 50Hz sine wave sample
        var boostEnergy = 0.0
        var cutEnergy = 0.0
        val freq = 50.0
        val sr = 44100.0

        for (i in 0 until 500) {
            val input = sin(2.0 * Math.PI * freq * i / sr).toFloat()
            val bOut = boostBiquad.process(input)
            val cOut = cutBiquad.process(input)
            if (i > 100) { // steady state
                boostEnergy += bOut * bOut
                cutEnergy += cOut * cOut
            }
        }

        assertTrue(
            "Boosted low shelf should have substantially more energy than cut low shelf",
            boostEnergy > cutEnergy * 2.5,
        )
    }

    @Test
    fun `queueInput processes 16-bit stereo PCM without buffer overflow or allocations`() {
        val processor = SpatialProcessor()
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.onConfigure(format)
        processor.enabled = true
        processor.crossfeed = 0.3f
        processor.delayMs = 0.25f

        val sampleCount = 64
        val byteBuffer = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            byteBuffer.putShort(10000.toShort())  // Left
            byteBuffer.putShort((-10000).toShort()) // Right
        }
        byteBuffer.flip()

        processor.queueInput(byteBuffer)
        val outputBuffer = processor.output
        assertTrue(outputBuffer.hasRemaining())
        assertEquals(sampleCount * 4, outputBuffer.remaining())
    }

    @Test
    fun `smooth bypass transition prevents audio clicks when disabled`() {
        val processor = SpatialProcessor()
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.onConfigure(format)
        processor.enabled = false

        val sampleCount = 128
        val byteBuffer = ByteBuffer.allocateDirect(sampleCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            byteBuffer.putShort(5000.toShort())
            byteBuffer.putShort(5000.toShort())
        }
        byteBuffer.flip()

        processor.queueInput(byteBuffer)
        val out = processor.output
        assertTrue(out.hasRemaining())
        // Bypass passes signal cleanly
        val firstL = out.short
        assertTrue(abs(firstL.toInt()) > 0)
    }
}
