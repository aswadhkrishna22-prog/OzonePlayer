/*
 * Ozone Player (2026)
 * https://github.com/aswadhkrishna22-prog/OzonePlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.SolidColor
import moe.rukamori.archivetune.ui.component.liquidGlass
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun rememberMiniHazeStyle(
    pureBlack: Boolean,
    blurRadius: Float,
    gradientColor: Int = 0,
): HazeBlurStyle {
    val defaultContainer = MaterialTheme.colorScheme.surfaceContainer
    val tintColor = remember(pureBlack, gradientColor, defaultContainer) {
        if (pureBlack) Color.Black
        else if (gradientColor != 0) Color(gradientColor)
        else defaultContainer
    }
    val fixedTintAlpha = if (pureBlack) SettingsDimensions.HazePureBlackTintAlpha else SettingsDimensions.HazeDefaultTintAlpha

    return remember(tintColor, blurRadius, pureBlack, defaultContainer) {
        val tintEffect = HazeColorEffect.tint(tintColor.copy(alpha = fixedTintAlpha))
        HazeBlurStyle {
            backgroundColor(if (pureBlack) Color.Black else defaultContainer)
            colorEffects(listOf(tintEffect))
            fallbackColorEffect(tintEffect)
            blurRadius(blurRadius.dp)
            noiseFactor(SettingsDimensions.HazeNoiseFactor)
        }
    }
}

@Composable
internal fun rememberBackgroundGradient(gradientColor: Int): Brush {
    val defaultContainer = MaterialTheme.colorScheme.surfaceContainer
    val color = remember(gradientColor, defaultContainer) {
        if (gradientColor != 0) Color(gradientColor) else defaultContainer
    }

    return remember(color) {
        Brush.verticalGradient(
            colors = listOf(color, color)
        )
    }
}

@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.playerSheetBackdrop(
    hazeState: HazeState?,
    hazeStyle: HazeBlurStyle,
    backgroundBrush: Brush,
    pureBlack: Boolean = false,
    expansionFractionProvider: () -> Float = { 0f },
    pillShape: Shape = RoundedCornerShape(32.dp),
): Modifier = this
    .liquidGlass(
        shape = pillShape,
        hazeState = hazeState,
        pureBlack = pureBlack,
        blurRadius = 26.dp,
        showBorder = true,
        cornerRadiusDp = 32.dp,
    )
    .drawWithContent {
        drawContent()
        val fraction = expansionFractionProvider()
        if (fraction > 0f) {
            drawRect(
                brush = if (pureBlack) SolidColor(Color.Black) else backgroundBrush,
                alpha = fraction.coerceIn(0f, 1f)
            )
        }
    }

internal fun Modifier.playerSheetGlassBorder(
    expansionFractionProvider: () -> Float,
    density: Density,
): Modifier = this

@Composable
internal fun rememberPlayerSheetPillShape(
    density: Density,
    expansionFraction: Animatable<Float, *>,
    isDocked: Boolean = true,
): Shape = remember(density) {
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(Rect.Zero)
            val fraction = expansionFraction.value
            if (fraction >= 0.99f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
            val radiusPx = with(density) {
                androidx.compose.ui.unit.lerp(32.dp, 0.dp, fraction.coerceIn(0f, 1f)).toPx()
            }
            val corner = CornerRadius(radiusPx, radiusPx)
            return Outline.Rounded(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    topLeftCornerRadius = corner,
                    topRightCornerRadius = corner,
                    bottomRightCornerRadius = corner,
                    bottomLeftCornerRadius = corner
                )
            )
        }
    }
}
