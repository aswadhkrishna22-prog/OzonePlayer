/*
 * Ozone Player (2026) | Double Tap to Seek with Water Ripple Animation
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.ui.player.player_0

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics

// ── DOUBLE TAP TO SEEK & WATER RIPPLE CONSTANTS ────────────────────────────────
private const val ACCUMULATION_WINDOW_MS = 700L
private const val RIPPLE_DURATION_MS = 700
private const val RING_STAGGER_MS = 90L
private const val MAX_ACTIVE_RIPPLES = 3
private const val MAX_RINGS_NORMAL = 3
private const val MAX_RINGS_BATTERY_SAVER = 1
private const val RADIUS_SCREEN_WIDTH_FRACTION = 0.55f
private const val TRAVEL_SCREEN_WIDTH_FRACTION = 0.25f
private const val STRETCH_FACTOR = 1.35f
private const val STROKE_WIDTH_START_DP = 6f
private const val STROKE_WIDTH_END_DP = 1f
private const val ALPHA_START = 0.55f
private const val LABEL_FADE_IN_MS = 150
private const val LABEL_FADE_OUT_MS = 250

private class RingAnimation(
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f),
)

private class RippleInstance(
    val id: Long,
    val origin: Offset,
    val isForward: Boolean,
    val rings: List<RingAnimation>,
    var job: Job? = null,
)

/**
 * DoubleTapSeekOverlay draws water ripples on the full player artwork area
 * and manages YouTube-style accumulation for backward/forward seeking.
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
) {
    if (!enabled) return

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

    // ── Accumulation State ─────────────────────────────────────────────────────
    var isAccumulating by remember { mutableStateOf(false) }
    var accumulatedSeconds by remember { mutableIntStateOf(0) }
    var activeIsForward by remember { mutableStateOf(true) }
    var lastTapTimestampMs by remember { mutableLongStateOf(0L) }
    var targetSeekPositionMs by remember { mutableLongStateOf(-1L) }

    val labelAlpha = remember { Animatable(0f) }
    var labelFadeJob by remember { mutableStateOf<Job?>(null) }
    var accumulationTimerJob by remember { mutableStateOf<Job?>(null) }

    // ── Ripple Pool (Up to 3 active ripples at once) ───────────────────────────
    val activeRipples = remember { mutableStateListOf<RippleInstance>() }
    var rippleIdCounter by remember { mutableLongStateOf(0L) }

    val startStrokePx = with(density) { STROKE_WIDTH_START_DP.dp.toPx() }
    val endStrokePx = with(density) { STROKE_WIDTH_END_DP.dp.toPx() }

    Box(
        modifier = modifier
            .pointerInput(enabled, durationMs, seekStepSeconds) {
                if (durationMs <= 0L) return@pointerInput

                detectTapGestures(
                    onDoubleTap = { offset ->
                        val isRightSide = offset.x >= (size.width / 2f)
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

                        // Light haptic tick
                        haptics.segmentTick()

                        // Calculate and apply immediate seek
                        val basePos = if (targetSeekPositionMs >= 0L) targetSeekPositionMs else positionFlow.value
                        val stepMs = seekStepSeconds * 1000L
                        val deltaMs = if (activeIsForward) stepMs else -stepMs
                        val newPos = (basePos + deltaMs).coerceIn(0L, durationMs)
                        targetSeekPositionMs = newPos
                        onSeek(newPos.toFloat())

                        // Spawn ripples
                        val ringCount = when {
                            animationsDisabled || animatorScale == 0f -> 0
                            isPowerSaveMode -> MAX_RINGS_BATTERY_SAVER
                            else -> MAX_RINGS_NORMAL
                        }

                        if (ringCount > 0) {
                            val rings = List(ringCount) { RingAnimation() }
                            val ripple = RippleInstance(
                                id = ++rippleIdCounter,
                                origin = offset,
                                isForward = activeIsForward,
                                rings = rings
                            )

                            if (activeRipples.size >= MAX_ACTIVE_RIPPLES) {
                                val oldest = activeRipples.removeAt(0)
                                oldest.job?.cancel()
                            }
                            activeRipples.add(ripple)

                            ripple.job = coroutineScope.launch {
                                val jobs = rings.mapIndexed { index, ring ->
                                    launch {
                                        if (index > 0) {
                                            delay(index * RING_STAGGER_MS)
                                        }
                                        ring.progress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(
                                                durationMillis = RIPPLE_DURATION_MS,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    }
                                }
                                jobs.joinAll()
                                activeRipples.remove(ripple)
                            }
                        }

                        // Animate label and start accumulation window timer
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
                    },
                    onTap = { offset ->
                        val now = System.currentTimeMillis()
                        val isWindowActive = isAccumulating && (now - lastTapTimestampMs <= ACCUMULATION_WINDOW_MS)

                        // Outside active window, single tap does nothing
                        if (!isWindowActive) return@detectTapGestures

                        val isRightSide = offset.x >= (size.width / 2f)
                        lastTapTimestampMs = now

                        val isSameDirection = (isRightSide == activeIsForward)
                        if (!isSameDirection) {
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

                        val ringCount = when {
                            animationsDisabled || animatorScale == 0f -> 0
                            isPowerSaveMode -> MAX_RINGS_BATTERY_SAVER
                            else -> MAX_RINGS_NORMAL
                        }

                        if (ringCount > 0) {
                            val rings = List(ringCount) { RingAnimation() }
                            val ripple = RippleInstance(
                                id = ++rippleIdCounter,
                                origin = offset,
                                isForward = activeIsForward,
                                rings = rings
                            )

                            if (activeRipples.size >= MAX_ACTIVE_RIPPLES) {
                                val oldest = activeRipples.removeAt(0)
                                oldest.job?.cancel()
                            }
                            activeRipples.add(ripple)

                            ripple.job = coroutineScope.launch {
                                val jobs = rings.mapIndexed { index, ring ->
                                    launch {
                                        if (index > 0) {
                                            delay(index * RING_STAGGER_MS)
                                        }
                                        ring.progress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(
                                                durationMillis = RIPPLE_DURATION_MS,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    }
                                }
                                jobs.joinAll()
                                activeRipples.remove(ripple)
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
                )
            }
    ) {
        // ── Canvas: Zero Recomposition Draw Phase ─────────────────────────────
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

            val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(canvasWidth)
            val maxRadius = screenWidth * RADIUS_SCREEN_WIDTH_FRACTION
            val maxTravel = screenWidth * TRAVEL_SCREEN_WIDTH_FRACTION

            // 1. Draw animated water ripple rings
            for (ripple in activeRipples) {
                val directionX = if (ripple.isForward) 1f else -1f
                for (ring in ripple.rings) {
                    val p = ring.progress.value // Read Animatable in DRAW phase
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

            // 2. Draw running total label with three chevrons
            val currentLabelAlpha = labelAlpha.value // Read Animatable in DRAW phase
            if (currentLabelAlpha > 0.01f && accumulatedSeconds > 0) {
                val text = if (activeIsForward) {
                    "+$accumulatedSeconds s >>>"
                } else {
                    "<<< -$accumulatedSeconds s"
                }

                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = TextStyle(
                        color = Color.White.copy(alpha = currentLabelAlpha),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.65f * currentLabelAlpha),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    )
                )

                val textWidth = textLayoutResult.size.width.toFloat()
                val textHeight = textLayoutResult.size.height.toFloat()

                val targetCenterX = if (activeIsForward) canvasWidth * 0.75f else canvasWidth * 0.25f
                val targetCenterY = canvasHeight * 0.5f

                val textLeft = targetCenterX - (textWidth / 2f)
                val textTop = targetCenterY - (textHeight / 2f)

                val pillPadH = 14.dp.toPx()
                val pillPadV = 7.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.42f * currentLabelAlpha),
                    topLeft = Offset(textLeft - pillPadH, textTop - pillPadV),
                    size = Size(textWidth + pillPadH * 2f, textHeight + pillPadV * 2f),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(textLeft, textTop)
                )
            }
        }
    }
}
