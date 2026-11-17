/*
 * Ozone Player (2026)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated Ozone Logo:
 * An elegant circle "O" ring with a small illuminated dot orbiting around its perimeter
 * continuously with smooth, looping Compose Canvas graphics.
 */
@Composable
fun OzoneOrbitLogo(
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.onSurface,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp,
    dotRadius: Dp = 2.5.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OzoneOrbitInfiniteTransition")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "OzoneOrbitAngle",
    )

    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val dotRadiusPx = with(density) { dotRadius.toPx() }

    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        // Keep orbiting dot inside bounds
        val ringRadius = (side - strokeWidthPx - dotRadiusPx * 2f) / 2f

        if (ringRadius > 0f) {
            // 1. Draw the "O" circle ring
            drawCircle(
                color = ringColor,
                radius = ringRadius,
                center = center,
                style = Stroke(width = strokeWidthPx),
            )

            // 2. Compute orbit position for the small dot
            val angleRad = Math.toRadians(orbitAngle.toDouble())
            val dotX = (center.x + ringRadius * cos(angleRad)).toFloat()
            val dotY = (center.y + ringRadius * sin(angleRad)).toFloat()

            // 3. Draw the small orbiting dot
            drawCircle(
                color = dotColor,
                radius = dotRadiusPx,
                center = Offset(dotX, dotY),
            )
        }
    }
}

/**
 * Header logo for the top app bar:
 * Alternates in an infinite loop:
 * - Shows the music note icon for 10 seconds.
 * - Cross-fades into the animated Orbit "O" logo for 5 seconds.
 * - Repeats indefinitely when on the Home screen.
 */
@Composable
fun OzoneHeaderLogo(
    modifier: Modifier = Modifier,
    isHomeScreen: Boolean = true,
) {
    var isAnimatedState by remember { mutableStateOf(false) }

    LaunchedEffect(isHomeScreen) {
        if (!isHomeScreen) {
            isAnimatedState = false
            return@LaunchedEffect
        }
        while (true) {
            // Show music note icon for 10 seconds
            isAnimatedState = false
            delay(10_000L)
            // Show animated logo for 5 seconds
            isAnimatedState = true
            delay(5_000L)
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = isAnimatedState,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            label = "OzoneHeaderLogoCrossfade",
        ) { showAnimated ->
            if (showAnimated) {
                OzoneOrbitLogo(
                    modifier = Modifier.size(24.dp),
                    ringColor = MaterialTheme.colorScheme.onSurface,
                    dotColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.about_appbar),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
