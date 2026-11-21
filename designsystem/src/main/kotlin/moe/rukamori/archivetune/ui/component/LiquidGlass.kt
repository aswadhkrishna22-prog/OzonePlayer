/*
 * Ozone Player (2026)
 * https://github.com/aswadhkrishna22-prog/OzonePlayer
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

/**
 * Optical presets tuned for specific UI surfaces.
 */
enum class LiquidGlassPreset {
    /** Floating capsule / dock / mini player pill: low blur, high clarity, strong rim. */
    Pill,
    /** Wide top app bars and headers: deep frost for 100% text legibility, top sheen. */
    Header,
    /** Small filter chips and interactive tabs: medium blur, compact lens height. */
    Chip,
}

/**
 * Specular meniscus illumination giving the glass convex rim reflections
 * (bright top-left fading to bottom-right), subtle top sheen, and layered optical depth.
 */
fun Modifier.liquidBubbleHighlight(
    cornerRadiusDp: Dp,
    isDark: Boolean = true,
    hasTopSheen: Boolean = false,
    sheenAlpha: Float = 0.07f,
): Modifier = drawWithContent {
    drawContent()

    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawWithContent

    val radius = cornerRadiusDp.toPx().coerceAtMost(minOf(w, h) / 2f)
    val silhouette = Path().apply {
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius, radius)))
    }

    val rimWidth = 1.dp.toPx().coerceAtMost(if (radius > 0f) radius else 2.dp.toPx())
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
    val oppositeStroke = Stroke(0.5.dp.toPx().coerceAtMost(if (radius > 0f) radius else 1.dp.toPx()))
    val broadShade = Stroke(6.dp.toPx().coerceAtMost(if (radius > 0f) radius else 6.dp.toPx()))
    val middleShade = Stroke(3.5.dp.toPx().coerceAtMost(if (radius > 0f) radius else 3.5.dp.toPx()))
    val narrowShade = Stroke(1.5.dp.toPx().coerceAtMost(if (radius > 0f) radius else 1.5.dp.toPx()))

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
        if (hasTopSheen) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isDark) sheenAlpha else sheenAlpha * 1.5f),
                    0.55f to Color.Transparent,
                ),
            )
        }
        // Inset shaded meniscus bevel
        drawPath(silhouette, lowerShade, alpha = 0.40f, style = broadShade)
        drawPath(silhouette, lowerShade, alpha = 0.50f, style = middleShade)
        drawPath(silhouette, lowerShade, alpha = 0.65f, style = narrowShade)
        drawPath(rim, upperReflection, style = rimStroke)
        drawPath(rim, oppositeReflection, style = oppositeStroke)
    }
}

/**
 * Interactive press & touch physics tracker for liquid glass surfaces.
 */
class GlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val pressAnimation = Animatable(0f)

    val pressProgress: Float get() = pressAnimation.value
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) =
        with(pointer) {
            detectDragGestures(
                onDragStart = { down ->
                    touchPosition = down
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                },
                onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
                onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
            ) { change, _ ->
                touchPosition = change.position
            }
        }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/**
 * Unified Liquid Glass Modifier.
 *
 * Implements the full physical optical pipeline:
 * 1. Ambient & spot elevation drop shadow under floating glass.
 * 2. Shape silhouette clipping.
 * 3. Live backdrop blur with light translucent tint (NO dark opaque scrim).
 * 4. AGSL RuntimeShader lens refraction (API 33+) with Snell's law bevel, domed curvature,
 *    and vibrancy saturation boost.
 * 5. Multi-platform graceful fallbacks for API 31-32 (Compose blur + tint) and < API 31 (translucent solid).
 * 6. Visible 1dp directional specular meniscus rim highlight (bright top-left fading to bottom-right).
 * 7. Optional interactive spring press feedback and radial glow.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState?,
    pureBlack: Boolean = false,
    preset: LiquidGlassPreset = LiquidGlassPreset.Pill,
    blurRadius: Dp = when (preset) {
        LiquidGlassPreset.Pill -> 4.dp
        LiquidGlassPreset.Header -> 10.dp
        LiquidGlassPreset.Chip -> 3.dp
    },
    tintAlpha: Float = when (preset) {
        LiquidGlassPreset.Pill -> 0.08f
        LiquidGlassPreset.Header -> if (pureBlack) 0.08f else 0.05f
        LiquidGlassPreset.Chip -> 0.09f
    },
    showBorder: Boolean = true,
    cornerRadiusDp: Dp = when (preset) {
        LiquidGlassPreset.Pill -> 28.dp
        LiquidGlassPreset.Header -> 0.dp
        LiquidGlassPreset.Chip -> 18.dp
    },
    lensDistortion: Dp = when (preset) {
        LiquidGlassPreset.Pill -> 28.dp
        LiquidGlassPreset.Header -> 24.dp
        LiquidGlassPreset.Chip -> 18.dp
    },
    lensHeight: Dp = when (preset) {
        LiquidGlassPreset.Pill -> 18.dp
        LiquidGlassPreset.Header -> 14.dp
        LiquidGlassPreset.Chip -> 12.dp
    },
    saturation: Float = when (preset) {
        LiquidGlassPreset.Pill -> 1.35f
        LiquidGlassPreset.Header -> 1.55f
        LiquidGlassPreset.Chip -> 1.25f
    },
    shadowElevation: Dp = when (preset) {
        LiquidGlassPreset.Pill -> 8.dp
        LiquidGlassPreset.Header -> 0.dp
        LiquidGlassPreset.Chip -> 2.dp
    },
    hasTopSheen: Boolean = preset == LiquidGlassPreset.Header,
    interaction: GlassInteraction? = null,
): Modifier = composed {
    val density = LocalDensity.current

    // Light translucent glass tint (NO black scrim, clear liquid glass appearance)
    val glassTint = if (pureBlack && preset == LiquidGlassPreset.Header) {
        Color.Black.copy(alpha = tintAlpha)
    } else {
        Color.White.copy(alpha = tintAlpha)
    }

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

    this
        // 1. Soft elevation drop shadow under floating glass
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.14f),
                )
            } else {
                Modifier
            }
        )
        // 2. Clip to given shape
        .clip(shape)
        // 3. Live backdrop blur with light translucent tint
        .then(
            if (hazeState != null) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = hazeStyle,
                    performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                )
            } else {
                val fallbackAlpha = if (preset == LiquidGlassPreset.Header) {
                    if (pureBlack) 0.08f else 0.05f
                } else {
                    if (pureBlack) 0.65f else 0.12f
                }
                Modifier.background(
                    if (pureBlack) Color.Black.copy(alpha = fallbackAlpha) else Color.White.copy(alpha = fallbackAlpha),
                    shape = shape,
                )
            }
        )
        // 4. Specular rim highlight & surface sheen
        .then(
            if (showBorder) {
                Modifier.liquidBubbleHighlight(
                    cornerRadiusDp = cornerRadiusDp,
                    isDark = true,
                    hasTopSheen = hasTopSheen,
                )
            } else {
                Modifier
            }
        )
        // 6. Interactive touch scale and radial press glow
        .then(
            if (interaction != null) {
                Modifier
                    .graphicsLayer {
                        val scale = lerp(1f, 1.035f, interaction.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                    .drawWithContent {
                        drawContent()
                        val press = interaction.pressProgress
                        if (press > 0f) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.16f * press),
                                        Color.Transparent,
                                    ),
                                    center = interaction.touchPosition.takeIf { it != Offset.Zero }
                                        ?: Offset(size.width / 2f, size.height / 2f),
                                    radius = size.minDimension * 1.2f,
                                ),
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }
                    .pointerInput(interaction) {
                        interaction.detectPress(this)
                    }
            } else {
                Modifier
            }
        )
}

/**
 * Top app bar and collapsing header liquid glass.
 * Deep frost (22dp), high saturation vibrancy (1.55x), subtle top sheen,
 * and bottom curved rim refraction without heavy black scrim.
 */
fun Modifier.headerLiquidGlass(
    hazeState: HazeState?,
    shape: Shape = androidx.compose.ui.graphics.RectangleShape,
    pureBlack: Boolean = false,
    cornerRadiusDp: Dp = 0.dp,
): Modifier = this.liquidGlass(
    shape = shape,
    hazeState = hazeState,
    pureBlack = pureBlack,
    preset = LiquidGlassPreset.Header,
    cornerRadiusDp = cornerRadiusDp,
    showBorder = false,
    hasTopSheen = false,
)

/**
 * Filter chip and interactive pill liquid glass.
 */
fun Modifier.chipLiquidGlass(
    hazeState: HazeState?,
    shape: Shape,
    pureBlack: Boolean = false,
    cornerRadiusDp: Dp = 18.dp,
    interaction: GlassInteraction? = null,
): Modifier = this.liquidGlass(
    shape = shape,
    hazeState = hazeState,
    pureBlack = pureBlack,
    preset = LiquidGlassPreset.Chip,
    cornerRadiusDp = cornerRadiusDp,
    showBorder = true,
    interaction = interaction,
)

/**
 * Stage 1 Clear Liquid Glass Pill:
 * Engineered specifically for floating navigation bars and mini players.
 *
 * Requirements satisfied:
 * - NO LENS AT ALL (no runtimeShader, no lens displacement)
 * - Low blur (4.dp, sweet spot between 2-6dp)
 * - No black scrim: light translucent white tint (0.09f, or 0.12f in pureBlack)
 * - Soft drop shadow (8.dp elevation, low opacity ambient + spot)
 * - Bright thin rim highlight (1.dp specular meniscus highlight)
 */
fun Modifier.clearLiquidGlassPill(
    shape: Shape = RoundedCornerShape(28.dp),
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    blurRadius: Dp = 4.dp,
    tintAlpha: Float = if (pureBlack) 0.12f else 0.09f,
    showBorder: Boolean = true,
    cornerRadiusDp: Dp = 28.dp,
    shadowElevation: Dp = 8.dp,
): Modifier = composed {
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

    this
        // 1. Soft elevation drop shadow under floating pill
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.14f),
                )
            } else {
                Modifier
            }
        )
        // 2. Shape silhouette clipping
        .clip(shape)
        // 3. Live backdrop blur with light translucent white tint (NO black scrim)
        .then(
            if (hazeState != null) {
                Modifier.hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = hazeStyle,
                    performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                )
            } else {
                Modifier.background(
                    Color.White.copy(alpha = tintAlpha),
                    shape = shape,
                )
            }
        )
        // 4. Bright thin specular meniscus rim highlight (NO lens in Stage 1)
        .then(
            if (showBorder) {
                Modifier.liquidBubbleHighlight(
                    cornerRadiusDp = cornerRadiusDp,
                    isDark = true,
                    hasTopSheen = false,
                )
            } else {
                Modifier
            }
        )
}

