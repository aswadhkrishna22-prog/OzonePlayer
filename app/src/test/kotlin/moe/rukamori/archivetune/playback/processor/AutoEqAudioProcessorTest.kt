/*
 * Ozone Player (2026)
 * AutoEqAudioProcessor Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import moe.rukamori.archivetune.playback.autoeq.AutoEqFilter
import moe.rukamori.archivetune.playback.autoeq.AutoEqFilterType
import moe.rukamori.archivetune.playback.autoeq.AutoEqProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AutoEqAudioProcessorTest {

    private val testProfile = AutoEqProfile(
        name = "Sony WH-1000XM4 Test",
        source = "oratory1990",
        preampDb = -5.0f,
        filters = listOf(
            AutoEqFilter(AutoEqFilterType.LOW_SHELF, 105f, 4.0f, 0.71f),
            AutoEqFilter(AutoEqFilterType.PEAK, 1000f, -2.5f, 1.4f),
            AutoEqFilter(AutoEqFilterType.HIGH_SHELF, 8000f, 2.0f, 0.71f),
        )
    )

    @Test
    fun configure_supportsPcm16AndFloat() {
        val processor = AutoEqAudioProcessor()

        val format16 = AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        val configured16 = processor.configure(format16)
        assertEquals(format16, configured16)

        val formatFloat = AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        val configuredFloat = processor.configure(formatFloat)
        assertEquals(formatFloat, configuredFloat)

        val formatInvalid = AudioFormat(48000, 2, C.ENCODING_INVALID)
        val configuredInvalid = processor.configure(formatInvalid)
        assertEquals(AudioFormat.NOT_SET, configuredInvalid)
    }

    @Test
    fun queueInput_whenBypassed_copiesVerbatim() {
        val processor = AutoEqAudioProcessor()
        processor.enabled = false
        processor.profile = null

        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putShort(1000.toShort())
        input.putShort((-2000).toShort())
        input.putShort(3000.toShort())
        input.putShort((-4000).toShort())
        input.flip()

        processor.queueInput(input)
        val output = processor.output

        assertEquals(8, output.remaining())
        assertEquals(1000.toShort(), output.short)
        assertEquals((-2000).toShort(), output.short)
        assertEquals(3000.toShort(), output.short)
        assertEquals((-4000).toShort(), output.short)
    }

    @Test
    fun queueInput_whenActive_processesPcm16WithoutClipping() {
        val processor = AutoEqAudioProcessor()
        processor.enabled = true
        processor.profile = testProfile

        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)

        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        // Put non-zero audio samples
        repeat(4) {
            input.putShort(16000.toShort())
            input.putShort((-16000).toShort())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output

        assertEquals(16, output.remaining())
        while (output.hasRemaining()) {
            val sample = output.short
            assertTrue(sample in -32768..32767)
        }
    }

    @Test
    fun queueInput_whenActive_processesFloatPcm() {
        val processor = AutoEqAudioProcessor()
        processor.enabled = true
        processor.profile = testProfile

        val format = AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        processor.configure(format)

        val input = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
        repeat(4) {
            input.putFloat(0.5f)
            input.putFloat(-0.5f)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output

        assertEquals(32, output.remaining())
        while (output.hasRemaining()) {
            val sample = output.float
            assertFalse(sample.isNaN())
            assertFalse(sample.isInfinite())
            assertTrue(sample in -1.0f..1.0f)
        }
    }

    @Test
    fun flushAndReset_workWithoutErrors() {
        val processor = AutoEqAudioProcessor()
        processor.enabled = true
        processor.profile = testProfile
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))

        processor.flush()
        processor.reset()
        assertFalse(processor.isActive)
    }
}
