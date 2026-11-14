/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.core.common.math.lerp
import moe.rukamori.archivetune.core.common.math.lerp3

/**
 * Scroll-linked collapsing playlist cover.
 *
 * Follows the exact collapse curves, scroll signal (420dp), scale (1f -> 0.44f -> 0f),
 * corner-radius morph to circle (6.67% -> 32% -> 50%), blur (>0.70f), and fade
 * copied from the artist header [YumaMorphingHeader].
 *
 * Preserves the cover's layout footprint so underlying content (title, chips, play buttons, list)
 * maintains its natural layout and scroll behavior. All per-frame transformations run strictly
 * within [Modifier.graphicsLayer] (draw phase), avoiding per-scroll recompositions.
 */
@Composable
fun CollapsingPlaylistCover(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    cardSize: Dp = 240.dp,
    spotColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    maxScrollDistance: Dp = 420.dp,
    editIcon: (@Composable BoxScope.(fractionProvider: () -> Float, animationsDisabled: Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val localAnimationsDisabled = LocalAnimationsDisabled.current
    val systemAnimationsDisabled = remember(context) {
        runCatching {
            !android.animation.ValueAnimator.areAnimatorsEnabled() ||
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val animationsDisabled = localAnimationsDisabled || systemAnimationsDisabled

    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    val targetCameraY = if (statusBarHeightPx > 0f) statusBarHeightPx / 2f else with(density) { 24.dp.toPx() }
    val initialTopPx = statusBarHeightPx + with(density) { (AppBarHeight + 8.dp).toPx() }
    val initialCenterY = initialTopPx + with(density) { (cardSize / 2f).toPx() }
    val targetDeltaY = targetCameraY - initialCenterY

    val maxScrollPx = with(density) { maxScrollDistance.toPx() }

    val fractionProvider = remember(lazyListState, maxScrollPx) {
        {
            if (maxScrollPx <= 0f) {
                1f
            } else if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / maxScrollPx).coerceIn(0f, 1f)
            }
        }
    }

    val scrollOffsetProvider = remember(lazyListState, maxScrollPx) {
        {
            if (lazyListState.firstVisibleItemIndex > 0) {
                maxScrollPx
            } else {
                lazyListState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(maxScrollPx)
            }
        }
    }

    Box(
        modifier = modifier
            .padding(top = 8.dp, bottom = 20.dp)
            .size(cardSize),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)

                    if (animationsDisabled) {
                        scaleX = 1f
                        scaleY = 1f
                        translationY = 0f
                        alpha = 1f
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                        shadowElevation = 24.dp.toPx()
                        spotShadowColor = spotColor
                        ambientShadowColor = spotColor
                        return@graphicsLayer
                    }

                    val fraction = fractionProvider()
                    val scrollOffset = scrollOffsetProvider()

                    val currentScale = lerp3(start = 1f, mid = 0.44f, end = 0f, fraction = fraction)
                    scaleX = currentScale
                    scaleY = currentScale

                    val viewportOffset = lerp3(start = 0f, mid = 0f, end = targetDeltaY, fraction = fraction)
                    translationY = scrollOffset + viewportOffset

                    val cornerPercent = lerp3(start = 6.67f, mid = 32f, end = 50f, fraction = fraction).toInt()
                    shape = RoundedCornerShape(percent = cornerPercent)
                    clip = true

                    alpha = if (fraction < 0.70f) 1f else lerp(1f, 0f, (fraction - 0.70f) / 0.30f)

                    val currentElevation = if (fraction > 0.8f) 0f else lerp(24f, 8f, fraction)
                    shadowElevation = currentElevation.dp.toPx()
                    spotShadowColor = spotColor
                    ambientShadowColor = spotColor

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (fraction > 0.70f) {
                            val blurProgress = (fraction - 0.70f) / 0.30f
                            val blurRadius = lerp(0.1f, 40f, blurProgress)
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    } else {
                        renderEffect = null
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            content()
            editIcon?.invoke(this, fractionProvider, animationsDisabled)
        }
    }
}
