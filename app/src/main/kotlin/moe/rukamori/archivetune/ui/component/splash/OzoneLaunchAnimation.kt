package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * =======================================================================
 * OZONE PLAYER - LAUNCH ANIMATION CONFIGURATION
 * All timing, grid counts, and visual parameters can be tweaked below.
 * =======================================================================
 */
object OzoneLaunchConfig {
    // Phase Durations (in milliseconds)
    const val PHASE1_ASSEMBLE_MS = 1000f   // Phase 1: Tiles fly in from outside & assemble
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

    // Tile Grid Setup (10x10 = 100 tiles max)
    const val GRID_COLS = 10
    const val GRID_ROWS = 10
    const val RANDOM_SEED = 42L

    // Visual Sizing & Physics
    const val LOGO_SCALE_OF_SCREEN = 0.62f  // Assembled logo size relative to min(width, height)
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

private class TileData(
    val srcX: Int,
    val srcY: Int,
    val srcW: Int,
    val srcH: Int,
    val targetX: Float,
    val targetY: Float,
    val startX: Float,
    val startY: Float,
    val startRotation: Float,
    val startScale: Float,
    val delayFraction: Float,
)

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
                easing = LinearEasing
            )
        )
        finishAnimation()
    }

    val bitmap = ImageBitmap.imageResource(id = R.drawable.ozone_logo)
    var screenWidth by remember { mutableFloatStateOf(0f) }
    var screenHeight by remember { mutableFloatStateOf(0f) }

    val tiles = remember(screenWidth, screenHeight, bitmap) {
        if (screenWidth <= 0f || screenHeight <= 0f) return@remember emptyList<TileData>()
        val logoSize = min(screenWidth, screenHeight) * OzoneLaunchConfig.LOGO_SCALE_OF_SCREEN
        val logoLeft = (screenWidth - logoSize) / 2f
        val logoTop = (screenHeight - logoSize) / 2f
        val tileDstW = logoSize / OzoneLaunchConfig.GRID_COLS
        val tileDstH = logoSize / OzoneLaunchConfig.GRID_ROWS
        val tileSrcW = bitmap.width / OzoneLaunchConfig.GRID_COLS
        val tileSrcH = bitmap.height / OzoneLaunchConfig.GRID_ROWS

        val rng = Random(OzoneLaunchConfig.RANDOM_SEED)
        val maxDiag = sqrt(screenWidth * screenWidth + screenHeight * screenHeight)

        val list = ArrayList<TileData>(OzoneLaunchConfig.GRID_COLS * OzoneLaunchConfig.GRID_ROWS)
        for (r in 0 until OzoneLaunchConfig.GRID_ROWS) {
            for (c in 0 until OzoneLaunchConfig.GRID_COLS) {
                val srcX = c * tileSrcW
                val srcY = r * tileSrcH
                val targetX = logoLeft + c * tileDstW
                val targetY = logoTop + r * tileDstH

                val dc = c - (OzoneLaunchConfig.GRID_COLS - 1) / 2f
                val dr = r - (OzoneLaunchConfig.GRID_ROWS - 1) / 2f
                val dist = sqrt(dc * dc + dr * dr)
                val maxGridDist = sqrt(4.5f * 4.5f + 4.5f * 4.5f)
                val normDist = (dist / maxGridDist).coerceIn(0f, 1f)
                val delayFrac = normDist * 0.32f

                val angle = rng.nextFloat() * 2f * PI.toFloat()
                val flyDist = maxDiag * (0.6f + 0.4f * rng.nextFloat())
                val startX = screenWidth / 2f + flyDist * cos(angle)
                val startY = screenHeight / 2f + flyDist * sin(angle)
                val startRot = (rng.nextFloat() - 0.5f) * 360f
                val startScale = 0.15f + 0.15f * rng.nextFloat()

                list.add(
                    TileData(
                        srcX = srcX,
                        srcY = srcY,
                        srcW = tileSrcW,
                        srcH = tileSrcH,
                        targetX = targetX,
                        targetY = targetY,
                        startX = startX,
                        startY = startY,
                        startRotation = startRot,
                        startScale = startScale,
                        delayFraction = delayFrac
                    )
                )
            }
        }
        list
    }

    val revealPath = remember { Path() }
    val wavePath = remember { Path() }

    Canvas(
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
            }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cx = w / 2f
        val cy = h / 2f
        val t = progress.value * OzoneLaunchConfig.T5_TOTAL_END
        val logoSize = min(w, h) * OzoneLaunchConfig.LOGO_SCALE_OF_SCREEN
        val logoLeft = (w - logoSize) / 2f
        val logoTop = (h - logoSize) / 2f
        val tileDstW = logoSize / OzoneLaunchConfig.GRID_COLS
        val tileDstH = logoSize / OzoneLaunchConfig.GRID_ROWS

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
                    val angle = (dist / waveLen) * 2f * PI.toFloat() - dt5 * 18f
                    val y = cy + maxAmp * envelope * sin(angle)
                    val midX = (prevX + currX) / 2f
                    val midY = (prevY + y) / 2f
                    wavePath.quadraticBezierTo(prevX, prevY, midX, midY)
                    prevX = currX
                    prevY = y
                }
                wavePath.lineTo(w, cy)

                drawPath(
                    path = wavePath,
                    color = Color.White.copy(alpha = (1f - p5).coerceIn(0f, 1f)),
                    style = Stroke(width = OzoneLaunchConfig.STRING_STROKE_DP.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Trailing white rings at the edge of the reveal circle
            if (currentRadius > 0f) {
                val ringAlpha = (1f - p5).coerceIn(0f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = ringAlpha * 0.85f),
                    radius = currentRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                val ring2Radius = currentRadius - 14.dp.toPx()
                if (ring2Radius > 0f) {
                    drawCircle(
                        color = Color.White.copy(alpha = ringAlpha * 0.40f),
                        radius = ring2Radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }

        // Draw foreground elements based on phase
        when {
            // Phase 1: ASSEMBLE (0..1000ms)
            t <= OzoneLaunchConfig.T1_ASSEMBLE_END -> {
                for (tile in tiles) {
                    val p = ((t / OzoneLaunchConfig.T1_ASSEMBLE_END - tile.delayFraction) / (1f - 0.32f)).coerceIn(0f, 1f)
                    val ease = 1f - (1f - p).pow(3)
                    val curX = tile.startX + (tile.targetX - tile.startX) * ease
                    val curY = tile.startY + (tile.targetY - tile.startY) * ease
                    val curRot = tile.startRotation * (1f - ease)
                    val curScale = tile.startScale + (1f - tile.startScale) * ease
                    val curAlpha = ease.coerceIn(0f, 1f)

                    if (curAlpha > 0.01f) {
                        withTransform({
                            translate(curX + tileDstW / 2f, curY + tileDstH / 2f)
                            rotate(curRot)
                            scale(curScale, curScale)
                        }) {
                            drawImage(
                                image = bitmap,
                                srcOffset = IntOffset(tile.srcX, tile.srcY),
                                srcSize = IntSize(tile.srcW, tile.srcH),
                                dstOffset = IntOffset((-tileDstW / 2f).roundToInt(), (-tileDstH / 2f).roundToInt()),
                                dstSize = IntSize(tileDstW.roundToInt(), tileDstH.roundToInt()),
                                alpha = curAlpha
                            )
                        }
                    }
                }
            }

            // Phase 2: HOLD (1000..1300ms)
            t <= OzoneLaunchConfig.T2_HOLD_END -> {
                val p2 = ((t - OzoneLaunchConfig.T1_ASSEMBLE_END) / OzoneLaunchConfig.PHASE2_HOLD_MS).coerceIn(0f, 1f)
                val glowAlpha = (sin(p2 * PI.toFloat()) * 0.45f).coerceIn(0f, 1f)
                if (glowAlpha > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = glowAlpha), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = logoSize * 0.7f
                        ),
                        radius = logoSize * 0.7f,
                        center = Offset(cx, cy)
                    )
                }
                drawImage(
                    image = bitmap,
                    dstOffset = IntOffset(logoLeft.roundToInt(), logoTop.roundToInt()),
                    dstSize = IntSize(logoSize.roundToInt(), logoSize.roundToInt())
                )
            }

            // Phase 3: TO STRING (1300..1750ms)
            t <= OzoneLaunchConfig.T3_STRING_END -> {
                val p3 = ((t - OzoneLaunchConfig.T2_HOLD_END) / OzoneLaunchConfig.PHASE3_TO_STRING_MS).coerceIn(0f, 1f)
                val ease3 = 1f - (1f - p3) * (1f - p3)
                val logoScale = (1f - ease3).coerceIn(0f, 1f)
                val logoAlpha = (1f - ease3).coerceIn(0f, 1f)

                if (logoAlpha > 0.01f) {
                    withTransform({
                        translate(cx, cy)
                        scale(logoScale, logoScale)
                    }) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset((-logoSize / 2f).roundToInt(), (-logoSize / 2f).roundToInt()),
                            dstSize = IntSize(logoSize.roundToInt(), logoSize.roundToInt()),
                            alpha = logoAlpha
                        )
                    }
                }

                val halfW = (w / 2f) * ease3
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(cx - halfW, cy),
                    end = Offset(cx + halfW, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_GLOW_DP.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cx - halfW, cy),
                    end = Offset(cx + halfW, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_STROKE_DP.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Phase 4: DROP (1750..2050ms)
            t <= OzoneLaunchConfig.T4_DROP_END -> {
                // Background string stays glowing
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_GLOW_DP.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(0f, cy),
                    end = Offset(w, cy),
                    strokeWidth = OzoneLaunchConfig.STRING_STROKE_DP.dp.toPx(),
                    cap = StrokeCap.Round
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
                    size = androidx.compose.ui.geometry.Size(dropRadiusX * 3.6f, dropRadiusY * 3.6f)
                )
                drawOval(
                    color = Color.White,
                    topLeft = Offset(cx - dropRadiusX, dropY - dropRadiusY),
                    size = androidx.compose.ui.geometry.Size(dropRadiusX * 2f, dropRadiusY * 2f)
                )
            }
        }
    }
}
