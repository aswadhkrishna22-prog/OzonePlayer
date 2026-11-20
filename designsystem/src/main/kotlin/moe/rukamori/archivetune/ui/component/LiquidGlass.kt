/*
 * Ozone Player (2026)
 * https://github.com/aswadhkrishna22-prog/OzonePlayer
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

// ── AGSL LIQUID GLASS LENS REFRACTION SHADER (API 33+) ─────────────────────────
// Physically distorts the blurred backdrop near capsule edges (Snell's law bevel)
private const val LIQUID_GLASS_SHADER_SRC = """
    uniform shader content;
    uniform float2 size;
    uniform float cornerRadius;
    uniform float distortion;
    uniform float edgeWidth;

    float sdRoundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + float2(r, r);
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 p) {
        if (size.x <= 0.0 || size.y <= 0.0) {
            return content.eval(p);
        }
        float2 halfSize = size * 0.5;
        float2 centerOffset = p - halfSize;
        float r = min(cornerRadius, min(halfSize.x, halfSize.y));
        float d = sdRoundedBox(centerOffset, halfSize, r);

        // Within the outer edge bevel zone (-edgeWidth < d <= 0)
        if (d < 0.0 && d > -edgeWidth) {
            float t = 1.0 + (d / edgeWidth);
            float lensProfile = sqrt(max(0.0, 1.0 - (1.0 - t) * (1.0 - t)));
            float distToCenter = length(centerOffset);
            float2 normal = distToCenter > 0.001 ? (centerOffset / distToCenter) : float2(0.0, 0.0);
            float2 refractedCoord = p - normal * (distortion * lensProfile);
            return content.eval(refractedCoord);
        }

        return content.eval(p);
    }
"""

/**
 * Unified iOS 26 style Liquid Glass Modifier.
 *
 * Shared between FloatingNavigationToolbar and MiniPlayer so both surfaces look identical.
 *
 * 1. Live blurred backdrop via Haze with low fill alpha.
 * 2. Light dark scrim for text/icon readability over bright artwork.
 * 3. On Android 13+ (API 33+), AGSL lens refraction / edge distortion.
 * 4. On Android 12 and below, blur + tint fallback (no refraction). Below API 31, translucent solid.
 * 5. Bright edge highlight (gradient stroke, brighter top-left, fading bottom-right).
 * 6. Subtle inner glow / rim light.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState?,
    pureBlack: Boolean = false,
    blurRadius: Dp = 26.dp,
    tintAlpha: Float = 0.12f,
    scrimAlpha: Float = 0.14f,
    showBorder: Boolean = true,
    cornerRadiusDp: Dp = 28.dp,
    lensDistortionPx: Float = 12f,
    lensEdgeWidthPx: Float = 18f,
): Modifier = composed {
    val density = LocalDensity.current
    val isShaderSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val runtimeShader = remember(isShaderSupported) {
        if (isShaderSupported) {
            try {
                RuntimeShader(LIQUID_GLASS_SHADER_SRC)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    val tintColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
    val effectiveTintAlpha = if (pureBlack) 0.35f else tintAlpha

    val hazeStyle = remember(tintColor, effectiveTintAlpha, pureBlack, blurRadius) {
        val tintEffect = HazeColorEffect.tint(tintColor.copy(alpha = effectiveTintAlpha))
        HazeBlurStyle {
            blurEnabled(true)
            backgroundColor(Color.Transparent)
            colorEffects(listOf(tintEffect))
            fallbackColorEffect(tintEffect)
            blurRadius(blurRadius)
            noiseFactor(0.04f)
        }
    }

    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val borderWidthPx = with(density) { 1.dp.toPx() }

    this
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = hazeStyle,
                    performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                )
            } else {
                Modifier.background(
                    if (pureBlack) Color.Black.copy(alpha = 0.80f)
                    else Color(0xFF16161A).copy(alpha = 0.72f),
                    shape = shape,
                )
            }
        )
        // Light dark scrim for text/icon contrast over bright artwork
        .background(
            Color.Black.copy(alpha = if (pureBlack) 0.30f else scrimAlpha),
            shape = shape,
        )
        // Lens refraction shader on Android 13+
        .then(
            if (runtimeShader != null) {
                Modifier.graphicsLayer {
                    val w = size.width
                    val h = size.height
                    if (w > 0f && h > 0f) {
                        try {
                            runtimeShader.setFloatUniform("size", w, h)
                            runtimeShader.setFloatUniform("cornerRadius", cornerRadiusPx)
                            runtimeShader.setFloatUniform("distortion", lensDistortionPx)
                            runtimeShader.setFloatUniform("edgeWidth", lensEdgeWidthPx)
                            renderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "content")
                                .asComposeRenderEffect()
                        } catch (_: Throwable) {
                            renderEffect = null
                        }
                    }
                }
            } else {
                Modifier
            }
        )
        // Subtle inner glow + bright edge highlight (gradient stroke)
        .then(
            if (showBorder) {
                Modifier.drawWithContent {
                    drawContent()

                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@drawWithContent

                    // 1. Subtle luminous inner glow at the top-left
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = h * 0.45f,
                        ),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )

                    // 2. Thin bright edge highlight: brighter at top-left, fading to bottom-right
                    val strokeHalf = borderWidthPx / 2f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.52f),
                                Color.White.copy(alpha = 0.32f),
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f),
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                        ),
                        topLeft = Offset(strokeHalf, strokeHalf),
                        size = Size(w - borderWidthPx, h - borderWidthPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            (cornerRadiusPx - strokeHalf).coerceAtLeast(0f),
                            (cornerRadiusPx - strokeHalf).coerceAtLeast(0f),
                        ),
                        style = Stroke(width = borderWidthPx),
                    )
                }
            } else {
                Modifier
            }
        )
}
