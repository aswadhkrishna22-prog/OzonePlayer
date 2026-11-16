/*
 * Ozone Player (2026)
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun HeroRollDie(
    spinning: Boolean,
    animationsDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(spinning, animationsDisabled) {
        if (animationsDisabled) {
            rotation.snapTo(0f)
            return@LaunchedEffect
        }
        if (spinning) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(620, easing = LinearEasing))
            }
        } else {
            val rest = (rotation.value / 90f).roundToInt() * 90f
            if (rotation.value != rest) {
                rotation.animateTo(rest, spring(dampingRatio = 0.45f, stiffness = 300f))
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(20.dp)
            .graphicsLayer { rotationZ = rotation.value }
    ) {
        drawRoundRect(color = Color.White, cornerRadius = CornerRadius(4.5.dp.toPx()))
        val pip = 2.dp.toPx()
        val near = size.width * 0.28f
        val far = size.width * 0.72f
        val mid = size.width / 2f
        listOf(
            Offset(near, near), Offset(far, near),
            Offset(mid, mid),
            Offset(near, far), Offset(far, far),
        ).forEach { drawCircle(color = HeroCarouselTokens.GachaPipColor, radius = pip, center = it) }
    }
}
