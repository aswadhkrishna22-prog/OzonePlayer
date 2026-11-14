/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.viewmodels.OnlineSearchSource

/**
 * Slide-in animation constants for search result song rows.
 */
const val SEARCH_SLIDE_MAX_ANIMATED_ROWS = 8
const val SEARCH_SLIDE_ROW_STAGGER_MS = 45L
const val SEARCH_SLIDE_DURATION_MS = 450
const val SEARCH_SLIDE_ALPHA_DURATION_MS = 270 // 60% of duration (450ms * 0.6)
const val SEARCH_SLIDE_START_OFFSET_RATIO = 0.5f // 50% of screen width
val SearchSlidePositionEasing: Easing = FastOutSlowInEasing
val SearchSlideAlphaEasing: Easing = LinearEasing

/**
 * Animation triggers rule:
 * - Animate on source chip switch (YouTube Music <-> Spotify)
 * - Do NOT animate on category filter switch (All / Songs / Albums / Artists / Playlists)
 */
object SearchSlideTrigger {
    fun shouldTriggerOnSourceChange(
        oldSource: OnlineSearchSource,
        newSource: OnlineSearchSource,
    ): Boolean = oldSource != newSource

    fun shouldTriggerOnCategoryFilterChange(): Boolean = false
}

/**
 * Checks whether system animator duration scale is > 0 (animations enabled in developer options).
 */
fun isSystemAnimatorEnabled(context: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }
    } catch (_: Throwable) {
        true
    }
}

/**
 * Determines whether search results slide animation is active, considering:
 * - User setting (AnimateSearchResultsKey)
 * - LocalAnimationsDisabled
 * - System animator duration scale
 */
@Composable
fun rememberSearchSlideAnimationEnabled(userPreferenceEnabled: Boolean): Boolean {
    val localDisabled = LocalAnimationsDisabled.current
    val context = LocalContext.current
    return remember(userPreferenceEnabled, localDisabled) {
        if (!userPreferenceEnabled || localDisabled) {
            false
        } else {
            isSystemAnimatorEnabled(context)
        }
    }
}

/**
 * A wrapper for a search result song row that performs the slide-in animation.
 *
 * The row keeps its full layout space at all times (so nothing shifts),
 * and translationX/alpha are driven inside graphicsLayer { ... }, avoiding
 * recomposition per frame while ensuring taps work while moving.
 */
@Composable
fun SearchSlideRow(
    index: Int,
    animationKey: String,
    shouldAnimate: Boolean,
    modifier: Modifier = Modifier,
    onAnimationTriggered: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isRowAnimatable = index < SEARCH_SLIDE_MAX_ANIMATED_ROWS
    val isAnimating = remember(animationKey, index) { shouldAnimate && isRowAnimatable }

    if (!isAnimating) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val initialTranslationX = screenWidthPx * SEARCH_SLIDE_START_OFFSET_RATIO

    val translationAnim = remember(animationKey, index) { Animatable(initialTranslationX) }
    val alphaAnim = remember(animationKey, index) { Animatable(0f) }

    LaunchedEffect(animationKey, index) {
        onAnimationTriggered?.invoke()
        val delayMs = index * SEARCH_SLIDE_ROW_STAGGER_MS
        if (delayMs > 0) {
            delay(delayMs)
        }
        launch {
            translationAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SEARCH_SLIDE_DURATION_MS,
                    easing = SearchSlidePositionEasing,
                ),
            )
        }
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = SEARCH_SLIDE_ALPHA_DURATION_MS,
                    easing = SearchSlideAlphaEasing,
                ),
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationX = translationAnim.value
            alpha = alphaAnim.value
        },
    ) {
        content()
    }
}
