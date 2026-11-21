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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
// with domed depth curvature and boosted vibrancy/saturation.
private const val LIQUID_GLASS_SHADER_SRC = """
    uniform shader content;
    uniform float2 size;
    uniform float cornerRadius;
    uniform float distortion;
    uniform float edgeWidth;
    uniform float saturation;

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

        if (d <= 0.0) {
            float distToCenter = length(centerOffset);
            float2 normal = distToCenter > 0.001 ? (centerOffset / distToCenter) : float2(0.0, 0.0);

            // Outer edge bevel refraction (height = edgeWidth, amount = distortion)
            float edgeFactor = 0.0;
            if (d > -edgeWidth) {
                float t = 1.0 + (d / edgeWidth);
                edgeFactor = sqrt(max(0.0, 1.0 - (1.0 - t) * (1.0 - t)));
            }

            // Domed depth effect across the capsule
            float maxR = max(1.0, min(halfSize.x, halfSize.y));
            float normDist = clamp(distToCenter / maxR, 0.0, 1.0);
            float domeFactor = 0.12 * (1.0 - normDist * normDist);

            float totalDisplacement = distortion * (edgeFactor + domeFactor);
            float2 refractedCoord = p - normal * totalDisplacement;

            half4 c = content.eval(refractedCoord);

            // Boosted vibrancy/saturation so colours underneath glow through
            float luma = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
            c.rgb = mix(half3(luma), c.rgb, saturation);

            return c;
        }

        return content.eval(p);
    }
"""

/**
 * Specular meniscus illumination ported from LastWave-Native (liquidBubbleHighlight).
 * Gives the capsule convex rim reflections (bright top-left fading to bottom-right),
 * subtle top sheen, and layered optical depth.
 */
fun Modifier.liquidBubbleHighlight(
    cornerRadiusDp: Dp,
    isDark: Boolean = true,
): Modifier = drawWithContent {
    drawContent()

    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawWithContent

    val radius = cornerRadiusDp.toPx().coerceAtMost(minOf(w, h) / 2f)
    val silhouette = Path().apply {
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius, radius)))
    }

    val rimWidth = 1.dp.toPx().coerceAtMost(radius)
    val rimInset = rimWidth / 2f
    val rim = Path().apply {
        addRoundRect(
            RoundRect(
                rimInset,
                rimInset,
                w - rimInset,
                h - rimInset,
                CornerRadius((radius - rimInset).coerceAtLeast(0f), (radius - rimInset).coerceAtLeast(0f)),
            )
        )
    }

    val rimStroke = Stroke(rimWidth)
    val oppositeStroke = Stroke(0.5.dp.toPx().coerceAtMost(radius))
    val broadShade = Stroke(6.dp.toPx().coerceAtMost(radius))
    val middleShade = Stroke(3.5.dp.toPx().coerceAtMost(radius))
    val narrowShade = Stroke(1.5.dp.toPx().coerceAtMost(radius))

    // Light comes from above-left (iOS 26 visible specular rim highlight)
    val upperReflection = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (isDark) 0.75f else 0.85f),
        0.24f to Color.White.copy(alpha = 0.35f),
        0.52f to Color.Transparent,
        1f to Color.Transparent,
        start = Offset.Zero,
        end = Offset(w * 0.78f, h),
    )
    val oppositeReflection = Brush.linearGradient(
        0f to Color.Transparent,
        0.62f to Color.Transparent,
        0.84f to Color.White.copy(alpha = 0.08f),
        1f to Color.White.copy(alpha = if (isDark) 0.28f else 0.35f),
        start = Offset.Zero,
        end = Offset(w, h),
    )
    val lowerShade = Brush.verticalGradient(
        0f to Color.Transparent,
        0.50f to Color.Transparent,
        0.74f to Color.Black.copy(alpha = 0.025f),
        1f to Color.Black.copy(alpha = if (isDark) 0.12f else 0.08f),
        endY = h,
    )
    val surfaceReflection = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (isDark) 0.07f else 0.06f),
        0.38f to Color.White.copy(alpha = 0.012f),
        0.64f to Color.Transparent,
        1f to Color.White.copy(alpha = 0.018f),
        endY = h,
    )

    clipPath(silhouette) {
        drawRect(surfaceReflection)
        // Inset shaded meniscus bevel
        drawPath(silhouette, lowerShade, alpha = 0.40f, style = broadShade)
        drawPath(silhouette, lowerShade, alpha = 0.50f, style = middleShade)
        drawPath(silhouette, lowerShade, alpha = 0.65f, style = narrowShade)
        drawPath(rim, upperReflection, style = rimStroke)
        drawPath(rim, oppositeReflection, style = oppositeStroke)
    }
}

/**
 * Unified iOS 26 Style Clear Liquid Glass Modifier.
 *
 * Shared between FloatingNavigationToolbar and MiniPlayer so both surfaces look identical.
 *
 * 1. 8dp ambient + spot elevation shadow under the pill.
 * 2. Low blur (2-6dp, default 4dp) so underlying content/artwork stays sharp and recognizable.
 * 3. Light translucent glass tint (white ~0.08f in dark theme, zero dark scrim/slab).
 * 4. On Android 13+ (API 33+), AGSL lens refraction (18dp height, 28dp distortion, domed depth,
 *    chromatic aberration off, 1.25x boosted vibrancy/saturation).
 * 5. On Android 12 and below, blur + light tint fallback (no refraction). Below API 31, translucent solid.
 * 6. Visible 1dp rim highlight (liquidBubbleHighlight, bright top-left fading to bottom-right).
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState?,
    pureBlack: Boolean = false,
    blurRadius: Dp = 4.dp,
    tintAlpha: Float = 0.08f,
    showBorder: Boolean = true,
    cornerRadiusDp: Dp = 28.dp,
    lensDistortion: Dp = 28.dp,
    lensHeight: Dp = 18.dp,
    saturation: Float = 1.25f,
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

    // Light translucent liquid glass tint (NO black scrim, clear glass look)
    val glassTint = Color.White.copy(alpha = tintAlpha)

    val hazeStyle = remember(glassTint, blurRadius) {
        val tintEffect = HazeColorEffect.tint(glassTint)
        HazeBlurStyle {
            blurEnabled(true)
            backgroundColor(Color.Transparent)
            colorEffects(listOf(tintEffect))
            fallbackColorEffect(tintEffect)
            blurRadius(blurRadius)
            noiseFactor(0.01f)
        }
    }

    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val lensDistortionPx = with(density) { lensDistortion.toPx() }
    val lensHeightPx = with(density) { lensHeight.toPx() }

    this
        // 1. Soft elevation drop shadow under the pill
        .shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.10f),
            spotColor = Color.Black.copy(alpha = 0.14f),
        )
        // 2. Clip to capsule shape
        .clip(shape)
        // 3. Live low-blur backdrop with light translucent tint (2-6dp, default 4dp)
        .then(
            if (hazeState != null) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = hazeStyle,
                    performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                )
            } else {
                Modifier.background(
                    Color.White.copy(alpha = 0.12f),
                    shape = shape,
                )
            }
        )
        // 4. AGSL lens refraction shader (API 33+) with domed depth effect & boosted vibrancy
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
                            runtimeShader.setFloatUniform("edgeWidth", lensHeightPx)
                            runtimeShader.setFloatUniform("saturation", saturation)
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
        // 5. Specular rim highlight (1dp, bright top-left fading bottom-right)
        .then(
            if (showBorder) {
                Modifier.liquidBubbleHighlight(
                    cornerRadiusDp = cornerRadiusDp,
                    isDark = true,
                )
            } else {
                Modifier
            }
        )
}
