/*
 * Ozone Player (2026) | ChannelSplitProcessor Unit Tests
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.playback.dualplay

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class ChannelSplitProcessorTest {

    @Before
    fun setUp() {
        DualPlayChannelState.mode = ChannelMode.ACTIVE
        DualPlayChannelState.modeA = EarMode.LEFT_ONLY
        DualPlayChannelState.modeB = EarMode.RIGHT_ONLY
        DualPlayChannelState.unsupportedFormatDescription = null
    }

    @After
    fun tearDown() {
        DualPlayChannelState.mode = ChannelMode.OFF
        DualPlayChannelState.modeA = EarMode.STEREO
        DualPlayChannelState.modeB = EarMode.STEREO
        DualPlayChannelState.unsupportedFormatDescription = null
    }

    @Test
    fun `onConfigure accepts mono, stereo, and multichannel PCM, always outputting stereo`() {
        val processor = ChannelSplitProcessor(ProcessorSide.LEFT)

        // Mono 16-bit PCM -> must configure output to stereo (2 channels)
        val monoFormat = AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        val outMono = processor.configure(monoFormat)
        assertNotEquals(AudioFormat.NOT_SET, outMono)
        assertEquals(44100, outMono.sampleRate)
        assertEquals(2, outMono.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, outMono.encoding)
        assertTrue(processor.isActive)

        // Stereo 16-bit PCM -> must configure output to stereo (2 channels)
        val stereoFormat = AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        val outStereo = processor.configure(stereoFormat)
        assertEquals(2, outStereo.channelCount)
        assertEquals(48000, outStereo.sampleRate)
        assertTrue(processor.isActive)

        // 5.1 Multichannel (6 channels) -> must configure output to stereo (2 channels)
        val surroundFormat = AudioFormat(48000, 6, C.ENCODING_PCM_16BIT)
        val outSurround = processor.configure(surroundFormat)
        assertEquals(2, outSurround.channelCount)
        assertTrue(processor.isActive)

        // Float PCM -> supported and outputs stereo
        val floatFormat = AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        val outFloat = processor.configure(floatFormat)
        assertEquals(2, outFloat.channelCount)
        assertEquals(C.ENCODING_PCM_FLOAT, outFloat.encoding)
        assertTrue(processor.isActive)
    }

    @Test
    fun `mono 16-bit input routed to LEFT_ONLY outputs stereo with strict digital zero in right ear`() {
        val processor = ChannelSplitProcessor(ProcessorSide.LEFT)
        val inFormat = AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        processor.configure(inFormat)
        processor.flush()

        DualPlayChannelState.modeA = EarMode.LEFT_ONLY

        val frameCount = 100
        val input = ByteBuffer.allocateDirect(frameCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            input.putShort(12345.toShort())
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        out.order(ByteOrder.nativeOrder())

        assertEquals(frameCount * 4, out.remaining())

        var rightChannelLeakEnergy = 0.0
        while (out.remaining() >= 4) {
            val l = out.short
            val r = out.short
            assertEquals(12345.toShort(), l)
            assertEquals(0.toShort(), r)
            rightChannelLeakEnergy += r.toDouble() * r.toDouble()
        }
        assertEquals(0.0, rightChannelLeakEnergy, 0.0)
    }

    @Test
    fun `mono 16-bit input routed to RIGHT_ONLY outputs stereo with strict digital zero in left ear`() {
        val processor = ChannelSplitProcessor(ProcessorSide.RIGHT)
        val inFormat = AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        processor.configure(inFormat)
        processor.flush()

        DualPlayChannelState.modeB = EarMode.RIGHT_ONLY

        val frameCount = 100
        val input = ByteBuffer.allocateDirect(frameCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            input.putShort(20000.toShort())
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        out.order(ByteOrder.nativeOrder())

        assertEquals(frameCount * 4, out.remaining())

        var leftChannelLeakEnergy = 0.0
        while (out.remaining() >= 4) {
            val l = out.short
            val r = out.short
            assertEquals(0.toShort(), l)
            assertEquals(20000.toShort(), r)
            leftChannelLeakEnergy += l.toDouble() * l.toDouble()
        }
        assertEquals(0.0, leftChannelLeakEnergy, 0.0)
    }

    @Test
    fun `stereo 16-bit input routed to LEFT_ONLY downmixes symmetrically and zeroes right ear`() {
        val processor = ChannelSplitProcessor(ProcessorSide.LEFT)
        val inFormat = AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(inFormat)
        processor.flush()

        DualPlayChannelState.modeA = EarMode.LEFT_ONLY

        val frameCount = 50
        val input = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            input.putShort(10000.toShort()) // L
            input.putShort(4000.toShort())  // R
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        out.order(ByteOrder.nativeOrder())

        assertEquals(frameCount * 4, out.remaining())

        var rightChannelLeakEnergy = 0.0
        while (out.remaining() >= 4) {
            val l = out.short
            val r = out.short
            assertEquals(7000.toShort(), l) // (10000 + 4000) / 2
            assertEquals(0.toShort(), r)
            rightChannelLeakEnergy += r.toDouble() * r.toDouble()
        }
        assertEquals(0.0, rightChannelLeakEnergy, 0.0)
    }

    @Test
    fun `stereo 16-bit input routed to RIGHT_ONLY downmixes symmetrically and zeroes left ear`() {
        val processor = ChannelSplitProcessor(ProcessorSide.RIGHT)
        val inFormat = AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(inFormat)
        processor.flush()

        DualPlayChannelState.modeB = EarMode.RIGHT_ONLY

        val frameCount = 50
        val input = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            input.putShort((-8000).toShort()) // L
            input.putShort(2000.toShort())   // R
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        out.order(ByteOrder.nativeOrder())

        assertEquals(frameCount * 4, out.remaining())

        var leftChannelLeakEnergy = 0.0
        while (out.remaining() >= 4) {
            val l = out.short
            val r = out.short
            assertEquals(0.toShort(), l)
            assertEquals((-3000).toShort(), r) // (-8000 + 2000) / 2
            leftChannelLeakEnergy += l.toDouble() * l.toDouble()
        }
        assertEquals(0.0, leftChannelLeakEnergy, 0.0)
    }

    @Test
    fun `multichannel 5_1 input downmixes without bypass and zeroes opposite ear`() {
        val processor = ChannelSplitProcessor(ProcessorSide.LEFT)
        val inFormat = AudioFormat(48000, 6, C.ENCODING_PCM_16BIT)
        processor.configure(inFormat)
        processor.flush()

        DualPlayChannelState.modeA = EarMode.LEFT_ONLY

        val frameCount = 20
        val input = ByteBuffer.allocateDirect(frameCount * 6 * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            for (ch in 0 until 6) {
                input.putShort(6000.toShort())
            }
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        out.order(ByteOrder.nativeOrder())

        assertEquals(frameCount * 4, out.remaining())

        while (out.remaining() >= 4) {
            val l = out.short
            val r = out.short
            assertEquals(6000.toShort(), l)
            assertEquals(0.toShort(), r)
        }
    }

    @Test
    fun `PCM float stereo routing guarantees zero leak in opposite ear`() {
        val processorL = ChannelSplitProcessor(ProcessorSide.LEFT)
        val floatFormat = AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        processorL.configure(floatFormat)
        processorL.flush()

        DualPlayChannelState.modeA = EarMode.LEFT_ONLY

        val frameCount = 30
        val input = ByteBuffer.allocateDirect(frameCount * 8).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            input.putFloat(0.6f)
            input.putFloat(-0.2f)
        }
        input.flip()

        processorL.queueInput(input)
        val out = processorL.output
        out.order(ByteOrder.nativeOrder())

        assertEquals(frameCount * 8, out.remaining())

        while (out.remaining() >= 8) {
            val l = out.float
            val r = out.float
            assertEquals(0.2f, l, 0.0001f) // (0.6 - 0.2) * 0.5
            assertEquals(0.0f, r, 0.0f)
        }
    }

    @Test
    fun `dynamic ear switching and swap instantly isolates opposite channels`() {
        val processor = ChannelSplitProcessor(ProcessorSide.LEFT)
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // 1. LEFT_ONLY
        DualPlayChannelState.modeA = EarMode.LEFT_ONLY
        val input1 = ByteBuffer.allocateDirect(40).order(ByteOrder.nativeOrder())
        repeat(10) { input1.putShort(10000.toShort()); input1.putShort(10000.toShort()) }
        input1.flip()
        processor.queueInput(input1)
        val out1 = processor.output
        out1.order(ByteOrder.nativeOrder())
        assertEquals(10000.toShort(), out1.short)
        assertEquals(0.toShort(), out1.short)

        // 2. Dynamic switch to RIGHT_ONLY (no reconfigure)
        DualPlayChannelState.modeA = EarMode.RIGHT_ONLY
        val input2 = ByteBuffer.allocateDirect(40).order(ByteOrder.nativeOrder())
        repeat(10) { input2.putShort(10000.toShort()); input2.putShort(10000.toShort()) }
        input2.flip()
        processor.queueInput(input2)
        val out2 = processor.output
        out2.order(ByteOrder.nativeOrder())
        assertEquals(0.toShort(), out2.short)
        assertEquals(10000.toShort(), out2.short)

        // 3. Dynamic switch to STEREO
        DualPlayChannelState.modeA = EarMode.STEREO
        val input3 = ByteBuffer.allocateDirect(40).order(ByteOrder.nativeOrder())
        repeat(10) { input3.putShort(8000.toShort()); input3.putShort(4000.toShort()) }
        input3.flip()
        processor.queueInput(input3)
        val out3 = processor.output
        out3.order(ByteOrder.nativeOrder())
        assertEquals(8000.toShort(), out3.short)
        assertEquals(4000.toShort(), out3.short)
    }

    @Test
    fun `when Dual Play master mode is OFF passthrough is active`() {
        val processor = ChannelSplitProcessor(ProcessorSide.LEFT)
        val format = AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        DualPlayChannelState.mode = ChannelMode.OFF
        DualPlayChannelState.modeA = EarMode.LEFT_ONLY // ignored because master mode is OFF

        val input = ByteBuffer.allocateDirect(40).order(ByteOrder.nativeOrder())
        repeat(10) { input.putShort(5000.toShort()); input.putShort(-5000.toShort()) }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        out.order(ByteOrder.nativeOrder())

        assertEquals(5000.toShort(), out.short)
        assertEquals((-5000).toShort(), out.short)
    }
}
