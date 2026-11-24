/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object HomeLayoutConfig {
    val shelfSpacing: Dp = 10.dp
    val shelfHorizontalPadding: Dp = 16.dp
    val sidePadding: Dp = 16.dp
    val sectionSpacing: Dp = 18.dp
    val headerHeight: Dp = 48.dp

    private var homeEntrancePlayedSession = false

    fun markHomeEntrancePlayed() {
        homeEntrancePlayedSession = true
    }

    fun isHomeEntrancePlayed(): Boolean = homeEntrancePlayedSession

    fun cardWidth(availableWidth: Dp): Dp {
        val usableWidth = availableWidth - (shelfHorizontalPadding * 2)
        return (usableWidth / 2.35f).coerceIn(132.dp, 160.dp)
    }

    fun artistSize(availableWidth: Dp): Dp =
        (cardWidth(availableWidth) * 0.90f).coerceIn(122.dp, 146.dp)

    fun quickPicksHeroHeight(availableWidth: Dp): Dp = when {
        availableWidth >= 840.dp -> 370.dp
        availableWidth >= 600.dp -> 345.dp
        else -> 310.dp
    }

    fun heroCarouselHeight(
        availableWidth: Dp,
        screenHeight: Dp = Dp.Unspecified,
    ): Dp {
        val heroWidth = (availableWidth - (sidePadding * 2)).coerceAtLeast(240.dp)
        return when {
            availableWidth >= 840.dp -> 480.dp
            availableWidth >= 600.dp -> 420.dp
            else -> (heroWidth * 1.05f).coerceIn(320.dp, 440.dp)
        }
    }
}

@Composable
fun Modifier.homeEntranceAnimation(
    index: Int,
    enabled: Boolean,
): Modifier {
    if (!enabled || HomeLayoutConfig.isHomeEntrancePlayed()) return this
    val density = LocalDensity.current
    val offsetPx = remember(density) { with(density) { 16.dp.toPx() } }
    val animState = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val delayMs = (index * 45L).coerceAtMost(450L)
        delay(delayMs)
        animState.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 350,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    return this.graphicsLayer {
        val progress = animState.value
        alpha = progress
        translationY = (1f - progress) * offsetPx
    }
}
