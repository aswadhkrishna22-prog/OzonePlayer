/*
 * Ozone Player (2026) | Real Liquid Glass Engine (Kyant Backdrop Port)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * Checks whether the current device is capable of rendering real Kyant Backdrop glass.
 * Gated to Android 12+ (API 31+), hardware-accelerated windows, non-low-RAM devices,
 * and not in Compose edit/preview mode.
 */
@Composable
fun isBackdropGlassCapable(): Boolean {
    val view = LocalView.current
    if (view.isInEditMode) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (view.isAttachedToWindow && !view.isHardwareAccelerated) return false
    val context = LocalContext.current
    val am = remember(context) {
        runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    }
    if (am?.isLowRamDevice == true) return false
    android.util.Log.d(
        "GlassDbg",
        "isBackdropGlassCapable=true sdk=${Build.VERSION.SDK_INT} attached=${view.isAttachedToWindow} hwAcc=${view.isHardwareAccelerated} lowRam=${am?.isLowRamDevice}",
    )
    return true
}

/**
 * Shared frosted-glass recipe ported directly from LastWave.
 * Exact constants for optical blur, contrast, saturation, and lens physics.
 */
private object BackdropGlassRecipe {
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
 * Lens-safe shape guard: Kyant's lens effect only accepts corner-based shapes.
 * Its SDF shader throws UnsupportedOperationException if passed a plain RectangleShape
 * or non-corner-based shape. This safely maps any non-CornerBasedShape to a zero-radius
 * RoundedCornerShape.
 */
internal fun lensSafeGlassShape(shape: Shape): Shape =
    if (shape is CornerBasedShape) shape else RoundedCornerShape(0.dp)

/**
 * Press/hold physics state holder for a liquid-glass surface.
 * Responsive spring press 0 -> 1, observe-only drag so child clicks keep working.
 */
class BackdropGlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0f at rest, animating to 1f while pressed. Read in draw/effect/layer blocks. */
    val pressProgress: Float get() = pressAnimation.value

    /** Local-space touch point used as the centre of the press glow. */
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
fun rememberBackdropGlassInteraction(): BackdropGlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { BackdropGlassInteraction(scope) }
}

/**
 * Interactive liquid glass drawing modifier using Kyant Backdrop.
 * Element MUST be a sibling of the backdrop source, never inside it.
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: Backdrop,
    luminanceAnimation: Float,
    shape: Shape,
    interaction: BackdropGlassInteraction?,
    pressedScale: Float = 1.12f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = BackdropGlassRecipe.MIN_SCRIM,
    maxScrim: Float = BackdropGlassRecipe.MAX_SCRIM,
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
                    brightness = BackdropGlassRecipe.BRIGHTNESS,
                    contrast = BackdropGlassRecipe.CONTRAST,
                    saturation = BackdropGlassRecipe.SATURATION,
                )
                blur(
                    (
                        if (l > 0f) {
                            lerp(
                                BackdropGlassRecipe.BLUR_BASE_DP.dp.toPx(),
                                BackdropGlassRecipe.BLUR_BRIGHT_DP.dp.toPx(),
                                l,
                            )
                        } else {
                            lerp(
                                BackdropGlassRecipe.BLUR_BASE_DP.dp.toPx(),
                                BackdropGlassRecipe.BLUR_DARK_DP.dp.toPx(),
                                -l,
                            )
                        }
                    ) * blurScale + BackdropGlassRecipe.PRESS_BLUR_DP.dp.toPx() * press,
                )
                lens(
                    size.minDimension / BackdropGlassRecipe.LENS_DIVISOR +
                        BackdropGlassRecipe.PRESS_BLUR_DP.dp.toPx() * press,
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
 * Public composable modifier for rendering real Kyant Backdrop Liquid Glass.
 */
@Composable
fun Modifier.backdropLiquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    isDark: Boolean,
    interactive: Boolean = true,
    pressedScale: Float = 1.12f,
    luminance: Float = 0.5f,
    highlight: Highlight = Highlight.Default,
): Modifier {
    val interaction = rememberBackdropGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        luminanceAnimation = luminance,
        shape = shape,
        interaction = if (interactive) interaction else null,
        pressedScale = pressedScale,
        highlight = highlight,
    )
}

/**
 * Temporary DEBUG mode for verifying whether Kyant drawBackdrop path or Haze fallback is active:
 * - If glassBackdrop != null (Kyant path): effects = { blur(0.dp.toPx()); lens(24.dp.toPx(), 48.dp.toPx(), true) } with NO scrim, 2dp GREEN border.
 * - Else (Haze fallback): 2dp RED border.
 */
@Composable
fun Modifier.debugGlassPill(
    backdrop: Backdrop?,
    shape: Shape,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    cornerRadiusDp: Dp = 28.dp,
): Modifier {
    return if (backdrop != null) {
        this
            .drawBackdrop(
                backdrop = backdrop,
                shape = { lensSafeGlassShape(shape) },
                highlight = { Highlight.Default },
                effects = {
                    blur(0.dp.toPx())
                    lens(24.dp.toPx(), 48.dp.toPx(), true)
                },
                onDrawBackdrop = { drawBackdrop ->
                    drawBackdrop()
                },
                onDrawSurface = {
                    // NO scrim
                },
            )
            .border(2.dp, Color.Green, shape)
    } else {
        this
            .clearLiquidGlassPill(
                shape = shape,
                hazeState = hazeState,
                pureBlack = pureBlack,
                showBorder = false,
                cornerRadiusDp = cornerRadiusDp,
            )
            .border(2.dp, Color.Red, shape)
    }
}

/**
 * Observe-only drag/press recogniser (Kyant catalog DragGestureInspector).
 * Never consumes events, so glass reacts while wrapped buttons keep their taps.
 */
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
