/*
 * Ozone Player (2026) | Audio Enhancement Processor
 * High-performance DSP processor for low-quality and low-bitrate streams
 * Transposed Direct Form II Biquads, Dynamic Range Compression & Soft Limiter
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.playback.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import moe.rukamori.archivetune.constants.EnhanceStrength
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

@UnstableApi
class EnhanceAudioProcessor : BaseAudioProcessor() {

    companion object {
        @Volatile var globalEnabled: Boolean = false
        @Volatile var globalAutoMode: Boolean = false
        @Volatile var globalStrength: EnhanceStrength = EnhanceStrength.MEDIUM
        @Volatile var currentStreamBitrateKbps: Int = 0
        @Volatile var isLowDataModeActive: Boolean = false

        val instance: EnhanceAudioProcessor by lazy { EnhanceAudioProcessor() }

        val isEffectivelyActive: Boolean
            get() {
                if (!globalEnabled) return false
                if (!globalAutoMode) return true
                val isLowBitrate = currentStreamBitrateKbps in 1..159
                return isLowBitrate || isLowDataModeActive
            }
    }

    var enabled: Boolean
        get() = globalEnabled
        set(value) { globalEnabled = value }

    var autoMode: Boolean
        get() = globalAutoMode
        set(value) { globalAutoMode = value }

    var strength: EnhanceStrength
        get() = globalStrength
        set(value) { globalStrength = value }

    val isEffectivelyActive: Boolean
        get() = Companion.isEffectivelyActive

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var encoding: Int = C.ENCODING_PCM_16BIT
    private var wasActive: Boolean = false

    private var lastSampleRate: Double = -1.0
    private var lastStrength: EnhanceStrength? = null

    private var lowShelfFilters: Array<Biquad> = Array(2) { Biquad() }
    private var highShelfFilters: Array<Biquad> = Array(2) { Biquad() }
    private var frameSamples: DoubleArray = DoubleArray(2)

    private var envelope: Double = 0.0
    private var attackCoeff: Double = 0.0
    private var releaseCoeff: Double = 0.0
    private var makeupGainLin: Double = 1.0
    private var threshLin: Double = 0.2
    private var ratioInvMinus1: Double = 0.0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        val ch = inputAudioFormat.channelCount
        val sr = inputAudioFormat.sampleRate

        if ((enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) || ch < 1 || sr < 8000) {
            return AudioFormat.NOT_SET
        }

        sampleRate = sr
        channelCount = ch
        encoding = enc

        if (lowShelfFilters.size < ch) {
            lowShelfFilters = Array(ch) { Biquad() }
            highShelfFilters = Array(ch) { Biquad() }
        }
        if (frameSamples.size < ch) {
            frameSamples = DoubleArray(ch)
        }

        lastSampleRate = -1.0
        lastStrength = null
        resetDspState()
        updateParameters(force = true)

        return inputAudioFormat
    }

    override fun onFlush() {
        resetDspState()
    }

    override fun onReset() {
        onFlush()
        sampleRate = 44100
        channelCount = 2
        encoding = C.ENCODING_PCM_16BIT
        wasActive = false
        lastSampleRate = -1.0
        lastStrength = null
    }

    private fun resetDspState() {
        envelope = 0.0
        lowShelfFilters.forEach { it.reset() }
        highShelfFilters.forEach { it.reset() }
    }

    private fun updateParameters(force: Boolean = false) {
        val sr = sampleRate.toDouble()
        val currentStr = globalStrength
        if (!force && sr == lastSampleRate && currentStr == lastStrength) return

        lastSampleRate = sr
        lastStrength = currentStr

        val lowCutFreq = currentStr.lowBoostFreqHz
        val lowGain = currentStr.lowBoostGainDb
        val highCutFreq = currentStr.highCutFreqHz
        val highCutGain = currentStr.highCutGainDb

        for (ch in lowShelfFilters.indices) {
            lowShelfFilters[ch].setLowShelf(sr, lowCutFreq, lowGain)
            highShelfFilters[ch].setHighShelf(sr, highCutFreq, highCutGain)
        }

        // Dynamics coefficients precomputation
        attackCoeff = 1.0 - exp(-1.0 / (sr * 0.010)) // 10ms attack
        releaseCoeff = 1.0 - exp(-1.0 / (sr * 0.100)) // 100ms release
        makeupGainLin = 10.0.pow(currentStr.makeupGainDb / 20.0)
        threshLin = 10.0.pow(currentStr.thresholdDb / 20.0)
        ratioInvMinus1 = (1.0 / currentStr.ratio) - 1.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val active = isEffectivelyActive
        if (!active) {
            if (wasActive) {
                resetDspState()
                wasActive = false
            }
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.order(ByteOrder.nativeOrder())
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }
        wasActive = true

        updateParameters()

        val channels = channelCount
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val bytesPerFrame = channels * bytesPerSample
        val frames = remaining / bytesPerFrame
        if (frames == 0) return

        val outputBuffer = replaceOutputBuffer(frames * bytesPerFrame)
        outputBuffer.order(ByteOrder.nativeOrder())

        if (encoding == C.ENCODING_PCM_FLOAT) {
            for (f in 0 until frames) {
                for (ch in 0 until channels) {
                    frameSamples[ch] = inputBuffer.float.toDouble()
                }

                for (ch in 0 until channels) {
                    frameSamples[ch] = lowShelfFilters[ch].process(frameSamples[ch])
                    frameSamples[ch] = highShelfFilters[ch].process(frameSamples[ch])
                }

                var peak = 0.0
                for (ch in 0 until channels) {
                    val sAbs = abs(frameSamples[ch])
                    if (sAbs > peak) peak = sAbs
                }

                if (peak > envelope) {
                    envelope += attackCoeff * (peak - envelope)
                } else {
                    envelope += releaseCoeff * (peak - envelope)
                }

                val gr = if (envelope > threshLin) {
                    (envelope / threshLin).pow(ratioInvMinus1)
                } else {
                    1.0
                }
                val currentGain = makeupGainLin * gr

                for (ch in 0 until channels) {
                    var s = frameSamples[ch] * currentGain
                    if (s > 0.9) {
                        s = 0.9 + 0.1 * tanh((s - 0.9) / 0.1)
                    } else if (s < -0.9) {
                        s = -0.9 + 0.1 * tanh((s + 0.9) / 0.1)
                    }
                    outputBuffer.putFloat(s.coerceIn(-1.0, 1.0).toFloat())
                }
            }
        } else {
            // C.ENCODING_PCM_16BIT
            for (f in 0 until frames) {
                for (ch in 0 until channels) {
                    val sShort = inputBuffer.short
                    frameSamples[ch] = sShort / 32768.0
                }

                for (ch in 0 until channels) {
                    frameSamples[ch] = lowShelfFilters[ch].process(frameSamples[ch])
                    frameSamples[ch] = highShelfFilters[ch].process(frameSamples[ch])
                }

                var peak = 0.0
                for (ch in 0 until channels) {
                    val sAbs = abs(frameSamples[ch])
                    if (sAbs > peak) peak = sAbs
                }

                if (peak > envelope) {
                    envelope += attackCoeff * (peak - envelope)
                } else {
                    envelope += releaseCoeff * (peak - envelope)
                }

                val gr = if (envelope > threshLin) {
                    (envelope / threshLin).pow(ratioInvMinus1)
                } else {
                    1.0
                }
                val currentGain = makeupGainLin * gr

                for (ch in 0 until channels) {
                    var s = frameSamples[ch] * currentGain
                    if (s > 0.9) {
                        s = 0.9 + 0.1 * tanh((s - 0.9) / 0.1)
                    } else if (s < -0.9) {
                        s = -0.9 + 0.1 * tanh((s + 0.9) / 0.1)
                    }
                    val outShort = (s.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                    outputBuffer.putShort(outShort)
                }
            }
        }

        outputBuffer.flip()
    }

    class Biquad {
        private var b0: Double = 1.0
        private var b1: Double = 0.0
        private var b2: Double = 0.0
        private var a1: Double = 0.0
        private var a2: Double = 0.0
        private var z1: Double = 0.0
        private var z2: Double = 0.0

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        fun setLowShelf(sr: Double, freqHz: Double, gainDb: Double, q: Double = 0.7071) {
            val clampedFreq = freqHz.coerceIn(20.0, sr * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sr
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.05))
            val cw = cos(w0)
            val sa = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) + (a - 1.0) * cw + sa

            b0 = (a * ((a + 1.0) - (a - 1.0) * cw + sa)) / a0
            b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cw)) / a0
            b2 = (a * ((a + 1.0) - (a - 1.0) * cw - sa)) / a0
            a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cw)) / a0
            a2 = ((a + 1.0) + (a - 1.0) * cw - sa) / a0
        }

        fun setHighShelf(sr: Double, freqHz: Double, gainDb: Double, q: Double = 0.7071) {
            val clampedFreq = freqHz.coerceIn(20.0, sr * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sr
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.05))
            val cw = cos(w0)
            val sa = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) - (a - 1.0) * cw + sa

            b0 = (a * ((a + 1.0) + (a - 1.0) * cw + sa)) / a0
            b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cw)) / a0
            b2 = (a * ((a + 1.0) - (a - 1.0) * cw - sa)) / a0
            a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cw)) / a0
            a2 = ((a + 1.0) - (a - 1.0) * cw - sa) / a0
        }
    }
}
