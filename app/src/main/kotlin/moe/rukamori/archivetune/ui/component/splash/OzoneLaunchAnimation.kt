package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.appicon.OzoneLogo
import moe.rukamori.archivetune.appicon.rememberCurrentAppIcon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * =======================================================================
 * OZONE PLAYER - LAUNCH ANIMATION CONFIGURATION
 * All timing and visual parameters can be tweaked below.
 * =======================================================================
 */
object OzoneLaunchConfig {
    // Phase Durations (in milliseconds)
    const val PHASE1_ASSEMBLE_MS = 1000f   // Phase 1: Wave wipes in from left to right
    const val PHASE2_HOLD_MS = 300f        // Phase 2: Fully assembled logo with soft glow pulse
    const val PHASE3_TO_STRING_MS = 450f   // Phase 3: Logo collapses into horizontal glowing string
    const val PHASE4_DROP_MS = 300f        // Phase 4: White droplet falls from top to center
    const val PHASE5_REVEAL_MS = 750f      // Phase 5: Ripple wave + expanding circular reveal

    // Phase End Timestamps (Cumulative ms)
    const val T1_ASSEMBLE_END = PHASE1_ASSEMBLE_MS                          // 1000ms
    const val T2_HOLD_END = T1_ASSEMBLE_END + PHASE2_HOLD_MS                // 1300ms
    const val T3_STRING_END = T2_HOLD_END + PHASE3_TO_STRING_MS             // 1750ms
    const val T4_DROP_END = T3_STRING_END + PHASE4_DROP_MS                  // 2050ms
    const val T5_TOTAL_END = T4_DROP_END + PHASE5_REVEAL_MS                 // 2800ms

    // Visual Sizing & Physics
    const val LOGO_SCALE_OF_SCREEN = 0.52f  // Assembled logo size relative to min(width, height)
    const val STRING_STROKE_DP = 2.5f       // String stroke thickness
    const val STRING_GLOW_DP = 7.0f         // String outer glow thickness
    const val DROPLET_RADIUS_DP = 3.5f      // Droplet radius
    const val WAVE_MAX_AMP_DP = 18f         // Ripple wave peak amplitude
    const val WAVE_LENGTH_DP = 68f          // Ripple wavelength
    const val WAVE_SPEED = 1400f            // Wave front speed in pixels/sec
}

object OzoneLaunchState {
    /**
     * Process-level flag ensuring animation plays strictly on COLD START,
     * never on screen rotation or returning from background.
     */
    var hasPlayedThisProcess: Boolean = false
}

@Composable
fun OzoneLaunchAnimation(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    if (OzoneLaunchState.hasPlayedThisProcess) {
        return
    }

    var isDismissed by rememberSaveable { mutableStateOf(false) }
    if (isDismissed) {
        return
    }

    val currentVariant = rememberCurrentAppIcon()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    fun finishAnimation() {
        OzoneLaunchState.hasPlayedThisProcess = true
        isDismissed = true
        onDismiss()
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = OzoneLaunchConfig.T5_TOTAL_END.toInt(),
                easing = LinearEasing,
            ),
        )
        finishAnimation()
    }

    var screenWidth by remember { mutableFloatStateOf(0f) }
    var screenHeight by remember { mutableFloatStateOf(0f) }

    val revealPath = remember { Path() }
    val wavePath = remember { Path() }

    val t = progress.value * OzoneLaunchConfig.T5_TOTAL_END

    // Logo transformation based on phase
    val revealProgress = if (t <= OzoneLaunchConfig.T1_ASSEMBLE_END) {
        (t / OzoneLaunchConfig.T1_ASSEMBLE_END).coerceIn(0f, 1f)
    } else {
        1f
    }

    val (logoScale, logoAlpha) = when {
        t <= OzoneLaunchConfig.T2_HOLD_END -> 1f to 1f
        t <= OzoneLaunchConfig.T3_STRING_END -> {
            val p3 = ((t - OzoneLaunchConfig.T2_HOLD_END) / OzoneLaunchConfig.PHASE3_TO_STRING_MS).coerceIn(0f, 1f)
            val ease3 = 1f - (1f - p3) * (1f - p3)
            val scale = (1f - ease3).coerceIn(0f, 1f)
            scale to scale
        }
        else -> 0f to 0f
    }

    val logoSizePx = if (screenWidth > 0f && screenHeight > 0f) {
        min(screenWidth, screenHeight) * OzoneLaunchConfig.LOGO_SCALE_OF_SCREEN
    } else {
        0f
    }
    val logoSizeDp = with(density) { logoSizePx.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                screenWidth = size.width.toFloat()
                screenHeight = size.height.toFloat()
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    coroutineScope.launch {
                        progress.snapTo(1f)
                        finishAnimation()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val cx = w / 2f
            val cy = h / 2f

            // Background handling & Phase 5 Reveal
            if (t < OzoneLaunchConfig.T4_DROP_END) {
                drawRect(Color.Black)
            } else {
                // Phase 5: Circular Reveal Mask
                val p5 = ((t - OzoneLaunchConfig.T4_DROP_END) / OzoneLaunchConfig.PHASE5_REVEAL_MS).coerceIn(0f, 1f)
                val easeReveal = if (p5 < 0.5f) 4f * p5 * p5 * p5 else 1f - (-2f * p5 + 2f).pow(3) / 2f
                val maxRadius = sqrt(cx * cx + cy * cy) + 40.dp.toPx()
                val currentRadius = maxRadius * easeReveal
                val dt5 = (t - OzoneLaunchConfig.T4_DROP_END) / 1000f

                revealPath.reset()
                revealPath.addOval(Rect(center = Offset(cx, cy), radius = currentRadius))

                clipPath(path = revealPath, clipOp = ClipOp.Difference) {
                    drawRect(Color.Black)

                    // Damped ripple wave travelling outward from center
                    wavePath.reset()
                    wavePath.moveTo(0f, cy)

                    val maxAmp = OzoneLaunchConfig.WAVE_MAX_AMP_DP.dp.toPx() * exp(-4.2f * dt5) * (1f - p5)
                    val waveLen = OzoneLaunchConfig.WAVE_LENGTH_DP.dp.toPx()
                    val waveSpeedPx = OzoneLaunchConfig.WAVE_SPEED.dp.toPx()
                    val frontX = waveSpeedPx * dt5

                    val step = 8.dp.toPx().coerceAtLeast(12f)
                    var currX = 0f
                    var prevX = 0f
                    var prevY = cy

                    while (currX < w) {
                        currX += step
                        if (currX > w) currX = w
                        val dist = abs(currX - cx)
                        val envelope = if (dist <= frontX) {
                            ((frontX - dist) / (waveLen * 0.8f)).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val waveY = cy - maxAmp * envelope * sin((dist - frontX) / waveLen * 2f * PI.toFloat())
                        wavePath.quadraticTo(prevX, prevY, (prevX + currX) / 2f, (prevY + waveY) / 2f)
                        prevX = currX
                        prevY = waveY
                    }
                    wavePath.lineTo(w, cy)

                    drawPath(
                        path = wavePath,
                        color = Color.White.copy(alpha = 0.28f * (1f - p5)),
                        style = Stroke(
                            width = OzoneLaunchConfig.STRING_GLOW_DP.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                    drawPath(
                        path = wavePath,
                        color = Color.White.copy(alpha = 0.9f * (1f - p5)),
                        style = Stroke(
                            width = OzoneLaunchConfig.STRING_STROKE_DP.dp.toPx(),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }

            // Phase 2: HOLD glow pulse (1000..1300ms)
            if (t in OzoneLaunchConfig.T1_ASSEMBLE_END..OzoneLaunchConfig.T2_HOLD_END) {
                val p2 = ((t - OzoneLaunchConfig.T1_ASSEMBLE_END) / OzoneLaunchConfig.PHASE2_HOLD_MS).coerceIn(0f, 1f)
                val glowAlpha = (sin(p2 * PI.toFloat()) * 0.45f).coerceIn(0f, 1f)
                if (glowAlpha > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = glowAlpha), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = logoSizePx * 0.7f,
                        ),
                        radius = logoSizePx * 0.7f,
                        center = Offset(cx, cy),
                    )
                }
            }

            // Phase 3: TO STRING (1300..1750ms)
            if (t in OzoneLaunchConfig.T2_HOLD_END..OzoneLaunchConfig.T3_STRING_END) {
                val p3 = ((t - OzoneLaunchConfig.T2_HOLD_END) / OzoneLaunchConfig.PHASE3_TO_STRING_MS).coerceIn(0f, 1f)
                val ease3 = 1f - (1f - p3) * (1f - p3)
                val halfW = (w / 2f) * ease3

                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(cx - halfW, cy),
                    end = Offset(cx + halfW, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_GLOW_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cx - halfW, cy),
                    end = Offset(cx + halfW, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_STROKE_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Phase 4: DROP (1750..2050ms)
            if (t in OzoneLaunchConfig.T3_STRING_END..OzoneLaunchConfig.T4_DROP_END) {
                // Background string stays glowing
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_GLOW_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_STROKE_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Falling droplet
                val p4 = ((t - OzoneLaunchConfig.T3_STRING_END) / OzoneLaunchConfig.PHASE4_DROP_MS).coerceIn(0f, 1f)
                val easeDrop = p4 * p4
                val dropY = cy * easeDrop
                val stretch = 1f + 0.8f * (1f - (p4 - 1f) * (p4 - 1f))
                val dropRadiusX = OzoneLaunchConfig.DROPLET_RADIUS_DP.dp.toPx()
                val dropRadiusY = dropRadiusX * stretch

                drawOval(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(cx - dropRadiusX * 1.8f, dropY - dropRadiusY * 1.8f),
                    size = androidx.compose.ui.geometry.Size(dropRadiusX * 3.6f, dropRadiusY * 3.6f),
                )
                drawOval(
                    color = Color.White,
                    topLeft = Offset(cx - dropRadiusX, dropY - dropRadiusY),
                    size = androidx.compose.ui.geometry.Size(dropRadiusX * 2f, dropRadiusY * 2f),
                )
            }
        }

        // Overlay the selected icon logo in Phase 1, 2, 3
        if (t <= OzoneLaunchConfig.T3_STRING_END && logoAlpha > 0.01f && logoSizeDp > 0.dp) {
            OzoneLogo(
                variant = currentVariant,
                modifier = Modifier
                    .size(logoSizeDp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    },
                revealProgress = revealProgress,
            )
        }
    }
}
