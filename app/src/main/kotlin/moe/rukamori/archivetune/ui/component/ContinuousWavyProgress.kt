/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ContinuousWavyProgress(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.33f),
    waveHeight: Dp = 28.dp,
    strokeWidth: Dp = 3.dp,
    waveLength: Dp = 48.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavyProgressTransition")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "WavePhase",
    )

    val currentPhase = if (isPlaying) animatedPhase else 0f
    val path = remember { Path() }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(waveHeight),
    ) {
        val w = size.width
        val centerY = size.height / 2f
        val strokePx = strokeWidth.toPx()
        val clampedProgress = progress.coerceIn(0f, 1f)
        val playedW = (clampedProgress * w).coerceIn(0f, w)

        // 1. Unplayed straight track
        if (playedW < w) {
            drawLine(
                color = trackColor,
                start = Offset(playedW, centerY),
                end = Offset(w, centerY),
                strokeWidth = strokePx * 0.8f,
                cap = StrokeCap.Round,
            )
        }

        // 2. Played wavy line
        if (playedW > 0.5f) {
            if (!isPlaying || clampedProgress < 0.02f) {
                drawLine(
                    color = waveColor,
                    start = Offset(0f, centerY),
                    end = Offset(playedW, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            } else {
                path.reset()
                path.moveTo(0f, centerY)

                val waveLengthPx = waveLength.toPx().coerceAtLeast(10f)
                val frequency = (2f * Math.PI / waveLengthPx).toFloat()
                val amplitude = size.height * 0.22f
                val taperDistance = (waveLengthPx * 0.5f).coerceAtLeast(10f)

                val stepPx = 4.dp.toPx().coerceAtLeast(6f)
                var prevX = 0f
                var prevY = centerY
                var currentX = 0f

                while (currentX < playedW) {
                    currentX += stepPx
                    if (currentX > playedW) currentX = playedW

                    val startTaper = (currentX / taperDistance).coerceIn(0f, 1f)
                    val endTaper = ((playedW - currentX) / taperDistance).coerceIn(0f, 1f)
                    val envelope = min(startTaper, endTaper)

                    val angle = currentX * frequency + currentPhase
                    val y = centerY + (amplitude * envelope * sin(angle.toDouble()).toFloat())

                    val midX = (prevX + currentX) / 2f
                    val midY = (prevY + y) / 2f
                    path.quadraticBezierTo(prevX, prevY, midX, midY)

                    prevX = currentX
                    prevY = y
                }
                path.lineTo(playedW, prevY)

                drawPath(
                    path = path,
                    color = waveColor,
                    style =
                        Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
            }

            // 3. Playhead thumb dot
            drawCircle(
                color = waveColor,
                radius = strokePx * 1.1f,
                center = Offset(playedW, centerY),
            )
        }
    }
}
