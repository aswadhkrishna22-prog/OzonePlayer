/*
 * Ozone Player (2026) | Spatial Audio Processor
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.playback.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@UnstableApi
class SpatialProcessor : BaseAudioProcessor() {

    companion object {
        val instance: SpatialProcessor by lazy { SpatialProcessor() }
    }

    // Live-tweakable from debug screen
    @Volatile var enabled = false
    @Volatile var crossfeed = 0.3f      // 0..0.6
    @Volatile var delayMs = 0.25f       // 0.1..0.4
    @Volatile var bassDb = 0f           // -6..+6
    @Volatile var midDb = 0f            // -6..+6
    @Volatile var trebleDb = 0f         // -6..+6

    private var sr = 44100
    private var mix = 0f                 // smoothed bypass crossfade 0..1
    private var dl = FloatArray(64)
    private var dr = FloatArray(64)
    private var wp = 0
    private var lpL = 0f
    private var lpR = 0f
    private val eq = Array(2) { Array(3) { Biquad() } }

    private var lastBassDb = Float.NaN
    private var lastMidDb = Float.NaN
    private var lastTrebleDb = Float.NaN
    private var lastSr = -1

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioFormat.NOT_SET   // processor skipped for other formats
        }
        sr = inputAudioFormat.sampleRate
        dl.fill(0f)
        dr.fill(0f)
        wp = 0
        lpL = 0f
        lpR = 0f
        lastSr = -1
        return inputAudioFormat
    }

    override fun onFlush() {
        dl.fill(0f)
        dr.fill(0f)
        wp = 0
        lpL = 0f
        lpR = 0f
        eq.forEach { ch -> ch.forEach { it.reset() } }
    }

    override fun onReset() {
        onFlush()
        mix = 0f
    }

    override fun queueInput(input: ByteBuffer) {
        val n = input.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        updateEq()
        val d = (delayMs * sr / 1000f).toInt().coerceIn(1, 63)
        val a = exp(-2.0 * PI * 700.0 / sr).toFloat()   // 700Hz lowpass for crossfeed
        val target = if (enabled) 1f else 0f
        val g = crossfeed

        while (input.remaining() >= 4) {
            val l = input.short / 32768f
            val r = input.short / 32768f
            dl[wp] = l
            dr[wp] = r
            val rp = (wp - d + 64) % 64
            lpL = a * lpL + (1f - a) * dr[rp]     // R -> L
            lpR = a * lpR + (1f - a) * dl[rp]     // L -> R
            wp = (wp + 1) % 64

            var ol = (l + g * lpL) / (1f + g * 0.5f)
            var or = (r + g * lpR) / (1f + g * 0.5f)
            for (b in 0..2) {
                ol = eq[0][b].process(ol)
                or = eq[1][b].process(or)
            }

            mix += (target - mix) * 0.002f       // smooth toggle, no click
            val fl = l + (ol - l) * mix
            val fr = r + (or - r) * mix
            out.putShort((fl.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            out.putShort((fr.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        out.flip()
    }

    private fun updateEq() {
        val b = bassDb
        val m = midDb
        val t = trebleDb
        val s = sr
        if (b == lastBassDb && m == lastMidDb && t == lastTrebleDb && s == lastSr) return
        lastBassDb = b
        lastMidDb = m
        lastTrebleDb = t
        lastSr = s
        for (c in 0..1) {
            eq[c][0].lowShelf(s, 120f, b)
            eq[c][1].peaking(s, 1500f, 0.8f, m)
            eq[c][2].highShelf(s, 6000f, t)
        }
    }

    class Biquad {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var z1 = 0f
        private var z2 = 0f

        fun reset() {
            z1 = 0f
            z2 = 0f
        }

        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
            b0 = (nb0 / a0).toFloat()
            b1 = (nb1 / a0).toFloat()
            b2 = (nb2 / a0).toFloat()
            a1 = (na1 / a0).toFloat()
            a2 = (na2 / a0).toFloat()
        }

        fun peaking(sr: Int, f: Float, q: Float, db: Float) {
            val A = 10.0.pow(db / 40.0)
            val w = 2.0 * PI * f / sr
            val al = sin(w) / (2.0 * q)
            val c = cos(w)
            set(1.0 + al * A, -2.0 * c, 1.0 - al * A, 1.0 + al / A, -2.0 * c, 1.0 - al / A)
        }

        fun lowShelf(sr: Int, f: Float, db: Float) {
            val A = 10.0.pow(db / 40.0)
            val w = 2.0 * PI * f / sr
            val c = cos(w)
            val s = sqrt(2.0 * A) * sin(w)
            set(
                A * ((A + 1.0) - (A - 1.0) * c + s),
                2.0 * A * ((A - 1.0) - (A + 1.0) * c),
                A * ((A + 1.0) - (A - 1.0) * c - s),
                (A + 1.0) + (A - 1.0) * c + s,
                -2.0 * ((A - 1.0) + (A + 1.0) * c),
                (A + 1.0) + (A - 1.0) * c - s,
            )
        }

        fun highShelf(sr: Int, f: Float, db: Float) {
            val A = 10.0.pow(db / 40.0)
            val w = 2.0 * PI * f / sr
            val c = cos(w)
            val s = sqrt(2.0 * A) * sin(w)
            set(
                A * ((A + 1.0) + (A - 1.0) * c + s),
                -2.0 * A * ((A - 1.0) + (A + 1.0) * c),
                A * ((A + 1.0) + (A - 1.0) * c - s),
                (A + 1.0) - (A - 1.0) * c + s,
                2.0 * ((A - 1.0) - (A + 1.0) * c),
                (A + 1.0) - (A - 1.0) * c - s,
            )
        }
    }
}
