/*
 * Ozone Player (2026)
 * https://github.com/aswadhkrishna22-prog/OzonePlayer
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import com.kyant.backdrop.backdrops.layerBackdrop as nativeBackdrop

/** Shared opt-in flag for Liquid Glass. Defaults to true in OzonePlayer. */
val LocalLiquidGlass = staticCompositionLocalOf { true }

/** Shared toggle for classic Material 3 full-width bottom navigation bar. */
val LocalMd3NavBar = staticCompositionLocalOf { false }

/** Background-only source for surfaces inside the captured scrolling content. */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Separate source for overlays; kept for call-site compat. */
val LocalLiquidGlassOverlayBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Content-brightness hint, kept for compat. */
val LocalLiquidGlassContentBrightness = compositionLocalOf { 0f }

/** Factory matching Kyant0 Backdrop's official API. */
@Composable
fun rememberLayerBackdrop(
    onDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit = { drawContent() },
): LayerBackdrop? = com.kyant.backdrop.backdrops.rememberLayerBackdrop(onDraw = onDraw)

/** Marks a composable as the source layer that sibling glass surfaces refract. */
fun Modifier.layerBackdropCompat(backdrop: LayerBackdrop): Modifier = this.nativeBackdrop(backdrop)

/** Remember a backdrop that draws a flat color + content. */
@Composable
fun rememberBackdrop(color: Color): LayerBackdrop? =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

/**
 * Continuous-curvature squircle (G2 superellipse) implementing [CornerBasedShape].
 * Guaranteed lens-compatible with Kyant's SDF lens shader.
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp) : this(
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
    )

    constructor(percent: Int = 50) : this(
        CornerSize(percent),
        CornerSize(percent),
        CornerSize(percent),
        CornerSize(percent),
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val tl = if (isLtr) topStart else topEnd
        val tr = if (isLtr) topEnd else topStart
        val br = if (isLtr) bottomEnd else bottomStart
        val bl = if (isLtr) bottomStart else bottomEnd

        if (tl <= 0f && tr <= 0f && br <= 0f && bl <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        }

        val path = createSquirclePath(w, h, tl, tr, br, bl)
        return Outline.Generic(path)
    }
}

fun createSquirclePath(
    w: Float,
    h: Float,
    tlRadius: Float,
    trRadius: Float,
    brRadius: Float,
    blRadius: Float,
): Path {
    val path = Path()
    val maxRadius = minOf(w, h) / 2f
    val tl = tlRadius.coerceIn(0f, maxRadius)
    val tr = trRadius.coerceIn(0f, maxRadius)
    val br = brRadius.coerceIn(0f, maxRadius)
    val bl = blRadius.coerceIn(0f, maxRadius)

    val k = 1.528665f
    var lTl = tl * k
    var lTr = tr * k
    var lBr = br * k
    var lBl = bl * k

    val maxWTop = lTl + lTr
    if (maxWTop > w && maxWTop > 0f) {
        val scale = w / maxWTop
        lTl *= scale
        lTr *= scale
    }
    val maxWBottom = lBl + lBr
    if (maxWBottom > w && maxWBottom > 0f) {
        val scale = w / maxWBottom
        lBl *= scale
        lBr *= scale
    }
    val maxHLeft = lTl + lBl
    if (maxHLeft > h && maxHLeft > 0f) {
        val scale = h / maxHLeft
        lTl *= scale
        lBl *= scale
    }
    val maxHRight = lTr + lBr
    if (maxHRight > h && maxHRight > 0f) {
        val scale = h / maxHRight
        lTr *= scale
        lBr *= scale
    }

    path.moveTo(lTl, 0f)
    path.lineTo(w - lTr, 0f)
    if (lTr > 0.001f) {
        path.cubicTo(
            w - lTr * (1f - 0.712053f), 0f,
            w - lTr * (1f - 0.566789f), lTr * 0.030183f,
            w - lTr * (1f - 0.455953f), lTr * 0.087377f,
        )
        path.cubicTo(
            w - lTr * (1f - 0.345118f), lTr * 0.144571f,
            w - lTr * (1f - 0.242846f), lTr * 0.242846f,
            w - lTr * (1f - 0.144571f), lTr * 0.345118f,
        )
        path.cubicTo(
            w - lTr * (1f - 0.087377f), lTr * 0.455953f,
            w - lTr * 0.030183f, lTr * (1f - 0.566789f),
            w, lTr * (1f - 0.712053f),
        )
        path.lineTo(w, lTr)
    } else {
        path.lineTo(w, 0f)
        path.lineTo(w, lTr)
    }
    path.lineTo(w, h - lBr)
    if (lBr > 0.001f) {
        path.cubicTo(
            w, h - lBr * (1f - 0.712053f),
            w - lBr * 0.030183f, h - lBr * (1f - 0.566789f),
            w - lBr * 0.087377f, h - lBr * (1f - 0.455953f),
        )
        path.cubicTo(
            w - lBr * 0.144571f, h - lBr * (1f - 0.345118f),
            w - lBr * 0.242846f, h - lBr * (1f - 0.242846f),
            w - lBr * 0.345118f, h - lBr * (1f - 0.144571f),
        )
        path.cubicTo(
            w - lBr * 0.455953f, h - lBr * (1f - 0.087377f),
            w - lBr * (1f - 0.566789f), h - lBr * 0.030183f,
            w - lBr * (1f - 0.712053f), h,
        )
        path.lineTo(w - lBr, h)
    } else {
        path.lineTo(w, h)
        path.lineTo(w - lBr, h)
    }
    path.lineTo(lBl, h)
    if (lBl > 0.001f) {
        path.cubicTo(
            lBl * (1f - 0.712053f), h,
            lBl * (1f - 0.566789f), h - lBl * 0.030183f,
            lBl * (1f - 0.455953f), h - lBl * 0.087377f,
        )
        path.cubicTo(
            lBl * (1f - 0.345118f), h - lBl * 0.144571f,
            lBl * (1f - 0.242846f), h - lBl * 0.242846f,
            lBl * (1f - 0.144571f), h - lBl * 0.345118f,
        )
        path.cubicTo(
            lBl * (1f - 0.087377f), h - lBl * 0.455953f,
            lBl * 0.030183f, h - lBl * (1f - 0.566789f),
            0f, h - lBl * (1f - 0.712053f),
        )
        path.lineTo(0f, h - lBl)
    } else {
        path.lineTo(0f, h)
        path.lineTo(0f, h - lBl)
    }
    path.lineTo(0f, lTl)
    if (lTl > 0.001f) {
        path.cubicTo(
            0f, lTl * (1f - 0.712053f),
            lTl * 0.030183f, lTl * (1f - 0.566789f),
            lTl * 0.087377f, lTl * (1f - 0.455953f),
        )
        path.cubicTo(
            lTl * 0.144571f, lTl * (1f - 0.345118f),
            lTl * 0.242846f, lTl * (1f - 0.242846f),
            lTl * 0.345118f, lTl * (1f - 0.144571f),
        )
        path.cubicTo(
            lTl * 0.455953f, lTl * (1f - 0.087377f),
            lTl * (1f - 0.566789f), lTl * 0.030183f,
            lTl * (1f - 0.712053f), 0f,
        )
        path.lineTo(lTl, 0f)
    } else {
        path.lineTo(0f, 0f)
        path.lineTo(lTl, 0f)
    }
    path.close()
    return path
}

/** Keeps glass inside Material's visual bounds while retaining its outer touch target. */
@Composable
fun LiquidGlassSurface(
    onClick: () -> Unit,
    glassModifier: Modifier,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().then(glassModifier),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

// ── Interactive liquid glass core ──────────────────────────────────────────────

@Composable
fun isDeviceGlassCapable(): Boolean {
    val view = LocalView.current
    if (view.isInEditMode) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (!view.isHardwareAccelerated) return false
    val context = LocalContext.current
    val am = remember(context) {
        runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    }
    if (am?.isLowRamDevice == true) return false
    return true
}

@Composable
fun isLiquidGlassBackdropSupported(): Boolean =
    LocalLiquidGlass.current && isDeviceGlassCapable()

@Composable
fun Modifier.liquidGlassSource(
    backdrop: LayerBackdrop?,
): Modifier {
    if (backdrop == null) return this
    if (!isLiquidGlassBackdropSupported()) return this
    return this.layerBackdrop(backdrop)
}

@Composable
fun liquidGlassContainerColor(
    color: Color,
    enabled: Boolean = LocalLiquidGlass.current,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
): Color = if (enabled && isLiquidGlassBackdropSupported() && backdrop != null) {
    if (color.isSpecified && color.alpha < 0.95f) color else Color.Transparent
} else color

@Composable
fun isLiquidGlassEnabled(): Boolean = LocalLiquidGlass.current

/**
 * Press/hold state holder for a single liquid-glass surface.
 * Responsive GlassInteraction: spring press 0→1.
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
            inspectDragGestures(
                onDragStart = { down ->
                    touchPosition = down.position
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
 * Single source of truth for frosted-glass parameters.
 */
object GlassRecipe {
    const val BLUR_BASE_DP = 12f
    const val BLUR_BRIGHT_DP = 20f
    const val BLUR_DARK_DP = 6f

    const val BRIGHTNESS = 0.05f
    const val CONTRAST = 1f
    const val SATURATION = 1.1f

    const val LENS_DIVISOR = 6f

    const val MIN_SCRIM = 0.18f
    const val MAX_SCRIM = 0.56f

    const val PRESS_BLUR_DP = 2f
}

/**
 * Interactive liquid glass drawing modifier.
 * Element MUST be a sibling of the backdrop source, never inside it.
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: Backdrop,
    luminanceAnimation: Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 1.12f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = GlassRecipe.MIN_SCRIM,
    maxScrim: Float = GlassRecipe.MAX_SCRIM,
): Modifier =
    this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { lensSafeGlassShape(shape) },
            highlight = { highlight },
            effects = {
                val l = (luminanceAnimation * 2f - 1f).let { sign(it) * it * it }
                val press = interaction?.pressProgress ?: 0f
                colorControls(
                    brightness = GlassRecipe.BRIGHTNESS,
                    contrast = GlassRecipe.CONTRAST,
                    saturation = GlassRecipe.SATURATION,
                )
                blur(
                    (
                        if (l > 0f) {
                            lerp(
                                GlassRecipe.BLUR_BASE_DP.dp.toPx(),
                                GlassRecipe.BLUR_BRIGHT_DP.dp.toPx(),
                                l,
                            )
                        } else {
                            lerp(
                                GlassRecipe.BLUR_BASE_DP.dp.toPx(),
                                GlassRecipe.BLUR_DARK_DP.dp.toPx(),
                                -l,
                            )
                        }
                    ) * blurScale + GlassRecipe.PRESS_BLUR_DP.dp.toPx() * press,
                )
                lens(
                    size.minDimension / GlassRecipe.LENS_DIVISOR +
                        GlassRecipe.PRESS_BLUR_DP.dp.toPx() * press,
                    size.minDimension / 2f,
                    false,
                )
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
            },
            onDrawSurface = {
                val darken = lerp(minScrim, maxScrim, ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f))
                drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * press),
                                Color.Transparent,
                            ),
                            center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 1.2f,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock =
                if (interaction != null) {
                    {
                        val scale = lerp(1f, pressedScale, interaction.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    null
                },
        ).then(
            if (interaction != null) {
                Modifier.pointerInput(interaction) { interaction.detectPress(this) }
            } else {
                Modifier
            },
        )

/**
 * Top app bar glass parameters.
 */
object HeaderGlassRecipe {
    const val BLUR_DP = 24f
    const val SATURATION = 1.6f
    const val BRIGHTNESS_DARK = 0f
    const val BRIGHTNESS_LIGHT = 0.04f
    const val TINT_DARK = 0.30f
    const val TINT_LIGHT = 0.46f
    const val LENS_HEIGHT_DP = 14f
    const val LENS_AMOUNT_DP = 28f
    val RIM = Highlight(width = 1.dp)
}

/**
 * Top app bar glass drawing modifier using Kyant0 Backdrop.
 */
@Composable
fun Modifier.headerLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
): Modifier {
    if (!LocalLiquidGlass.current || !isLiquidGlassBackdropSupported()) return this
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { lensSafeGlassShape(shape) },
        highlight = { HeaderGlassRecipe.RIM },
        effects = {
            colorControls(
                brightness = if (isDark) HeaderGlassRecipe.BRIGHTNESS_DARK else HeaderGlassRecipe.BRIGHTNESS_LIGHT,
                contrast = 1f,
                saturation = HeaderGlassRecipe.SATURATION,
            )
            blur(HeaderGlassRecipe.BLUR_DP.dp.toPx())
            lens(
                HeaderGlassRecipe.LENS_HEIGHT_DP.dp.toPx().coerceAtMost(size.minDimension / 3f),
                HeaderGlassRecipe.LENS_AMOUNT_DP.dp.toPx().coerceAtMost(size.minDimension / 2f),
                false,
            )
        },
        onDrawBackdrop = { drawBackdrop -> drawBackdrop() },
        onDrawSurface = {
            drawRect(
                (if (isDark) Color.Black else Color.White).copy(
                    alpha = if (isDark) HeaderGlassRecipe.TINT_DARK else HeaderGlassRecipe.TINT_LIGHT,
                ),
            )
            // Sheen: light catching the top of the pane.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isDark) 0.07f else 0.16f),
                    0.55f to Color.Transparent,
                ),
            )
        },
    )
}

/**
 * iOS 26 style clear liquid glass recipe for BottomNavigation and MiniPlayer.
 *
 * 1. Low blur (4dp) so background content behind remains recognizable.
 * 2. No black scrim — uses a very light white tint (0.10 in dark, 0.12 in light).
 * 3. Strong edge refraction: lens height 20dp, amount 28dp, depthEffect on, chromatic aberration off.
 * 4. 1dp directional rim highlight + soft shadow under the pill.
 * 5. Boosted saturation (1.35) and vibrancy so colors behind glow through.
 */
object ClearLiquidGlassRecipe {
    const val BLUR_DP = 4f
    const val SATURATION = 1.35f
    const val BRIGHTNESS_DARK = 0.04f
    const val BRIGHTNESS_LIGHT = 0.04f
    const val CONTRAST = 1.05f
    const val TINT_ALPHA_DARK = 0.10f
    const val TINT_ALPHA_LIGHT = 0.12f
    const val LENS_HEIGHT_DP = 20f
    const val LENS_AMOUNT_DP = 28f
    val RIM = Highlight(width = 1.dp)
    val SHADOW = Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.25f))
}

/**
 * Clear liquid glass drawing modifier for BottomNavigation and MiniPlayer.
 * Shared recipe ensuring the bottom navigation and mini player visually match.
 */
@Composable
fun Modifier.clearLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    interactive: Boolean = false,
    forceDark: Boolean = false,
): Modifier {
    if (!LocalLiquidGlass.current || !isLiquidGlassBackdropSupported()) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
    }
    val isDark = forceDark || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val interaction = if (interactive) rememberGlassInteraction() else null
    val targetShape = lensSafeGlassShape(shape)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { targetShape },
        highlight = { ClearLiquidGlassRecipe.RIM },
        shadow = { ClearLiquidGlassRecipe.SHADOW },
        effects = {
            vibrancy()
            colorControls(
                brightness = if (isDark) ClearLiquidGlassRecipe.BRIGHTNESS_DARK else ClearLiquidGlassRecipe.BRIGHTNESS_LIGHT,
                contrast = ClearLiquidGlassRecipe.CONTRAST,
                saturation = ClearLiquidGlassRecipe.SATURATION,
            )
            blur(ClearLiquidGlassRecipe.BLUR_DP.dp.toPx())
            lens(
                ClearLiquidGlassRecipe.LENS_HEIGHT_DP.dp.toPx().coerceAtMost(size.minDimension / 2f),
                ClearLiquidGlassRecipe.LENS_AMOUNT_DP.dp.toPx().coerceAtMost(size.minDimension / 2f),
                false,
            )
        },
        onDrawBackdrop = { drawBackdrop ->
            drawBackdrop()
        },
        onDrawSurface = {
            drawRect(
                Color.White.copy(
                    alpha = if (isDark) ClearLiquidGlassRecipe.TINT_ALPHA_DARK else ClearLiquidGlassRecipe.TINT_ALPHA_LIGHT,
                ),
            )
            val press = interaction?.pressProgress ?: 0f
            if (press > 0f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f * press),
                            Color.Transparent,
                        ),
                        center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 1.2f,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        },
        layerBlock = if (interaction != null) {
            {
                val scale = lerp(1f, 1.04f, interaction.pressProgress)
                scaleX = scale
                scaleY = scale
            }
        } else null,
    ).then(
        if (interaction != null) {
            Modifier.pointerInput(interaction) { interaction.detectPress(this) }
        } else {
            Modifier
        },
    )
}

/**
 * Lens-safe shape guard: Kyant's lens effect shader requires CornerBasedShape.
 */
fun lensSafeGlassShape(shape: Shape): Shape =
    if (shape == RectangleShape) RoundedCornerShape(0.dp) else shape

/** Interactive liquidGlass: static surfaces use fixed mid-luminance. */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    forceDark: Boolean = false,
): Modifier {
    if (!LocalLiquidGlass.current || !isLiquidGlassBackdropSupported()) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
    }
    val isDark = forceDark || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val interaction = if (interactive) rememberGlassInteraction() else null
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        luminanceAnimation = 0.5f,
        shape = shape,
        interaction = interaction,
        highlight = highlight,
    )
}

/** Interactive liquidGlass overload for luminance-sampling surfaces. */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    luminanceAnimation: Float,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    blurScale: Float = 1f,
    minScrim: Float = GlassRecipe.MIN_SCRIM,
    maxScrim: Float = GlassRecipe.MAX_SCRIM,
    forceDark: Boolean = false,
): Modifier {
    if (!LocalLiquidGlass.current || !isLiquidGlassBackdropSupported()) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
    }
    val isDark = forceDark || (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val interaction = if (interactive) rememberGlassInteraction() else null
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        luminanceAnimation = luminanceAnimation,
        shape = shape,
        interaction = interaction,
        pressedScale = 1.04f,
        blurScale = blurScale,
        minScrim = minScrim,
        maxScrim = maxScrim,
    )
}

@Composable
fun LiquidGlassContainer(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)),
            contentAlignment = contentAlignment,
            content = content,
        )
        return
    }
    Box(
        modifier = modifier.liquidGlass(backdrop, shape, interactive, highlight),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Floating action pill hosting icon buttons in a liquid glass shell. */
@Composable
fun LiquidGlassActionPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable RowScope.() -> Unit,
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    Row(
        modifier = modifier.liquidGlass(backdrop, shape, true, Highlight.Default),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** LiquidGlassIconButton: ImageVector version. */
@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    interactive: Boolean = true,
    highlight: Highlight = Highlight(width = 1.dp),
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        highlight = highlight,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp).clickable(onClick = onClick),
        )
    }
}

/** Compat overload: Painter version. */
@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = true,
        highlight = Highlight(width = 1.dp),
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp).clickable(onClick = onClick),
        )
    }
}

/**
 * Optical presets tuned for specific UI surfaces.
 */
enum class LiquidGlassPreset {
    BottomNavigation,
    MiniPlayer,
    PlayerControls,
    FloatingControls,
    Header,
    ModalSheet,
    ContextMenu,
    Overlay,
    Card,
    Pill,
    Chip,
}

private fun LiquidGlassPreset.isGlassTarget(): Boolean = when (this) {
    LiquidGlassPreset.BottomNavigation,
    LiquidGlassPreset.MiniPlayer,
    LiquidGlassPreset.PlayerControls,
    LiquidGlassPreset.FloatingControls,
    LiquidGlassPreset.Header,
    LiquidGlassPreset.Pill,
    LiquidGlassPreset.Chip -> true
    LiquidGlassPreset.ModalSheet,
    LiquidGlassPreset.ContextMenu,
    LiquidGlassPreset.Overlay,
    LiquidGlassPreset.Card -> false
}

/**
 * Chrome liquid glass modifier mapping presets to dedicated optical profiles.
 */
@Composable
fun Modifier.liquidGlassChrome(
    shape: Shape,
    enabled: Boolean = true,
    preset: LiquidGlassPreset = LiquidGlassPreset.Card,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    interactionSource: MutableInteractionSource? = null,
    exportedBackdrop: LayerBackdrop? = null,
    ambientMotion: Boolean = false,
    contentBrightness: Float = 0f,
    onPointerPosition: ((Offset?) -> Unit)? = null,
    forceDark: Boolean = false,
): Modifier {
    if (!enabled || !preset.isGlassTarget() || backdrop == null || !isLiquidGlassBackdropSupported()) {
        return this
    }
    if (preset == LiquidGlassPreset.BottomNavigation || preset == LiquidGlassPreset.MiniPlayer || preset == LiquidGlassPreset.Pill) {
        return this.clearLiquidGlass(
            backdrop = backdrop,
            shape = shape,
            interactive = preset == LiquidGlassPreset.MiniPlayer,
            forceDark = forceDark,
        )
    }
    val shouldForceDark = forceDark ||
        preset == LiquidGlassPreset.PlayerControls ||
        preset == LiquidGlassPreset.FloatingControls
    return this.liquidGlass(
        backdrop = backdrop,
        shape = shape,
        interactive = preset != LiquidGlassPreset.BottomNavigation && preset != LiquidGlassPreset.Header,
        highlight = Highlight.Default,
        forceDark = shouldForceDark,
    )
}

/**
 * Specular meniscus illumination giving the glass convex rim reflections.
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
        drawPath(silhouette, lowerShade, alpha = 0.40f, style = broadShade)
        drawPath(silhouette, lowerShade, alpha = 0.50f, style = middleShade)
        drawPath(silhouette, lowerShade, alpha = 0.65f, style = narrowShade)
        drawPath(rim, upperReflection, style = rimStroke)
        drawPath(rim, oppositeReflection, style = oppositeStroke)
    }
}

/**
 * Unified Liquid Glass Modifier with automatic Backdrop bridge.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    preset: LiquidGlassPreset = LiquidGlassPreset.Pill,
    blurRadius: Dp = when (preset) {
        LiquidGlassPreset.Pill, LiquidGlassPreset.BottomNavigation, LiquidGlassPreset.MiniPlayer -> 4.dp
        LiquidGlassPreset.Header -> 22.dp
        LiquidGlassPreset.Chip -> 3.dp
        else -> 12.dp
    },
    tintAlpha: Float = when (preset) {
        LiquidGlassPreset.Pill, LiquidGlassPreset.BottomNavigation, LiquidGlassPreset.MiniPlayer -> 0.08f
        LiquidGlassPreset.Header -> if (pureBlack) 0.50f else 0.28f
        LiquidGlassPreset.Chip -> 0.09f
        else -> 0.18f
    },
    showBorder: Boolean = true,
    cornerRadiusDp: Dp = when (preset) {
        LiquidGlassPreset.Pill, LiquidGlassPreset.BottomNavigation, LiquidGlassPreset.MiniPlayer -> 28.dp
        LiquidGlassPreset.Header -> 0.dp
        LiquidGlassPreset.Chip -> 18.dp
        else -> 16.dp
    },
    lensDistortion: Dp = when (preset) {
        LiquidGlassPreset.Pill, LiquidGlassPreset.BottomNavigation, LiquidGlassPreset.MiniPlayer -> 28.dp
        LiquidGlassPreset.Header -> 24.dp
        LiquidGlassPreset.Chip -> 18.dp
        else -> 20.dp
    },
    lensHeight: Dp = when (preset) {
        LiquidGlassPreset.Pill, LiquidGlassPreset.BottomNavigation, LiquidGlassPreset.MiniPlayer -> 18.dp
        LiquidGlassPreset.Header -> 14.dp
        LiquidGlassPreset.Chip -> 12.dp
        else -> 14.dp
    },
    saturation: Float = when (preset) {
        LiquidGlassPreset.Pill, LiquidGlassPreset.BottomNavigation, LiquidGlassPreset.MiniPlayer -> 1.35f
        LiquidGlassPreset.Header -> 1.55f
        LiquidGlassPreset.Chip -> 1.25f
        else -> 1.1f
    },
    shadowElevation: Dp = when (preset) {
        LiquidGlassPreset.Pill, LiquidGlassPreset.BottomNavigation, LiquidGlassPreset.MiniPlayer -> 8.dp
        LiquidGlassPreset.Header -> 0.dp
        LiquidGlassPreset.Chip -> 2.dp
        else -> 4.dp
    },
    hasTopSheen: Boolean = preset == LiquidGlassPreset.Header,
    interaction: GlassInteraction? = null,
    backdrop: Backdrop? = null,
    forceDark: Boolean = false,
): Modifier = composed {
    val activeBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current
    if (activeBackdrop != null && isLiquidGlassBackdropSupported()) {
        when (preset) {
            LiquidGlassPreset.BottomNavigation,
            LiquidGlassPreset.MiniPlayer,
            LiquidGlassPreset.Pill -> {
                return@composed Modifier.clearLiquidGlass(
                    backdrop = activeBackdrop,
                    shape = shape,
                    interactive = interaction != null || preset == LiquidGlassPreset.MiniPlayer,
                    forceDark = forceDark || pureBlack,
                )
            }
            LiquidGlassPreset.Header -> {
                return@composed Modifier.headerLiquidGlass(
                    backdrop = activeBackdrop,
                    shape = shape,
                )
            }
            else -> {
                return@composed Modifier.liquidGlass(
                    backdrop = activeBackdrop,
                    shape = shape,
                    interactive = interaction != null,
                    forceDark = forceDark || pureBlack,
                )
            }
        }
    }

    // Fallback path when activeBackdrop is null
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
                    if (pureBlack) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f),
                    shape = shape,
                )
            }
        )
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
 */
fun Modifier.headerLiquidGlass(
    hazeState: HazeState? = null,
    shape: Shape = RectangleShape,
    pureBlack: Boolean = false,
    cornerRadiusDp: Dp = 0.dp,
    backdrop: Backdrop? = null,
): Modifier = composed {
    val activeBackdrop = backdrop ?: LocalLiquidGlassBackdrop.current
    if (activeBackdrop != null && isLiquidGlassBackdropSupported()) {
        Modifier.headerLiquidGlass(
            backdrop = activeBackdrop,
            shape = shape,
        )
    } else {
        Modifier.liquidGlass(
            shape = shape,
            hazeState = hazeState,
            pureBlack = pureBlack,
            preset = LiquidGlassPreset.Header,
            cornerRadiusDp = cornerRadiusDp,
            showBorder = true,
            hasTopSheen = true,
            backdrop = backdrop,
        )
    }
}

/**
 * Filter chip and interactive pill liquid glass.
 */
fun Modifier.chipLiquidGlass(
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    pureBlack: Boolean = false,
    cornerRadiusDp: Dp = 18.dp,
    interaction: GlassInteraction? = null,
    backdrop: Backdrop? = null,
): Modifier = this.liquidGlass(
    shape = shape,
    hazeState = hazeState,
    pureBlack = pureBlack,
    preset = LiquidGlassPreset.Chip,
    cornerRadiusDp = cornerRadiusDp,
    showBorder = true,
    interaction = interaction,
    backdrop = backdrop,
)

internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent = drag(
            pointerId = down.id,
            onDrag = { onDrag(it, it.positionChange()) },
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
