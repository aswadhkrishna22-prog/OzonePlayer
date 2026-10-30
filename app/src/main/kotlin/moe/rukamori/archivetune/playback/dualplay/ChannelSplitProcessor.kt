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

/**
 * One instance per ExoPlayer audio sink.  NEVER share this instance between
 * two sinks — it holds per-sink format state.
 *
 * [assignedChannel] tells this processor which output to produce when
 * [DualPlayChannelState.mode] is active:
 *   LEFT  → L = mono-mix (L+R)/2, R = 0
 *   RIGHT → L = 0, R = mono-mix (L+R)/2
 *
 * The processor is ALWAYS in the sink chain and always accepts audio.
 * When [DualPlayChannelState.mode] == OFF it copies input → output unchanged,
 * with no seek/re-prepare required for a mode change.
 *
 * Supported encodings:
 *   PCM_16BIT  — 2 bytes per sample, little-endian signed short
 *   PCM_FLOAT  — 4 bytes per sample, IEEE 754 float in [-1, 1]
 * Any other encoding (e.g. 24-bit int) → [AudioFormat.NOT_SET] returned from
 * [onConfigure] so Media3 skips the processor entirely (transparent passthrough),
 * a warning is logged and [DualPlayChannelState.unsupportedFormatDescription]
 * is set for the debug screen.
 */
@UnstableApi
class ChannelSplitProcessor(
    private val assignedChannel: ChannelMode,
) : BaseAudioProcessor() {

    /** Encoding decided at configure time; only valid when processor is active. */
    private var encoding = C.ENCODING_PCM_16BIT

    // ── BaseAudioProcessor callbacks ──────────────────────────────────────────

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        val ch  = inputAudioFormat.channelCount
        val supported =
            (enc == C.ENCODING_PCM_16BIT || enc == C.ENCODING_PCM_FLOAT) && ch == 2
        return if (supported) {
            encoding = enc
            // Clear any previous "unsupported" notice from this processor's side.
            // (The other processor may set it independently.)
            inputAudioFormat          // output format == input format: same bytes, same channels
        } else {
            val desc = "encoding=$enc channelCount=$ch (assignedChannel=$assignedChannel)"
            Timber.tag(TAG).w(
                "ChannelSplitProcessor[%s]: unsupported audio format %s — processor bypassed",
                assignedChannel, desc,
            )
            DualPlayChannelState.unsupportedFormatDescription = desc
            AudioFormat.NOT_SET       // processor skipped; Media3 routes audio past us unchanged
        }
    }

    /** No internal ring-buffer state; nothing to reset. */
    override fun onFlush() = Unit
    override fun onReset() = Unit

    override fun queueInput(input: ByteBuffer) {
        val mode = DualPlayChannelState.mode

        // Fast-path: dual play off or this processor is for the other channel.
        // The "other channel" guard handles an edge case where mode changes mid-frame.
        val apply = mode != ChannelMode.OFF && mode == assignedChannel
        if (!apply) {
            passThrough(input)
            return
        }

        when (encoding) {
            C.ENCODING_PCM_16BIT -> processShort(input, keepLeft = assignedChannel == ChannelMode.LEFT)
            C.ENCODING_PCM_FLOAT -> processFloat(input, keepLeft = assignedChannel == ChannelMode.LEFT)
            else                 -> passThrough(input)   // shouldn't reach here; guarded in onConfigure
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun passThrough(input: ByteBuffer) {
        val n = input.remaining()
        val out = replaceOutputBuffer(n)
        out.put(input)
        out.flip()
    }

    /** PCM 16-bit stereo interleaved: [L0_lo, L0_hi, R0_lo, R0_hi, L1_lo, …] */
    private fun processShort(input: ByteBuffer, keepLeft: Boolean) {
        val out = replaceOutputBuffer(input.remaining())
        while (input.remaining() >= 4) {
            val l = input.short
            val r = input.short
            // Arithmetic mean in int to avoid overflow, then clamp back to short range.
            val mono = ((l.toInt() + r.toInt()) ushr 1).toShort()
            if (keepLeft) {
                out.putShort(mono)
                out.putShort(0)
            } else {
                out.putShort(0)
                out.putShort(mono)
            }
        }
        out.flip()
    }

    /** PCM float stereo interleaved: [L0_f32, R0_f32, L1_f32, …] range [-1, 1] */
    private fun processFloat(input: ByteBuffer, keepLeft: Boolean) {
        val out = replaceOutputBuffer(input.remaining())
        while (input.remaining() >= 8) {
            val l = input.float
            val r = input.float
            val mono = (l + r) * 0.5f
            if (keepLeft) {
                out.putFloat(mono)
                out.putFloat(0f)
            } else {
                out.putFloat(0f)
                out.putFloat(mono)
            }
        }
        out.flip()
    }

    companion object {
        private const val TAG = "ChannelSplitProcessor"
    }
}
