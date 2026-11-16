/*
 * Ozone Player (2026)
 * Halo Visualizer Player Skin with Harmonic Radial Bars
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.player.player_0.skins

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HaloSkin(
    coverPainter: Painter?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val clockSeconds by rememberSkinAnimationClock(isPlaying = isPlaying)
    val accent = MaterialTheme.colorScheme.primary
    val softRing = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        // Orbiting radial visualizer halo bars
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = clockSeconds
            val r = size.minDimension / 2f
            val innerRadius = r * 0.72f
            val numBars = 72

            repeat(numBars) { i ->
                val angle = (i.toFloat() / numBars.toFloat()) * 2f * PI.toFloat() - (PI.toFloat() / 2f)
                val level = haloLevel(i, t, isPlaying)
                val from = innerRadius + r * 0.03f
                val to = from + r * (0.04f + 0.20f * level)

                val cosA = cos(angle)
                val sinA = sin(angle)

                // Background track
                drawLine(
                    color = softRing,
                    start = Offset(center.x + cosA * from, center.y + sinA * from),
                    end = Offset(center.x + cosA * (from + r * 0.24f), center.y + sinA * (from + r * 0.24f)),
                    strokeWidth = r * 0.022f,
                    cap = StrokeCap.Round,
                )

                // Animated active bar
                drawLine(
                    color = accent.copy(alpha = 0.55f + 0.45f * level),
                    start = Offset(center.x + cosA * from, center.y + sinA * from),
                    end = Offset(center.x + cosA * to, center.y + sinA * to),
                    strokeWidth = r * 0.022f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Circular breathing album artwork
        val breatheScale = if (isPlaying) 1f + 0.015f * sin(clockSeconds * 3.2f) else 1f

        Box(
            modifier = Modifier
                .fillMaxSize(0.68f)
                .graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                    shadowElevation = 18.dp.toPx()
                    shape = CircleShape
                    clip = true
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (coverPainter != null) {
                Image(
                    painter = coverPainter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * Calculates height of radial bar [i] at timestamp [t] using harmonic interference waves.
 */
fun haloLevel(i: Int, t: Float, playing: Boolean): Float {
    if (!playing) return 0.08f
    val x = i * 0.35f
    val v = 0.5f +
        0.25f * sin(x + t * 2.1f) +
        0.15f * sin(x * 2.3f - t * 3.7f) +
        0.10f * sin(x * 5.1f + t * 6.3f)
    return v.coerceIn(0f, 1f)
}
