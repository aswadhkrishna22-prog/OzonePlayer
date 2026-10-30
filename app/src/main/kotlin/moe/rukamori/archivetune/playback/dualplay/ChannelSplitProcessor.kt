/*
 * Ozone Player (2026) | Dual Play — Channel Split Audio Processor
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.playback.dualplay

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One instance per ExoPlayer audio sink.  NEVER share between two sinks.
 *
 * [assignedSide] identifies which Player this processor belongs to:
 *   [ProcessorSide.LEFT]  → Player A: reads [DualPlayChannelState.modeA]
 *   [ProcessorSide.RIGHT] → Player B: reads [DualPlayChannelState.modeB]
 *
 * [EarMode] controls per-processor routing:
 *   LEFT_ONLY  → L = mono-mix (L+R)/2, R = 0
 *   RIGHT_ONLY → L = 0, R = mono-mix (L+R)/2
 *   STEREO     → passthrough (Dual Play off or "M" selected)
 *
 * Always in the sink chain; mode changes are instant (no seek/re-prepare needed).
 *
 * Supported encodings: PCM_16BIT and PCM_FLOAT. Others → [AudioFormat.NOT_SET]
 * (Media3 bypasses the processor transparently; debug banner is set).
 */
@UnstableApi
class ChannelSplitProcessor(
    private val assignedSide: ProcessorSide,
) : BaseAudioProcessor() {

    private var encoding = C.ENCODING_PCM_16BIT

    // ── BaseAudioProcessor ────────────────────────────────────────────────────

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        val ch  = inputAudioFormat.channelCount
        val supported =
            (enc == C.ENCODING_PCM_16BIT || enc == C.ENCODING_PCM_FLOAT) && ch == 2
        return if (supported) {
            encoding = enc
            inputAudioFormat
        } else {
            val desc = "encoding=$enc channelCount=$ch (assignedSide=$assignedSide)"
            Timber.tag(TAG).w(
                "ChannelSplitProcessor[%s]: unsupported format %s — bypassed",
                assignedSide, desc,
            )
            DualPlayChannelState.unsupportedFormatDescription = desc
            AudioFormat.NOT_SET
        }
    }

    override fun onFlush() = Unit
    override fun onReset() = Unit

    override fun queueInput(input: ByteBuffer) {
        val n = input.remaining()
        if (n == 0) return

        // Global kill-switch: when Dual Play is OFF → passthrough for both processors.
        if (DualPlayChannelState.mode == ChannelMode.OFF) {
            passThrough(input)
            return
        }

        // Read THIS processor's individual ear mode.
        val earMode = when (assignedSide) {
            ProcessorSide.LEFT  -> DualPlayChannelState.modeA
            ProcessorSide.RIGHT -> DualPlayChannelState.modeB
        }

        if (earMode == EarMode.STEREO) {
            passThrough(input)
            return
        }

        val keepLeft = earMode == EarMode.LEFT_ONLY

        when (encoding) {
            C.ENCODING_PCM_16BIT -> processShort(input, keepLeft = keepLeft)
            C.ENCODING_PCM_FLOAT -> processFloat(input, keepLeft = keepLeft)
            else                 -> passThrough(input)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun passThrough(input: ByteBuffer) {
        val n = input.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        out.order(ByteOrder.nativeOrder())
        if (input !== out) out.put(input)
        out.flip()
    }

    /** PCM 16-bit stereo interleaved: [L0_lo, L0_hi, R0_lo, R0_hi, …] */
    private fun processShort(input: ByteBuffer, keepLeft: Boolean) {
        val count = input.remaining()
        if (count < 4) return
        val frames = count / 4
        val out = replaceOutputBuffer(frames * 4)
        out.order(ByteOrder.nativeOrder())
        while (input.remaining() >= 4) {
            val l = input.short
            val r = input.short
            val mono = ((l.toInt() + r.toInt()) shr 1).toShort()
            if (keepLeft) { out.putShort(mono); out.putShort(0) }
            else          { out.putShort(0);    out.putShort(mono) }
        }
        out.flip()
    }

    /** PCM float stereo interleaved: [L0_f32, R0_f32, …] range [-1, 1] */
    private fun processFloat(input: ByteBuffer, keepLeft: Boolean) {
        val count = input.remaining()
        if (count < 8) return
        val frames = count / 8
        val out = replaceOutputBuffer(frames * 8)
        out.order(ByteOrder.nativeOrder())
        while (input.remaining() >= 8) {
            val l = input.float
            val r = input.float
            val mono = (l + r) * 0.5f
            if (keepLeft) { out.putFloat(mono); out.putFloat(0f) }
            else          { out.putFloat(0f);   out.putFloat(mono) }
        }
        out.flip()
    }

    companion object {
        private const val TAG = "ChannelSplitProcessor"
    }
}
