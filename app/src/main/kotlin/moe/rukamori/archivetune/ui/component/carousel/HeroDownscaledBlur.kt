/*
 * Ozone Player (2026)
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

internal fun heroGlassDownscale(effectiveRadius: Float): Int = when {
    effectiveRadius >= 64f -> 4
    effectiveRadius >= 16f -> 2
    else -> 1
}

private class FullSize(var width: Int = 0, var height: Int = 0)

private class MaskedEffectCache {
    var radius = -1f
    var width = -1f
    var height = -1f
    var tileOrdinal = -1
    var mask: Brush? = null
    var effect: RenderEffect? = null
}

private fun Modifier.downscaledLayout(downscale: () -> Int, full: FullSize): Modifier = layout { measurable, constraints ->
    val k = downscale().coerceAtLeast(1)
    val fullWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val fullHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
    full.width = fullWidth
    full.height = fullHeight
    val placeable = measurable.measure(
        Constraints.fixed((fullWidth + k - 1) / k, (fullHeight + k - 1) / k)
    )
    layout(fullWidth, fullHeight) { placeable.place(0, 0) }
}

@Composable
internal fun HeroDownscaledBlur(
    radius: () -> Float,
    modifier: Modifier = Modifier,
    tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
    mask: Brush? = null,
    offscreen: (() -> Boolean)? = null,
    draw: DrawScope.(fullSize: Size) -> Unit,
) {
    val currentRadius by rememberUpdatedState(radius)
    val currentOffscreen by rememberUpdatedState(offscreen)
    val downscale by remember { derivedStateOf { heroGlassDownscale(currentRadius()) } }
    val full = remember { FullSize() }
    val maskCache = remember { MaskedEffectCache() }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .downscaledLayout({ downscale }, full)
                .graphicsLayer {
                    val k = downscale.toFloat()
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                    val r = currentRadius() / k
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && r > 0.5f) {
                        val quantized = HeroBlurEffectCache.quantize(r)
                        val maskBrush = mask
                        if (maskBrush is ShaderBrush) {
                            val cache = maskCache
                            if (cache.effect == null || cache.radius != quantized || cache.width != size.width ||
                                cache.height != size.height || cache.tileOrdinal != tileMode.ordinal || cache.mask != maskBrush
                            ) {
                                val baseBlur = android.graphics.RenderEffect.createBlurEffect(quantized, quantized, tileMode)
                                val shader = maskBrush.createShader(size)
                                cache.effect = android.graphics.RenderEffect.createBlendModeEffect(
                                    baseBlur,
                                    android.graphics.RenderEffect.createShaderEffect(shader),
                                    android.graphics.BlendMode.DST_IN,
                                ).asComposeRenderEffect()
                                cache.radius = quantized
                                cache.width = size.width
                                cache.height = size.height
                                cache.tileOrdinal = tileMode.ordinal
                                cache.mask = maskBrush
                            }
                            cache.effect
                        } else {
                            HeroBlurEffectCache.get(r, tileMode)
                        }
                    } else {
                        null
                    }
                    compositingStrategy = if (currentOffscreen?.invoke() == true) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    clip = true
                }
        ) {
            val k = downscale.toFloat()
            val fullSize = Size(full.width.toFloat(), full.height.toFloat())
            scale(1f / k, 1f / k, pivot = Offset.Zero) { draw(fullSize) }
        }
    }
}
