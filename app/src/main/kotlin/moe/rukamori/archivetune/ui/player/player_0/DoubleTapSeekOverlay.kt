/*
 * Ozone Player (2026) | Double Tap to Seek with AGSL Water Ripple Physics
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.ui.player.player_0

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import kotlin.math.max

// ── AGSL WATER RIPPLE SHADER (PHYSICAL REFRACTION & SPECULAR CRESTS) ────────────
private const val WATER_RIPPLE_SHADER_SRC = """
    uniform shader content;
    uniform float2 size;
    uniform float2 centers[3];
    uniform float times[3];
    uniform float strength;
    uniform float speed;
    uniform float waveLen;
    uniform float decay;
    uniform float stretchX;

    float height(float r, float t) {
        float x = r - speed * t;
        float env = exp(-x * x / (4.5 * waveLen * waveLen));
        return sin(6.2831853 * x / waveLen) * env * exp(-decay * t) / sqrt(1.0 + r / waveLen);
    }

    half4 main(float2 p) {
        float2 off = float2(0.0, 0.0);
        float shade = 0.0;
        for (int i = 0; i < 3; i++) {
            if (times[i] < 0.0) continue;
            float2 d = p - centers[i];
            d.x /= stretchX;
            float r = length(d);
            float2 dir = d / max(r, 0.001);
            float dh = (height(r + 1.0, times[i]) - height(r - 1.0, times[i])) * 0.5;
            off += dir * dh * strength;
            shade += dh;
        }
        float2 q = clamp(p + off, float2(0.0, 0.0), size);
        half4 c = content.eval(q);
        c.rgb += half3(half(shade * 1.5)) * c.a;
        return c;
    }
"""

// ── DOUBLE TAP TO SEEK & WATER RIPPLE TIMING CONSTANTS ─────────────────────────
const val RIPPLE_DURATION_MS = 1600L
const val RIPPLE_DURATION_BATTERY_MS = 1000L
const val ACCUMULATION_WINDOW_MS = 700L
const val MAX_ACTIVE_RIPPLES = 3
const val MAX_ACTIVE_RIPPLES_BATTERY = 1
const val MAX_RINGS_NORMAL = 3
const val MAX_RINGS_BATTERY_SAVER = 1
const val RADIUS_SCREEN_WIDTH_FRACTION = 0.55f
const val TRAVEL_SCREEN_WIDTH_FRACTION = 0.25f
const val STRETCH_FACTOR = 1.35f
const val STROKE_WIDTH_START_DP = 5f
const val STROKE_WIDTH_END_DP = 1f
const val ALPHA_START = 0.45f
const val LABEL_FADE_IN_MS = 140
const val LABEL_FADE_OUT_MS = 240

class ActiveRipple(
    val id: Long,
    val origin: Offset,
    val isForward: Boolean,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f),
    var job: Job? = null,
)

@Stable
class WaterRippleState {
    val activeRipples = mutableStateListOf<ActiveRipple>()
    var rippleIdCounter by mutableLongStateOf(0L)
    var isPowerSaveMode by mutableStateOf(false)
    var animationsDisabled by mutableStateOf(false)
    var animatorScale by mutableFloatStateOf(1f)

    private var cachedShader: RuntimeShader? = null
    private var cachedRenderEffect: RenderEffect? = null
    private var composeRenderEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun getOrCreateShader(): RuntimeShader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (cachedShader == null) {
            try {
                cachedShader = RuntimeShader(WATER_RIPPLE_SHADER_SRC)
            } catch (_: Throwable) {
                cachedShader = null
            }
        }
        return cachedShader
    }

    fun getOrCreateRenderEffect(shader: RuntimeShader): androidx.compose.ui.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (cachedRenderEffect == null || composeRenderEffect == null) {
            try {
                cachedRenderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                composeRenderEffect = cachedRenderEffect?.asComposeRenderEffect()
            } catch (_: Throwable) {
                composeRenderEffect = null
            }
        }
        return composeRenderEffect
    }
}

val GlobalFullPlayerRippleState = WaterRippleState()

/**
 * Applies the AGSL water ripple distortion as a RenderEffect to the calling modifier.
 * Active only while ripples are progressing; reverts to null when idle for zero overhead.
 */
fun Modifier.waterRippleEffect(rippleState: WaterRippleState?): Modifier {
    if (rippleState == null) return this
    return this.graphicsLayer {
        val shouldAnimate = !rippleState.animationsDisabled && rippleState.animatorScale > 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shouldAnimate && rippleState.activeRipples.isNotEmpty()) {
            val shader = rippleState.getOrCreateShader()
            val w = size.width
            val h = size.height
            if (shader == null || w <= 0f || h <= 0f) {
                renderEffect = null
                return@graphicsLayer
            }

            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("stretchX", STRETCH_FACTOR)
            shader.setFloatUniform("waveLen", 80f)
            shader.setFloatUniform("decay", 2.5f)
            shader.setFloatUniform("strength", if (rippleState.isPowerSaveMode) 120f else 200f)

            val maxRadius = max(w, h) * 0.95f
            val durationSec = (if (rippleState.isPowerSaveMode) RIPPLE_DURATION_BATTERY_MS else RIPPLE_DURATION_MS) / 1000f
            val speed = maxRadius / durationSec
            shader.setFloatUniform("speed", speed)

            val maxTravel = w * TRAVEL_SCREEN_WIDTH_FRACTION
            val centers = FloatArray(6)
            val times = FloatArray(3)

            for (i in 0 until 3) {
                val ripple = rippleState.activeRipples.getOrNull(i)
                if (ripple != null) {
                    val p = ripple.progress.value
                    val t = p * durationSec
                    times[i] = t

                    val directionX = if (ripple.isForward) 1f else -1f
                    val cx = ripple.origin.x + (p * maxTravel * directionX)
                    val cy = ripple.origin.y
                    centers[i * 2] = cx
                    centers[i * 2 + 1] = cy
                } else {
                    times[i] = -1.0f
                    centers[i * 2] = 0f
                    centers[i * 2 + 1] = 0f
                }
            }

            shader.setFloatUniform("centers", centers)
            shader.setFloatUniform("times", times)

            renderEffect = rippleState.getOrCreateRenderEffect(shader)
        } else {
            renderEffect = null
        }
    }
}

/**
 * DoubleTapSeekOverlay handles double-tap seek detection, label accumulation,
 * and initiates physical water ripples driven by [rippleState].
 */
@Composable
fun DoubleTapSeekOverlay(
    durationMs: Long,
    positionFlow: StateFlow<Long>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    seekStepSeconds: Int = 10,
    rippleState: WaterRippleState = GlobalFullPlayerRippleState,
    content: (@Composable () -> Unit)? = null,
) {
    if (!enabled) {
        if (content != null) {
            Box(modifier = modifier) { content() }
        }
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalYumaHaptics.current
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    // ── Reduced Motion & Battery Saver Observers ───────────────────────────────
    val animationsDisabled = LocalAnimationsDisabled.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var isPowerSaveMode by remember { mutableStateOf(powerManager?.isPowerSaveMode == true) }

    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                isPowerSaveMode = powerManager?.isPowerSaveMode == true
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    var animatorScale by remember {
        mutableFloatStateOf(
            try {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            } catch (_: Exception) {
                1f
            }
        )
    }

    // Sync environment state to the active ripple controller
    rippleState.isPowerSaveMode = isPowerSaveMode
    rippleState.animationsDisabled = animationsDisabled
    rippleState.animatorScale = animatorScale

    // ── Accumulation State ─────────────────────────────────────────────────────
    var isAccumulating by remember { mutableStateOf(false) }
    var accumulatedSeconds by remember { mutableIntStateOf(0) }
    var activeIsForward by remember { mutableStateOf(true) }
    var lastTapTimestampMs by remember { mutableLongStateOf(0L) }
    var targetSeekPositionMs by remember { mutableLongStateOf(-1L) }

    val labelAlpha = remember { Animatable(0f) }
    var labelFadeJob by remember { mutableStateOf<Job?>(null) }
    var accumulationTimerJob by remember { mutableStateOf<Job?>(null) }

    val startStrokePx = with(density) { STROKE_WIDTH_START_DP.dp.toPx() }
    val endStrokePx = with(density) { STROKE_WIDTH_END_DP.dp.toPx() }
    val outlineBorderWidthPx = with(density) { 3.dp.toPx() }

    fun triggerSeekAndRipple(offset: Offset, isRightSide: Boolean) {
        val now = System.currentTimeMillis()
        val isWindowActive = isAccumulating && (now - lastTapTimestampMs <= ACCUMULATION_WINDOW_MS)
        lastTapTimestampMs = now

        val isSameDirection = (isRightSide == activeIsForward)
        if (!isWindowActive || !isSameDirection) {
            isAccumulating = true
            activeIsForward = isRightSide
            accumulatedSeconds = seekStepSeconds
            targetSeekPositionMs = -1L
        } else {
            accumulatedSeconds += seekStepSeconds
        }

        haptics.segmentTick()

        val basePos = if (targetSeekPositionMs >= 0L) targetSeekPositionMs else positionFlow.value
        val stepMs = seekStepSeconds * 1000L
        val deltaMs = if (activeIsForward) stepMs else -stepMs
        val newPos = (basePos + deltaMs).coerceIn(0L, durationMs)
        targetSeekPositionMs = newPos
        onSeek(newPos.toFloat())

        val shouldAnimate = !animationsDisabled && animatorScale > 0f
        if (shouldAnimate) {
            val maxRipples = if (isPowerSaveMode) MAX_ACTIVE_RIPPLES_BATTERY else MAX_ACTIVE_RIPPLES
            val ripple = ActiveRipple(
                id = ++rippleState.rippleIdCounter,
                origin = offset,
                isForward = activeIsForward
            )

            if (rippleState.activeRipples.size >= maxRipples) {
                val oldest = rippleState.activeRipples.removeAt(0)
                oldest.job?.cancel()
            }
            rippleState.activeRipples.add(ripple)

            val duration = if (isPowerSaveMode) RIPPLE_DURATION_BATTERY_MS else RIPPLE_DURATION_MS
            ripple.job = coroutineScope.launch {
                ripple.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration.toInt(),
                        easing = LinearEasing
                    )
                )
                rippleState.activeRipples.remove(ripple)
            }
        }

        labelFadeJob?.cancel()
        labelFadeJob = coroutineScope.launch {
            labelAlpha.animateTo(1f, tween(LABEL_FADE_IN_MS))
        }

        accumulationTimerJob?.cancel()
        accumulationTimerJob = coroutineScope.launch {
            delay(ACCUMULATION_WINDOW_MS)
            isAccumulating = false
            targetSeekPositionMs = -1L
            labelAlpha.animateTo(0f, tween(LABEL_FADE_OUT_MS))
            accumulatedSeconds = 0
        }
    }

    Box(modifier = modifier) {
        if (content != null) {
            content()
        }

        // Overlay layer: Gestures & Crisp outline text & Fallback rings for API < 33
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(enabled, durationMs, seekStepSeconds) {
                    if (durationMs <= 0L) return@pointerInput

                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val isRightSide = offset.x >= (size.width / 2f)
                            triggerSeekAndRipple(offset, isRightSide)
                        },
                        onTap = { offset ->
                            val now = System.currentTimeMillis()
                            val isWindowActive = isAccumulating && (now - lastTapTimestampMs <= ACCUMULATION_WINDOW_MS)
                            if (!isWindowActive) return@detectTapGestures

                            val isRightSide = offset.x >= (size.width / 2f)
                            triggerSeekAndRipple(offset, isRightSide)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

                val isShaderActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !animationsDisabled &&
                        animatorScale > 0f

                // Fallback specular rings on Canvas if shader is not available (Android < 13)
                if (!isShaderActive && !animationsDisabled && animatorScale > 0f) {
                    val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(canvasWidth)
                    val maxRadius = screenWidth * RADIUS_SCREEN_WIDTH_FRACTION
                    val maxTravel = screenWidth * TRAVEL_SCREEN_WIDTH_FRACTION

                    for (ripple in rippleState.activeRipples) {
                        val directionX = if (ripple.isForward) 1f else -1f
                        val masterP = ripple.progress.value
                        if (masterP <= 0f || masterP >= 1f) continue

                        val ringCount = if (isPowerSaveMode) MAX_RINGS_BATTERY_SAVER else MAX_RINGS_NORMAL
                        for (ringIdx in 0 until ringCount) {
                            val staggerOffset = ringIdx * 0.12f
                            val p = ((masterP - staggerOffset) / (1f - staggerOffset)).coerceIn(0f, 1f)
                            if (p <= 0f || p >= 1f) continue

                            val centerX = ripple.origin.x + (p * maxTravel * directionX)
                            val centerY = ripple.origin.y
                            val radius = p * maxRadius
                            val radiusX = radius * STRETCH_FACTOR
                            val radiusY = radius

                            val strokeWidth = startStrokePx + p * (endStrokePx - startStrokePx)
                            val quadraticFalloff = (1f - p) * (1f - p)
                            val alpha = (ALPHA_START * quadraticFalloff).coerceIn(0f, 1f)

                            if (alpha > 0.005f && strokeWidth > 0.1f && radius > 0.5f) {
                                drawOval(
                                    color = Color.White.copy(alpha = alpha),
                                    topLeft = Offset(centerX - radiusX, centerY - radiusY),
                                    size = Size(radiusX * 2f, radiusY * 2f),
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                        }
                    }
                }

                // Plain +10 / -10 text with black outline (border) and white fill
                val currentLabelAlpha = labelAlpha.value
                if (currentLabelAlpha > 0.01f && accumulatedSeconds > 0) {
                    val text = if (activeIsForward) "+$accumulatedSeconds" else "-$accumulatedSeconds"

                    val outlineStyle = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        drawStyle = Stroke(width = outlineBorderWidthPx, join = StrokeJoin.Round)
                    )
                    val fillStyle = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        drawStyle = Fill
                    )

                    val strokeResult = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = outlineStyle
                    )
                    val fillResult = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = fillStyle
                    )

                    val textWidth = fillResult.size.width.toFloat()
                    val textHeight = fillResult.size.height.toFloat()

                    val targetCenterX = if (activeIsForward) canvasWidth * 0.75f else canvasWidth * 0.25f
                    val targetCenterY = canvasHeight * 0.5f

                    val textLeft = targetCenterX - (textWidth / 2f)
                    val textTop = targetCenterY - (textHeight / 2f)

                    // Layer 1: Black outline border
                    drawText(
                        textLayoutResult = strokeResult,
                        topLeft = Offset(textLeft, textTop),
                        color = Color.Black.copy(alpha = currentLabelAlpha)
                    )

                    // Layer 2: White fill
                    drawText(
                        textLayoutResult = fillResult,
                        topLeft = Offset(textLeft, textTop),
                        color = Color.White.copy(alpha = currentLabelAlpha)
                    )
                }
            }
        }
    }
}
