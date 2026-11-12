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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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

// ── AGSL WATER RIPPLE SHADER (PHYSICAL REFRACTION & SPECULAR CRESTS) ────────────
private const val WATER_RIPPLE_SHADER_SRC = """
    uniform shader composable;
    uniform float2 uResolution;
    // Up to 3 active ripples: (origin.x, origin.y, progress 0..1, active 0 or 1)
    uniform float4 uRipple0;
    uniform float4 uRipple1;
    uniform float4 uRipple2;

    half4 main(float2 coord) {
        if (uRipple0.w < 0.5 && uRipple1.w < 0.5 && uRipple2.w < 0.5) {
            return composable.eval(coord);
        }

        float2 totalOffset = float2(0.0, 0.0);
        float totalSpecular = 0.0;
        float maxDim = max(uResolution.x, uResolution.y);

        // Ripple 0
        if (uRipple0.w > 0.5) {
            float p = uRipple0.z;
            float2 diff = coord - uRipple0.xy;
            float dist = length(diff);
            float waveRadius = p * maxDim * 1.05;
            float dr = dist - waveRadius;

            float envelope = exp(-0.5 * (dr * dr) / (130.0 * 130.0));
            float damping = (1.0 - p) * (1.0 - p * 0.65);

            float phase = dr * (6.2831853 / 70.0);
            float wave = sin(phase) * envelope * damping;
            float slope = cos(phase) * envelope * damping;

            if (dist > 0.5) {
                float2 dir = diff / dist;
                totalOffset += dir * (wave * 20.0);

                float3 N = normalize(float3(-dir * slope * 1.6, 1.0));
                float3 L = normalize(float3(-0.35, -0.85, 0.75));
                float3 H = normalize(L + float3(0.0, 0.0, 1.0));
                float spec = pow(max(0.0, dot(N, H)), 24.0);
                totalSpecular += spec * damping * 0.38;
            }
        }

        // Ripple 1
        if (uRipple1.w > 0.5) {
            float p = uRipple1.z;
            float2 diff = coord - uRipple1.xy;
            float dist = length(diff);
            float waveRadius = p * maxDim * 1.05;
            float dr = dist - waveRadius;

            float envelope = exp(-0.5 * (dr * dr) / (130.0 * 130.0));
            float damping = (1.0 - p) * (1.0 - p * 0.65);

            float phase = dr * (6.2831853 / 70.0);
            float wave = sin(phase) * envelope * damping;
            float slope = cos(phase) * envelope * damping;

            if (dist > 0.5) {
                float2 dir = diff / dist;
                totalOffset += dir * (wave * 20.0);

                float3 N = normalize(float3(-dir * slope * 1.6, 1.0));
                float3 L = normalize(float3(-0.35, -0.85, 0.75));
                float3 H = normalize(L + float3(0.0, 0.0, 1.0));
                float spec = pow(max(0.0, dot(N, H)), 24.0);
                totalSpecular += spec * damping * 0.38;
            }
        }

        // Ripple 2
        if (uRipple2.w > 0.5) {
            float p = uRipple2.z;
            float2 diff = coord - uRipple2.xy;
            float dist = length(diff);
            float waveRadius = p * maxDim * 1.05;
            float dr = dist - waveRadius;

            float envelope = exp(-0.5 * (dr * dr) / (130.0 * 130.0));
            float damping = (1.0 - p) * (1.0 - p * 0.65);

            float phase = dr * (6.2831853 / 70.0);
            float wave = sin(phase) * envelope * damping;
            float slope = cos(phase) * envelope * damping;

            if (dist > 0.5) {
                float2 dir = diff / dist;
                totalOffset += dir * (wave * 20.0);

                float3 N = normalize(float3(-dir * slope * 1.6, 1.0));
                float3 L = normalize(float3(-0.35, -0.85, 0.75));
                float3 H = normalize(L + float3(0.0, 0.0, 1.0));
                float spec = pow(max(0.0, dot(N, H)), 24.0);
                totalSpecular += spec * damping * 0.38;
            }
        }

        // Chromatic dispersion for realistic optical liquid feel
        float2 uvR = clamp(coord + totalOffset * 1.025, float2(0.0, 0.0), uResolution);
        float2 uvG = clamp(coord + totalOffset * 1.000, float2(0.0, 0.0), uResolution);
        float2 uvB = clamp(coord + totalOffset * 0.975, float2(0.0, 0.0), uResolution);

        half4 colR = composable.eval(uvR);
        half4 colG = composable.eval(uvG);
        half4 colB = composable.eval(uvB);

        half3 finalColor = half3(colR.r, colG.g, colB.b) + half3(totalSpecular);
        return half4(clamp(finalColor, half3(0.0, 0.0, 0.0), half3(1.0, 1.0, 1.0)), colG.a);
    }
"""

// ── DOUBLE TAP TO SEEK CONSTANTS ───────────────────────────────────────────────
private const val ACCUMULATION_WINDOW_MS = 700L
private const val RIPPLE_DURATION_MS = 1050 // Tuned for natural, organic liquid propagation
private const val MAX_ACTIVE_RIPPLES = 3
private const val MAX_RINGS_NORMAL = 3
private const val MAX_RINGS_BATTERY_SAVER = 1
private const val RADIUS_SCREEN_WIDTH_FRACTION = 0.55f
private const val TRAVEL_SCREEN_WIDTH_FRACTION = 0.25f
private const val STRETCH_FACTOR = 1.35f
private const val STROKE_WIDTH_START_DP = 5f
private const val STROKE_WIDTH_END_DP = 1f
private const val ALPHA_START = 0.45f
private const val LABEL_FADE_IN_MS = 140
private const val LABEL_FADE_OUT_MS = 240

private class RippleInstance(
    val id: Long,
    val origin: Offset,
    val isForward: Boolean,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f),
    var job: Job? = null,
)

/**
 * DoubleTapSeekOverlay applies real fluid water wave displacement and specular highlights
 * to its content (on Android 13+ via AGSL RuntimeShader) with smooth Canvas fallback,
 * while managing YouTube-style accumulation for backward/forward seeking.
 *
 * Touch events are handled via [detectTapGestures] on double-tap (and single-tap
 * during an active accumulation window), ensuring drags and other gestures keep working.
 */
@Composable
fun DoubleTapSeekOverlay(
    durationMs: Long,
    positionFlow: StateFlow<Long>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    seekStepSeconds: Int = 10,
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

    // ── AGSL RuntimeShader & RenderEffect Setup ────────────────────────────────
    val runtimeShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(WATER_RIPPLE_SHADER_SRC)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    val shaderRenderEffect = remember(runtimeShader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
            try {
                RenderEffect
                    .createRuntimeShaderEffect(runtimeShader, "composable")
                    .asComposeRenderEffect()
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    // ── Accumulation State ─────────────────────────────────────────────────────
    var isAccumulating by remember { mutableStateOf(false) }
    var accumulatedSeconds by remember { mutableIntStateOf(0) }
    var activeIsForward by remember { mutableStateOf(true) }
    var lastTapTimestampMs by remember { mutableLongStateOf(0L) }
    var targetSeekPositionMs by remember { mutableLongStateOf(-1L) }

    val labelAlpha = remember { Animatable(0f) }
    val labelScale = remember { Animatable(1f) }
    var labelFadeJob by remember { mutableStateOf<Job?>(null) }
    var accumulationTimerJob by remember { mutableStateOf<Job?>(null) }

    // ── Ripple Pool (Up to 3 active ripples at once) ───────────────────────────
    val activeRipples = remember { mutableStateListOf<RippleInstance>() }
    var rippleIdCounter by remember { mutableLongStateOf(0L) }

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
            val ripple = RippleInstance(
                id = ++rippleIdCounter,
                origin = offset,
                isForward = activeIsForward
            )

            if (activeRipples.size >= MAX_ACTIVE_RIPPLES) {
                val oldest = activeRipples.removeAt(0)
                oldest.job?.cancel()
            }
            activeRipples.add(ripple)

            ripple.job = coroutineScope.launch {
                ripple.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = RIPPLE_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                )
                activeRipples.remove(ripple)
            }
        }

        labelFadeJob?.cancel()
        labelFadeJob = coroutineScope.launch {
            labelScale.snapTo(0.82f)
            launch {
                labelScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                )
            }
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
        // ── 1. Content Layer: Receives AGSL Water Ripple Distortion ──────────
        if (content != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null && activeRipples.isNotEmpty()) {
                            runtimeShader.setFloatUniform("uResolution", size.width, size.height)

                            val r0 = activeRipples.getOrNull(0)
                            if (r0 != null) {
                                runtimeShader.setFloatUniform("uRipple0", r0.origin.x, r0.origin.y, r0.progress.value, 1f)
                            } else {
                                runtimeShader.setFloatUniform("uRipple0", 0f, 0f, 0f, 0f)
                            }

                            val r1 = activeRipples.getOrNull(1)
                            if (r1 != null) {
                                runtimeShader.setFloatUniform("uRipple1", r1.origin.x, r1.origin.y, r1.progress.value, 1f)
                            } else {
                                runtimeShader.setFloatUniform("uRipple1", 0f, 0f, 0f, 0f)
                            }

                            val r2 = activeRipples.getOrNull(2)
                            if (r2 != null) {
                                runtimeShader.setFloatUniform("uRipple2", r2.origin.x, r2.origin.y, r2.progress.value, 1f)
                            } else {
                                runtimeShader.setFloatUniform("uRipple2", 0f, 0f, 0f, 0f)
                            }

                            renderEffect = shaderRenderEffect
                        } else {
                            renderEffect = null
                        }
                    }
            ) {
                content()
            }
        }

        // ── 2. Overlay Layer: Gestures & Crisp Floating Text & Fallback Rings ─
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
                        runtimeShader != null &&
                        !animationsDisabled &&
                        animatorScale > 0f

                // Fallback specular rings on Canvas if shader is not available (Android < 13)
                if (!isShaderActive && !animationsDisabled && animatorScale > 0f) {
                    val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(canvasWidth)
                    val maxRadius = screenWidth * RADIUS_SCREEN_WIDTH_FRACTION
                    val maxTravel = screenWidth * TRAVEL_SCREEN_WIDTH_FRACTION

                    for (ripple in activeRipples) {
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

                // Plain +10 / -10 text (no '>>>', no pill background / border)
                val currentLabelAlpha = labelAlpha.value
                val currentLabelScale = labelScale.value
                if (currentLabelAlpha > 0.01f && accumulatedSeconds > 0) {
                    val text = if (activeIsForward) "+$accumulatedSeconds" else "-$accumulatedSeconds"

                    val textLayoutResult = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = TextStyle(
                            color = Color.White.copy(alpha = currentLabelAlpha),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.70f * currentLabelAlpha),
                                offset = Offset(0f, 3f),
                                blurRadius = 10f
                            )
                        )
                    )

                    val textWidth = textLayoutResult.size.width.toFloat()
                    val textHeight = textLayoutResult.size.height.toFloat()

                    val targetCenterX = if (activeIsForward) canvasWidth * 0.75f else canvasWidth * 0.25f
                    val targetCenterY = canvasHeight * 0.5f

                    val textLeft = targetCenterX - (textWidth / 2f)
                    val textTop = targetCenterY - (textHeight / 2f)

                    scale(scale = currentLabelScale, pivot = Offset(targetCenterX, targetCenterY)) {
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(textLeft, textTop)
                        )
                    }
                }
            }
        }
    }
}
