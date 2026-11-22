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
 * One instance per ExoPlayer audio sink. NEVER share between two sinks.
 *
 * [assignedSide] identifies which Player this processor belongs to:
 *   [ProcessorSide.LEFT]  → Player A: reads [DualPlayChannelState.modeA]
 *   [ProcessorSide.RIGHT] → Player B: reads [DualPlayChannelState.modeB]
 *
 * [EarMode] controls per-processor routing:
 *   LEFT_ONLY  → L = mono-mix, R = 0 (silence)
 *   RIGHT_ONLY → L = 0 (silence), R = mono-mix
 *   STEREO     → passthrough (or duplicate mono to both channels)
 *
 * Output format is ALWAYS stereo (2 channels) for supported PCM encodings,
 * guaranteeing clean channel separation even for mono (1-ch) or multichannel (>2-ch) sources.
 *
 * Mode changes are instant and dynamic (no seek/re-prepare needed).
 */
@UnstableApi
class ChannelSplitProcessor(
    private val assignedSide: ProcessorSide,
) : BaseAudioProcessor() {

    private var encoding = C.ENCODING_PCM_16BIT
    private var inputChannels = 2
    private var lastLoggedMode: EarMode? = null
    private var lastRmsLogTime = 0L
    private var sumSqLeft = 0.0
    private var sumSqRight = 0.0
    private var sampleCount = 0L

    // ── BaseAudioProcessor ────────────────────────────────────────────────────

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        val ch  = inputAudioFormat.channelCount
        val sr  = inputAudioFormat.sampleRate

        // Support any channel count >= 1 for PCM_16BIT and PCM_FLOAT.
        // The output format is ALWAYS 2 channels (stereo) so channel isolation is preserved.
        val supported = (enc == C.ENCODING_PCM_16BIT || enc == C.ENCODING_PCM_FLOAT) && ch >= 1
        val currentMode = when (assignedSide) {
            ProcessorSide.LEFT  -> DualPlayChannelState.modeA
            ProcessorSide.RIGHT -> DualPlayChannelState.modeB
        }

        return if (supported) {
            encoding = enc
            inputChannels = ch
            DualPlayChannelState.unsupportedFormatDescription = null
            val encName = if (enc == C.ENCODING_PCM_16BIT) "PCM_16BIT" else "PCM_FLOAT"
            Timber.tag(TAG).i(
                "ChannelSplitProcessor[%s]: onConfigure configured: encoding=%s ($enc), inputChannels=%d -> outputChannels=2, sampleRate=%d, mode=%s",
                assignedSide, encName, ch, sr, currentMode,
            )
            AudioFormat(sr, 2, enc)
        } else {
            val desc = "encoding=$enc channelCount=$ch sampleRate=$sr (assignedSide=$assignedSide, mode=$currentMode)"
            Timber.tag(TAG).w(
                "ChannelSplitProcessor[%s]: unsupported format %s — bypassed",
                assignedSide, desc,
            )
            DualPlayChannelState.unsupportedFormatDescription = desc
            AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        val currentMode = when (assignedSide) {
            ProcessorSide.LEFT  -> DualPlayChannelState.modeA
            ProcessorSide.RIGHT -> DualPlayChannelState.modeB
        }
        sumSqLeft = 0.0
        sumSqRight = 0.0
        sampleCount = 0L
        Timber.tag(TAG).d("ChannelSplitProcessor[%s]: onFlush called, activeMode=%s", assignedSide, currentMode)
    }

    override fun onReset() {
        sumSqLeft = 0.0
        sumSqRight = 0.0
        sampleCount = 0L
        inputChannels = 2
        encoding = C.ENCODING_PCM_16BIT
        Timber.tag(TAG).d("ChannelSplitProcessor[%s]: onReset called", assignedSide)
    }

    override fun queueInput(input: ByteBuffer) {
        val n = input.remaining()
        if (n == 0) return

        val earMode = if (DualPlayChannelState.mode == ChannelMode.OFF) {
            EarMode.STEREO
        } else {
            when (assignedSide) {
                ProcessorSide.LEFT  -> DualPlayChannelState.modeA
                ProcessorSide.RIGHT -> DualPlayChannelState.modeB
            }
        }

        if (earMode != lastLoggedMode) {
            lastLoggedMode = earMode
            Timber.tag(TAG).i(
                "ChannelSplitProcessor[%s]: earMode changed to %s (encoding=%d, inputChannels=%d)",
                assignedSide, earMode, encoding, inputChannels,
            )
        }

        when (encoding) {
            C.ENCODING_PCM_16BIT -> processShort(input, earMode)
            C.ENCODING_PCM_FLOAT -> processFloat(input, earMode)
            else -> passThrough(input)
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

    /**
     * Process 16-bit PCM input of [inputChannels] channels and output 2-channel stereo.
     * Muted channel is guaranteed absolute digital silence (0).
     */
    private fun processShort(input: ByteBuffer, earMode: EarMode) {
        val bytesPerFrame = inputChannels * 2
        val frames = input.remaining() / bytesPerFrame
        if (frames == 0) return

        // Fast-path: stereo input and STEREO earMode -> direct copy
        if (inputChannels == 2 && earMode == EarMode.STEREO) {
            passThrough(input)
            return
        }

        val out = replaceOutputBuffer(frames * 4)
        out.order(ByteOrder.nativeOrder())
        val isDebug = moe.rukamori.archivetune.BuildConfig.DEBUG

        when (inputChannels) {
            1 -> {
                // Mono input: expand to stereo with clean channel routing
                for (i in 0 until frames) {
                    val sample = input.short
                    val outL: Short
                    val outR: Short
                    when (earMode) {
                        EarMode.LEFT_ONLY  -> { outL = sample; outR = 0 }
                        EarMode.RIGHT_ONLY -> { outL = 0; outR = sample }
                        EarMode.STEREO     -> { outL = sample; outR = sample }
                    }
                    out.putShort(outL)
                    out.putShort(outR)

                    if (isDebug) {
                        accumulateRms(outL.toDouble() / 32768.0, outR.toDouble() / 32768.0)
                    }
                }
            }
            2 -> {
                // Stereo input: downmix to mono when routing to single ear, or passthrough
                for (i in 0 until frames) {
                    val l = input.short
                    val r = input.short
                    val outL: Short
                    val outR: Short
                    when (earMode) {
                        EarMode.LEFT_ONLY -> {
                            val mono = ((l.toInt() + r.toInt()) / 2).toShort()
                            outL = mono
                            outR = 0
                        }
                        EarMode.RIGHT_ONLY -> {
                            val mono = ((l.toInt() + r.toInt()) / 2).toShort()
                            outL = 0
                            outR = mono
                        }
                        EarMode.STEREO -> {
                            outL = l
                            outR = r
                        }
                    }
                    out.putShort(outL)
                    out.putShort(outR)

                    if (isDebug) {
                        accumulateRms(outL.toDouble() / 32768.0, outR.toDouble() / 32768.0)
                    }
                }
            }
            else -> {
                // Multichannel (>2 channels): downmix across all channels
                for (i in 0 until frames) {
                    var sum = 0
                    for (ch in 0 until inputChannels) {
                        sum += input.short.toInt()
                    }
                    val mono = (sum / inputChannels).coerceIn(-32768, 32767).toShort()
                    val outL: Short
                    val outR: Short
                    when (earMode) {
                        EarMode.LEFT_ONLY  -> { outL = mono; outR = 0 }
                        EarMode.RIGHT_ONLY -> { outL = 0; outR = mono }
                        EarMode.STEREO     -> { outL = mono; outR = mono }
                    }
                    out.putShort(outL)
                    out.putShort(outR)

                    if (isDebug) {
                        accumulateRms(outL.toDouble() / 32768.0, outR.toDouble() / 32768.0)
                    }
                }
            }
        }
        out.flip()

        if (isDebug) {
            maybeLogRms("PCM_16BIT", earMode)
        }
    }

    /**
     * Process 32-bit float PCM input of [inputChannels] channels and output 2-channel stereo.
     * Muted channel is guaranteed absolute digital silence (0.0f).
     */
    private fun processFloat(input: ByteBuffer, earMode: EarMode) {
        val bytesPerFrame = inputChannels * 4
        val frames = input.remaining() / bytesPerFrame
        if (frames == 0) return

        // Fast-path: stereo input and STEREO earMode -> direct copy
        if (inputChannels == 2 && earMode == EarMode.STEREO) {
            passThrough(input)
            return
        }

        val out = replaceOutputBuffer(frames * 8)
        out.order(ByteOrder.nativeOrder())
        val isDebug = moe.rukamori.archivetune.BuildConfig.DEBUG

        when (inputChannels) {
            1 -> {
                for (i in 0 until frames) {
                    val sample = input.float
                    val outL: Float
                    val outR: Float
                    when (earMode) {
                        EarMode.LEFT_ONLY  -> { outL = sample; outR = 0f }
                        EarMode.RIGHT_ONLY -> { outL = 0f; outR = sample }
                        EarMode.STEREO     -> { outL = sample; outR = sample }
                    }
                    out.putFloat(outL)
                    out.putFloat(outR)

                    if (isDebug) {
                        accumulateRms(outL.toDouble(), outR.toDouble())
                    }
                }
            }
            2 -> {
                for (i in 0 until frames) {
                    val l = input.float
                    val r = input.float
                    val outL: Float
                    val outR: Float
                    when (earMode) {
                        EarMode.LEFT_ONLY -> {
                            val mono = (l + r) * 0.5f
                            outL = mono
                            outR = 0f
                        }
                        EarMode.RIGHT_ONLY -> {
                            val mono = (l + r) * 0.5f
                            outL = 0f
                            outR = mono
                        }
                        EarMode.STEREO -> {
                            outL = l
                            outR = r
                        }
                    }
                    out.putFloat(outL)
                    out.putFloat(outR)

                    if (isDebug) {
                        accumulateRms(outL.toDouble(), outR.toDouble())
                    }
                }
            }
            else -> {
                for (i in 0 until frames) {
                    var sum = 0f
                    for (ch in 0 until inputChannels) {
                        sum += input.float
                    }
                    val mono = (sum / inputChannels).coerceIn(-1f, 1f)
                    val outL: Float
                    val outR: Float
                    when (earMode) {
                        EarMode.LEFT_ONLY  -> { outL = mono; outR = 0f }
                        EarMode.RIGHT_ONLY -> { outL = 0f; outR = mono }
                        EarMode.STEREO     -> { outL = mono; outR = mono }
                    }
                    out.putFloat(outL)
                    out.putFloat(outR)

                    if (isDebug) {
                        accumulateRms(outL.toDouble(), outR.toDouble())
                    }
                }
            }
        }
        out.flip()

        if (isDebug) {
            maybeLogRms("PCM_FLOAT", earMode)
        }
    }

    private fun accumulateRms(normL: Double, normR: Double) {
        sumSqLeft += normL * normL
        sumSqRight += normR * normR
        sampleCount++
    }

    private fun maybeLogRms(encName: String, mode: EarMode) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRmsLogTime >= 1000L && sampleCount > 0L) {
            val lRms = kotlin.math.sqrt(sumSqLeft / sampleCount)
            val rRms = kotlin.math.sqrt(sumSqRight / sampleCount)
            Timber.tag(TAG).d(
                "RMS_MEASURE[%s]: mode=%s, encoding=%s, L_RMS=%.6f, R_RMS=%.6f (frames=%d)",
                assignedSide, mode, encName, lRms, rRms, sampleCount
            )
            sumSqLeft = 0.0
            sumSqRight = 0.0
            sampleCount = 0L
            lastRmsLogTime = now
        }
    }

    companion object {
        private const val TAG = "ChannelSplitProcessor"
    }
}
