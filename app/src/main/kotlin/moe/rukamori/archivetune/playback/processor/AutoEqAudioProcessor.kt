/*
 * Ozone Player (2026)
 * AutoEq Headphone Acoustic Correction Audio Processor
 * Transposed Direct Form II Cascade with Dynamic Headroom & Soft Limiter
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import moe.rukamori.archivetune.playback.autoeq.AutoEqFilterType
import moe.rukamori.archivetune.playback.autoeq.AutoEqProfile
import moe.rukamori.archivetune.playback.autoeq.BiquadFilter
import moe.rukamori.archivetune.playback.dsp.Dither16
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

@UnstableApi
class AutoEqAudioProcessor : BaseAudioProcessor() {

    companion object {
        val instance: AutoEqAudioProcessor by lazy { AutoEqAudioProcessor() }
    }

    @Volatile
    var enabled: Boolean = false
        set(value) {
            field = value
            needsFilterRebuild = true
        }

    @Volatile
    var profile: AutoEqProfile? = null
        set(value) {
            field = value
            needsFilterRebuild = true
        }

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var encoding: Int = C.ENCODING_PCM_16BIT
    private var dither: Dither16 = Dither16(2)

    @Volatile
    private var needsFilterRebuild: Boolean = true

    // Array of biquad filter cascades: [channelIndex][filterIndex]
    private var channelFilters: Array<List<BiquadFilter>> = emptyArray()
    private var linearPreampGain: Double = 1.0

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
        dither = Dither16(ch)
        needsFilterRebuild = true

        rebuildFilters()

        return inputAudioFormat
    }

    override fun onFlush() {
        dither.reset()
        channelFilters.forEach { cascade ->
            cascade.forEach { it.reset() }
        }
    }

    override fun onReset() {
        onFlush()
        channelFilters = emptyArray()
        needsFilterRebuild = true
    }

    private fun rebuildFilters() {
        val curProfile = profile
        if (!enabled || curProfile == null || curProfile.filters.isEmpty()) {
            channelFilters = emptyArray()
            linearPreampGain = 1.0
            needsFilterRebuild = false
            return
        }

        val sr = sampleRate.toDouble()
        val nyquistLimit = sr * 0.45
        val validFilters = curProfile.filters.filter {
            it.freqHz > 10f && it.freqHz < nyquistLimit
        }

        if (validFilters.isEmpty()) {
            channelFilters = emptyArray()
            linearPreampGain = 1.0
            needsFilterRebuild = false
            return
        }

        // Automatic headroom calculation:
        // Any positive boost in the parametric curve must be offset so inter-sample peaks don't clip.
        val maxFilterBoost = validFilters.maxOfOrNull { it.gainDb }?.coerceAtLeast(0f) ?: 0f
        val netPreampDb = (curProfile.preampDb - maxFilterBoost).coerceAtMost(0f)
        linearPreampGain = 10.0.pow(netPreampDb / 20.0)

        channelFilters = Array(channelCount) {
            validFilters.map { f ->
                when (f.type) {
                    AutoEqFilterType.PEAK -> BiquadFilter.peaking(
                        sampleRate = sr,
                        freqHz = f.freqHz.toDouble(),
                        q = f.q.toDouble(),
                        gainDb = f.gainDb.toDouble(),
                    )
                    AutoEqFilterType.LOW_SHELF -> BiquadFilter.lowShelf(
                        sampleRate = sr,
                        freqHz = f.freqHz.toDouble(),
                        gainDb = f.gainDb.toDouble(),
                        q = f.q.toDouble(),
                    )
                    AutoEqFilterType.HIGH_SHELF -> BiquadFilter.highShelf(
                        sampleRate = sr,
                        freqHz = f.freqHz.toDouble(),
                        gainDb = f.gainDb.toDouble(),
                        q = f.q.toDouble(),
                    )
                }
            }
        }

        needsFilterRebuild = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (needsFilterRebuild) {
            rebuildFilters()
        }

        val filters = channelFilters
        val isBypassed = !enabled || filters.isEmpty()

        val outputBuffer = replaceOutputBuffer(remaining)

        if (isBypassed) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val gain = linearPreampGain
        val channels = channelCount

        if (encoding == C.ENCODING_PCM_FLOAT) {
            val floatCount = remaining / 4
            val frames = floatCount / channels
            var ch = 0

            for (i in 0 until floatCount) {
                if (ch == 0) dither.nextFrame()

                val sampleIn = inputBuffer.getFloat()
                var sampleOut = sampleIn.toDouble() * gain

                val cascade = filters.getOrNull(ch)
                if (cascade != null) {
                    for (k in cascade.indices) {
                        sampleOut = cascade[k].process(sampleOut)
                    }
                }

                // Hyperbolic tangent soft limiter for peaks > 1.0f
                if (sampleOut > 1.0 || sampleOut < -1.0) {
                    sampleOut = tanh(sampleOut)
                }

                outputBuffer.putFloat(sampleOut.toFloat())

                ch = if (ch + 1 >= channels) 0 else ch + 1
            }
        } else {
            // ENCODING_PCM_16BIT
            val shortCount = remaining / 2
            val frames = shortCount / channels
            var ch = 0

            for (i in 0 until shortCount) {
                if (ch == 0) dither.nextFrame()

                val sampleInShort = inputBuffer.getShort()
                val sampleIn = sampleInShort / 32768.0
                var sampleOut = sampleIn * gain

                val cascade = filters.getOrNull(ch)
                if (cascade != null) {
                    for (k in cascade.indices) {
                        sampleOut = cascade[k].process(sampleOut)
                    }
                }

                // Soft limiter for peaks exceeding headroom
                if (sampleOut > 1.0 || sampleOut < -1.0) {
                    sampleOut = tanh(sampleOut)
                }

                val encoded = dither.encode(sampleOut.toFloat(), ch)
                outputBuffer.putShort(encoded)

                ch = if (ch + 1 >= channels) 0 else ch + 1
            }
        }

        outputBuffer.flip()
    }
}
