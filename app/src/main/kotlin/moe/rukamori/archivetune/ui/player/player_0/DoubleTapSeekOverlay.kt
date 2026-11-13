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
import androidx.compose.ui.graphics.asComposeRenderEffect
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

// ── AGSL WATER RIPPLE SHADER (PHYSICAL REFRACTION DISTORTION) ──────────────────
private const val WATER_RIPPLE_SHADER_SRC = """
    uniform shader content;
    uniform float2 size;
    uniform float4 r0;
    uniform float4 r1;
    uniform float4 r2;
    uniform float amp;
    uniform float waveLen;

    float2 disp(float2 p, float4 r) {
        float t = r.z;
        if (t <= 0.0 || t >= 1.0) { return float2(0.0, 0.0); }
        float2 c = float2(r.x + r.w * size.x * 0.25 * t, r.y);
        float2 d = p - c;
        float dist = length(d);
        float front = t * size.x * 0.55;
        float x = dist - front;
        float arg = x / (waveLen * 1.6);
        float env = exp(-arg * arg) * (1.0 - t) * (1.0 - t);
        float w = sin(6.2831853 * x / waveLen) * env * amp;
        float2 dir = dist > 0.001 ? (d / dist) : float2(0.0, 0.0);
        return dir * w;
    }

    half4 main(float2 p) {
        float2 q = p + disp(p, r0) + disp(p, r1) + disp(p, r2);
        return content.eval(q);
    }
"""

// ── DOUBLE TAP TO SEEK & WATER RIPPLE TIMING CONSTANTS ─────────────────────────
const val RIPPLE_DURATION_MS = 1000L
const val RIPPLE_DURATION_BATTERY_MS = 800L
const val RING_STAGGER_MS = 90L
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
const val ALPHA_START = 0.22f // 40% of previous 0.55f for thin subtle crest
const val LABEL_FADE_IN_MS = 140
const val LABEL_FADE_OUT_MS = 240
const val WAVE_AMP_PX = 14f
const val WAVE_LEN_PX = 90f

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
}

val GlobalFullPlayerRippleState = WaterRippleState()

/**
 * Applies the AGSL water ripple distortion as a RenderEffect to the calling modifier.
 * Active only while ripples are progressing; reverts to null when idle for zero overhead.
 * Uniform updates recreate RenderEffect each frame in graphicsLayer draw phase without recomposition.
 */
fun Modifier.waterRippleEffect(
    rippleState: WaterRippleState?,
    amp: Float = WAVE_AMP_PX,
    waveLen: Float = WAVE_LEN_PX,
): Modifier {
    if (rippleState == null) return this
    return this.graphicsLayer {
        val isShaderSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val shouldAnimate = !rippleState.animationsDisabled && rippleState.animatorScale > 0f
        val hasActiveRipples = rippleState.activeRipples.isNotEmpty()

        if (isShaderSupported && shouldAnimate && !rippleState.isPowerSaveMode && hasActiveRipples) {
            val shader = rippleState.getOrCreateShader()
            val w = size.width
            val h = size.height
            if (shader != null && w > 0f && h > 0f) {
                shader.setFloatUniform("size", w, h)
                shader.setFloatUniform("amp", amp)
                shader.setFloatUniform("waveLen", waveLen)

                for (i in 0 until 3) {
                    val ripple = rippleState.activeRipples.getOrNull(i)
                    val uniformName = "r$i"
                    if (ripple != null) {
                        val p = ripple.progress.value
                        val dir = if (ripple.isForward) 1.0f else -1.0f
                        shader.setFloatUniform(uniformName, ripple.origin.x, ripple.origin.y, p, dir)
                    } else {
                        shader.setFloatUniform(uniformName, 0f, 0f, -1f, 1f)
                    }
                }

                // Recreate RenderEffect when uniforms change so Android RenderNode applies the update
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
            } else {
                renderEffect = null
            }
        } else {
            renderEffect = null
        }
    }
}

/**
 * DoubleTapSeekOverlay handles double-tap seek detection, label accumulation,
 * and initiates physical water ripples driven by [rippleState].
 * Draws thin white rings (40% alpha) to highlight wave fronts and bold white "+10" / "-10" text.
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

        // Overlay layer: Gestures & Crisp bold text & Thin white rings
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

                val shouldDrawRings = !animationsDisabled && animatorScale > 0f
                if (shouldDrawRings) {
                    val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(canvasWidth)
                    val maxRadius = screenWidth * RADIUS_SCREEN_WIDTH_FRACTION
                    val maxTravel = screenWidth * TRAVEL_SCREEN_WIDTH_FRACTION
                    val ringCount = if (isPowerSaveMode) MAX_RINGS_BATTERY_SAVER else MAX_RINGS_NORMAL
                    val duration = (if (isPowerSaveMode) RIPPLE_DURATION_BATTERY_MS else RIPPLE_DURATION_MS).toFloat()
                    val staggerFraction = RING_STAGGER_MS.toFloat() / duration

                    for (ripple in rippleState.activeRipples) {
                        val directionX = if (ripple.isForward) 1f else -1f
                        val masterP = ripple.progress.value
                        if (masterP <= 0f || masterP >= 1f) continue

                        for (ringIdx in 0 until ringCount) {
                            val staggerOffset = ringIdx * staggerFraction
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

                // Plain +10 / -10 text (no chevrons, no pill, no border/outline, white bold)
                val currentLabelAlpha = labelAlpha.value
                if (currentLabelAlpha > 0.01f && accumulatedSeconds > 0) {
                    val text = if (activeIsForward) "+$accumulatedSeconds" else "-$accumulatedSeconds"

                    val textStyle = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = currentLabelAlpha)
                    )

                    val textResult = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = textStyle
                    )

                    val textWidth = textResult.size.width.toFloat()
                    val textHeight = textResult.size.height.toFloat()

                    val targetCenterX = if (activeIsForward) canvasWidth * 0.75f else canvasWidth * 0.25f
                    val targetCenterY = canvasHeight * 0.5f

                    val textLeft = targetCenterX - (textWidth / 2f)
                    val textTop = targetCenterY - (textHeight / 2f)

                    drawText(
                        textLayoutResult = textResult,
                        topLeft = Offset(textLeft, textTop),
                        color = Color.White.copy(alpha = currentLabelAlpha)
                    )
                }
            }
        }
    }
}
