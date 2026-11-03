/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min
import kotlin.math.sin

object WavyProgressRenderer {
    private const val DEFAULT_WIDTH = 600
    private const val DEFAULT_HEIGHT = 40

    fun render(
        progress: Float,
        phase: Float,
        isPlaying: Boolean,
        widthPx: Int = DEFAULT_WIDTH,
        heightPx: Int = DEFAULT_HEIGHT,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerY = heightPx / 2f
        val clampedProgress = progress.coerceIn(0f, 1f)
        val playedW = (clampedProgress * widthPx).coerceIn(0f, widthPx.toFloat())

        // 1. Unplayed straight line (thin 2.5px, #55FFFFFF)
        val unplayedPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = 2.5f
                color = 0x55FFFFFF
            }

        if (playedW < widthPx) {
            canvas.drawLine(playedW, centerY, widthPx.toFloat(), centerY, unplayedPaint)
        }

        // 2. Played portion (sine wave or straight line with rounded caps)
        if (playedW > 0.5f) {
            val playedPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = 3.5f
                    color = Color.WHITE
                }

            if (!isPlaying || clampedProgress < 0.02f) {
                // Paused or start of track: straight crisp line
                canvas.drawLine(0f, centerY, playedW, centerY, playedPaint)
            } else {
                // Playing: traveling sine wave with graceful edge tapering
                val path = Path()
                path.moveTo(0f, centerY)

                val amplitude = heightPx * 0.22f
                val waveLength = 48f
                val frequency = (2f * Math.PI / waveLength).toFloat()
                val taperDistance = 24f

                val step = 3f
                var prevX = 0f
                var prevY = centerY
                var currentX = 0f

                while (currentX < playedW) {
                    currentX += step
                    if (currentX > playedW) currentX = playedW

                    val startTaper = (currentX / taperDistance).coerceIn(0f, 1f)
                    val endTaper = ((playedW - currentX) / taperDistance).coerceIn(0f, 1f)
                    val envelope = min(startTaper, endTaper)

                    val angle = currentX * frequency + phase
                    val y = centerY + (amplitude * envelope * sin(angle.toDouble()).toFloat())

                    val midX = (prevX + currentX) / 2f
                    val midY = (prevY + y) / 2f
                    path.quadTo(prevX, prevY, midX, midY)

                    prevX = currentX
                    prevY = y
                }
                path.lineTo(playedW, prevY)
                canvas.drawPath(path, playedPaint)
            }

            // 3. Playhead thumb dot (radius 3.5px, white)
            val thumbPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.WHITE
                }
            canvas.drawCircle(playedW, centerY, 3.5f, thumbPaint)
        }

        return bitmap
    }
}
