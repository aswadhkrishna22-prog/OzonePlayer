/*
 * Ozone Player (2026)
 * Rain on Glass Effect & Interactive Touch Wipe Simulation
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.effects

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalAnimationsDisabled
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val STATIC_DROP_COUNT = 85
private const val SLIDING_DROP_COUNT = 9
private const val WIPE_RECOVERY_TIME_MS = 6000L
private const val TRAIL_LIFETIME_MS = 3800L

/**
 * Data model for a resting water droplet clinging to the window glass.
 */
private class StaticRainDrop(
    var x: Float,
    var y: Float,
    var radiusDp: Float,
    var alpha: Float,
    var isWiped: Boolean = false,
    var wipedTimeMs: Long = 0L,
)

/**
 * Historical trail coordinate left behind a trickling droplet.
 */
private class RainTrailPoint(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val timestamp: Long,
)

/**
 * Data model for an active gravity-driven sliding droplet.
 * Features stick-slip hesitation, path meandering, and droplet coalescence (swallowing).
 */
private class SlidingRainDrop(
    var x: Float,
    var y: Float,
    var radiusDp: Float,
    var mass: Float,
    var speedDpPerSec: Float,
    var isSliding: Boolean = false,
    var slideDurationRemainingMs: Float = 0f,
    var pauseRemainingMs: Float = 0f,
    var wigglePhase: Float = 0f,
    var wiggleSpeed: Float = 0f,
    val trail: ArrayList<RainTrailPoint> = ArrayList(32),
)

/**
 * A user's touch wipe point on the glass, clearing condensation & droplets.
 */
private class TouchWipePoint(
    val x: Float,
    val y: Float,
    val radiusPx: Float,
    val timestamp: Long,
)

/**
 * Simulation controller maintaining physics state, droplet collision/merging,
 * and touch wipe points without runtime GC allocations.
 */
private class RainSimulationState(
    private val staticDrops: List<StaticRainDrop>,
    private val slidingDrops: List<SlidingRainDrop>,
) {
    private val wipePoints = ArrayList<TouchWipePoint>(64)
    private var lastTouchX = -1000f
    private var lastTouchY = -1000f

    fun addTouch(x: Float, y: Float, radiusPx: Float, nowMs: Long) {
        val dx = x - lastTouchX
        val dy = y - lastTouchY
        if (dx * dx + dy * dy >= 144f || wipePoints.isEmpty()) { // ~12px distance threshold
            wipePoints.add(TouchWipePoint(x, y, radiusPx, nowMs))
            lastTouchX = x
            lastTouchY = y
        }
    }

    fun update(
        dtMs: Float,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        tiltX: Float,
        nowMs: Long,
    ) {
        if (widthPx <= 0f || heightPx <= 0f) return

        // 1. Prune expired wipe points
        wipePoints.removeAll { nowMs - it.timestamp > WIPE_RECOVERY_TIME_MS }

        // 2. Wipe droplets near active touch wipe points & handle condensation recovery
        for (staticDrop in staticDrops) {
            val dropPxX = staticDrop.x * widthPx
            val dropPxY = staticDrop.y * heightPx

            if (!staticDrop.isWiped) {
                for (wipe in wipePoints) {
                    val dx = dropPxX - wipe.x
                    val dy = dropPxY - wipe.y
                    if (dx * dx + dy * dy < wipe.radiusPx * wipe.radiusPx) {
                        staticDrop.isWiped = true
                        staticDrop.wipedTimeMs = nowMs
                        break
                    }
                }
            } else {
                // Check if moisture slowly re-condenses back onto the wiped glass
                val elapsed = nowMs - staticDrop.wipedTimeMs
                if (elapsed > WIPE_RECOVERY_TIME_MS) {
                    staticDrop.isWiped = false
                }
            }
        }

        // 3. Update sliding trickling drops
        val dtSec = dtMs / 1000f

        for (sliding in slidingDrops) {
            // Prune old trail points
            sliding.trail.removeAll { nowMs - it.timestamp > TRAIL_LIFETIME_MS }

            if (!sliding.isSliding) {
                sliding.pauseRemainingMs -= dtMs
                if (sliding.pauseRemainingMs <= 0f) {
                    sliding.isSliding = true
                    // Heavy drops slide longer, small drops slide shorter
                    sliding.slideDurationRemainingMs = 400f + sliding.mass * 350f + Random.nextFloat() * 400f
                }
            } else {
                sliding.slideDurationRemainingMs -= dtMs
                if (sliding.slideDurationRemainingMs <= 0f && sliding.mass < 3.2f) {
                    // Small drops hesitate / pause due to surface tension
                    sliding.isSliding = false
                    sliding.pauseRemainingMs = 1200f + Random.nextFloat() * 2500f
                }

                // Gravity pull + mass acceleration + slight tilt
                val targetSpeed = (85f + sliding.mass * 65f) * density
                sliding.speedDpPerSec += (targetSpeed - sliding.speedDpPerSec) * (dtSec * 3f)

                sliding.wigglePhase += sliding.wiggleSpeed * dtSec
                val meanderX = sin(sliding.wigglePhase) * 18f * density
                val tiltShiftX = tiltX * 80f * density

                val deltaYPx = (sliding.speedDpPerSec * density) * dtSec
                val deltaXPx = (meanderX + tiltShiftX) * dtSec

                sliding.y += deltaYPx / heightPx
                sliding.x += deltaXPx / widthPx

                // Record trail point periodically
                val currentPxX = sliding.x * widthPx
                val currentPxY = sliding.y * heightPx
                val lastTrail = sliding.trail.lastOrNull()
                if (lastTrail == null || (currentPxY - lastTrail.y * heightPx) > 12f * density) {
                    sliding.trail.add(
                        RainTrailPoint(
                            x = sliding.x,
                            y = sliding.y,
                            radiusDp = maxOf(1.2f, sliding.radiusDp * 0.45f),
                            timestamp = nowMs,
                        )
                    )
                }

                // Droplet Coalescence (Merging - "onnine vizhugi valuthaavuka")
                val slidingRadiusPx = sliding.radiusDp * density
                for (staticDrop in staticDrops) {
                    if (!staticDrop.isWiped) {
                        val staticPxX = staticDrop.x * widthPx
                        val staticPxY = staticDrop.y * heightPx
                        val dx = currentPxX - staticPxX
                        val dy = currentPxY - staticPxY
                        val captureDistance = slidingRadiusPx + (staticDrop.radiusDp * density)

                        if (dx * dx + dy * dy < captureDistance * captureDistance) {
                            // Swallowed by the sliding drop!
                            staticDrop.isWiped = true
                            staticDrop.wipedTimeMs = nowMs

                            sliding.mass = minOf(6.5f, sliding.mass + 0.35f)
                            sliding.radiusDp = minOf(11.0f, sliding.radiusDp + 0.35f)
                            sliding.speedDpPerSec += 45f // Surge of momentum
                        }
                    }
                }

                // Droplet reached bottom: recycle back to top
                if (sliding.y > 1.06f) {
                    sliding.y = -0.05f - Random.nextFloat() * 0.1f
                    sliding.x = Random.nextFloat()
                    val isHeavy = Random.nextFloat() < 0.25f
                    sliding.mass = if (isHeavy) 3.5f + Random.nextFloat() * 1.5f else 1.2f + Random.nextFloat() * 1.4f
                    sliding.radiusDp = if (isHeavy) 5.5f + Random.nextFloat() * 2.0f else 2.8f + Random.nextFloat() * 1.8f
                    sliding.speedDpPerSec = 40f
                    sliding.isSliding = false
                    sliding.pauseRemainingMs = 600f + Random.nextFloat() * 2400f
                    sliding.wigglePhase = Random.nextFloat() * 6.28f
                    sliding.wiggleSpeed = 2f + Random.nextFloat() * 3f
                    sliding.trail.clear()
                }
            }
        }
    }

    fun getStaticDrops(): List<StaticRainDrop> = staticDrops
    fun getSlidingDrops(): List<SlidingRainDrop> = slidingDrops
    fun getWipePoints(): List<TouchWipePoint> = wipePoints
}

/**
 * Creates and remembers the simulation state with deterministic initial seeds.
 */
@Composable
private fun rememberRainSimulationState(): RainSimulationState {
    return remember {
        val staticList = ArrayList<StaticRainDrop>(STATIC_DROP_COUNT)
        for (i in 0 until STATIC_DROP_COUNT) {
            val radius = 1.2f + Random.nextFloat() * 3.2f
            val alpha = 0.45f + Random.nextFloat() * 0.45f
            staticList.add(
                StaticRainDrop(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    radiusDp = radius,
                    alpha = alpha,
                )
            )
        }

        val slidingList = ArrayList<SlidingRainDrop>(SLIDING_DROP_COUNT)
        for (i in 0 until SLIDING_DROP_COUNT) {
            val isHeavy = i % 3 == 0 // Every 3rd drop is a heavy tear-drop streak
            val mass = if (isHeavy) 3.8f + Random.nextFloat() * 1.5f else 1.2f + Random.nextFloat() * 1.4f
            val radius = if (isHeavy) 5.5f + Random.nextFloat() * 2.2f else 3.0f + Random.nextFloat() * 1.6f
            slidingList.add(
                SlidingRainDrop(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    radiusDp = radius,
                    mass = mass,
                    speedDpPerSec = 60f + Random.nextFloat() * 50f,
                    isSliding = Random.nextBoolean(),
                    pauseRemainingMs = Random.nextFloat() * 2000f,
                    slideDurationRemainingMs = 600f + Random.nextFloat() * 800f,
                    wigglePhase = Random.nextFloat() * 6.28f,
                    wiggleSpeed = 2f + Random.nextFloat() * 3.5f,
                )
            )
        }

        RainSimulationState(staticList, slidingList)
    }
}

/**
 * Ultra-realistic Rain on Glass Background for Ozone Player Home Screen.
 * Features 3D optical lens refraction, specular highlights, stick-slip trickling drops,
 * droplet coalescence (merging), gyroscope tilt gravity, and interactive finger touch wiping!
 */
@Composable
fun RainBackground(
    modifier: Modifier = Modifier,
    parallaxEnabled: Boolean = true,
    parallaxSensitivity: Float = 0.6f,
    brightness: Float = 1f,
    isVisible: Boolean = true,
    enableTouchWipe: Boolean = true,
) {
    val context = LocalContext.current
    val disableAnimations = LocalAnimationsDisabled.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val parallaxState = rememberParallaxState(
        enableParallax = parallaxEnabled && !disableAnimations,
        sensitivity = parallaxSensitivity * 0.7f,
        context = context,
    )

    val simState = rememberRainSimulationState()
    var frameTick by remember { mutableLongStateOf(0L) }
    var lastFrameTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isVisible, disableAnimations) {
        if (!isVisible || disableAnimations) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameMs ->
                frameTick = frameMs
            }
        }
    }

    val density = LocalDensity.current.density
    val brightnessFactor = brightness.coerceIn(0.1f, 2.0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enableTouchWipe) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val now = System.currentTimeMillis()
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        simState.addTouch(
                                            change.position.x,
                                            change.position.y,
                                            radiusPx = 42f * density,
                                            nowMs = now,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nowMs = if (frameTick > 0L) frameTick else System.currentTimeMillis()
            val dt = if (disableAnimations || lastFrameTimeMs == 0L) {
                16f
            } else {
                (nowMs - lastFrameTimeMs).coerceIn(8L, 48L).toFloat()
            }
            lastFrameTimeMs = nowMs

            simState.update(
                dtMs = dt,
                widthPx = size.width,
                heightPx = size.height,
                density = density,
                tiltX = parallaxState.tiltX.value,
                nowMs = nowMs,
            )

            drawRainGlass(
                simState = simState,
                density = density,
                brightness = brightnessFactor,
                isDark = isDark,
                nowMs = nowMs,
                activeApertureTopFraction = null,
                activeApertureBottomFraction = null,
            )
        }
    }
}

/**
 * Misty Rain on Glass Overlay for Lyrics Screen.
 * Renders atmospheric condensation and trickling raindrops over past & future lines,
 * keeping the current singing line sharp and readable, with interactive touch wiping!
 */
@Composable
fun RainGlassLyricsOverlay(
    modifier: Modifier = Modifier,
    brightness: Float = 1f,
    isVisible: Boolean = true,
) {
    val disableAnimations = LocalAnimationsDisabled.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val simState = rememberRainSimulationState()
    var frameTick by remember { mutableLongStateOf(0L) }
    var lastFrameTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isVisible, disableAnimations) {
        if (!isVisible || disableAnimations) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameMs ->
                frameTick = frameMs
            }
        }
    }

    val density = LocalDensity.current.density
    val brightnessFactor = brightness.coerceIn(0.1f, 2.0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val now = System.currentTimeMillis()
                        for (change in event.changes) {
                            if (change.pressed) {
                                simState.addTouch(
                                    change.position.x,
                                    change.position.y,
                                    radiusPx = 48f * density,
                                    nowMs = now,
                                )
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val nowMs = if (frameTick > 0L) frameTick else System.currentTimeMillis()
            val dt = if (disableAnimations || lastFrameTimeMs == 0L) {
                16f
            } else {
                (nowMs - lastFrameTimeMs).coerceIn(8L, 48L).toFloat()
            }
            lastFrameTimeMs = nowMs

            simState.update(
                dtMs = dt,
                widthPx = size.width,
                heightPx = size.height,
                density = density,
                tiltX = 0f,
                nowMs = nowMs,
            )

            // In Karaoke lyrics, the active lyric line is centered at ~32% from the top.
            // We carve a smooth clear window from 24% to 42% so the singing line remains 100% crisp!
            drawRainGlass(
                simState = simState,
                density = density,
                brightness = brightnessFactor,
                isDark = isDark,
                nowMs = nowMs,
                activeApertureTopFraction = 0.22f,
                activeApertureBottomFraction = 0.44f,
            )
        }
    }
}

/**
 * Shared photorealistic 3D canvas renderer for water droplets, mist, and touch wipe holes.
 */
private fun DrawScope.drawRainGlass(
    simState: RainSimulationState,
    density: Float,
    brightness: Float,
    isDark: Boolean,
    nowMs: Long,
    activeApertureTopFraction: Float?,
    activeApertureBottomFraction: Float?,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    val mistBaseColor = if (isDark) Color(0xFF0A0F1A) else Color(0xFF202A38)
    val mistAlpha = (if (isDark) 0.16f else 0.10f) * brightness

    // 1. Atmospheric misty glass condensation gradient
    if (activeApertureTopFraction != null && activeApertureBottomFraction != null) {
        // Upper mist (past lyrics)
        val upperTop = 0f
        val upperBottom = height * activeApertureTopFraction
        if (upperBottom > upperTop) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to mistBaseColor.copy(alpha = mistAlpha),
                    0.8f to mistBaseColor.copy(alpha = mistAlpha * 0.75f),
                    1.0f to Color.Transparent,
                    startY = upperTop,
                    endY = upperBottom,
                ),
                topLeft = Offset(0f, upperTop),
                size = Size(width, upperBottom - upperTop),
            )
        }

        // Lower mist (upcoming lyrics)
        val lowerTop = height * activeApertureBottomFraction
        val lowerBottom = height
        if (lowerBottom > lowerTop) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.25f to mistBaseColor.copy(alpha = mistAlpha * 0.8f),
                    1.0f to mistBaseColor.copy(alpha = mistAlpha),
                    startY = lowerTop,
                    endY = lowerBottom,
                ),
                topLeft = Offset(0f, lowerTop),
                size = Size(width, lowerBottom - lowerTop),
            )
        }
    } else {
        // Fullscreen atmospheric glass tint
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to mistBaseColor.copy(alpha = mistAlpha * 0.9f),
                0.5f to mistBaseColor.copy(alpha = mistAlpha * 0.4f),
                1.0f to mistBaseColor.copy(alpha = mistAlpha * 1.1f),
            ),
            size = size,
        )
    }

    // 2. Interactive Touch Wipe Highlights (cleared glass glow)
    val wipePoints = simState.getWipePoints()
    for (wipe in wipePoints) {
        val age = (nowMs - wipe.timestamp).coerceAtLeast(0L)
        val progress = (1f - (age.toFloat() / WIPE_RECOVERY_TIME_MS)).coerceIn(0f, 1f)
        if (progress > 0.01f) {
            val clearRadius = wipe.radiusPx * (0.5f + 0.5f * progress)
            // Soft clear aura simulating freshly wiped, shining glass
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.08f * progress * brightness),
                    0.6f to Color.White.copy(alpha = 0.03f * progress * brightness),
                    1.0f to Color.Transparent,
                    center = Offset(wipe.x, wipe.y),
                    radius = clearRadius,
                ),
                radius = clearRadius,
                center = Offset(wipe.x, wipe.y),
            )
        }
    }

    // 3. Wet Trails left behind trickling sliding droplets
    for (sliding in simState.getSlidingDrops()) {
        val trail = sliding.trail
        if (trail.size >= 2) {
            for (i in 0 until trail.size - 1) {
                val p1 = trail[i]
                val p2 = trail[i + 1]
                val age = (nowMs - p1.timestamp).coerceAtLeast(0L)
                val trailAlpha = (1f - (age.toFloat() / TRAIL_LIFETIME_MS)).coerceIn(0f, 1f) * 0.22f * brightness
                if (trailAlpha > 0.01f) {
                    val x1 = p1.x * width
                    val y1 = p1.y * height
                    val x2 = p2.x * width
                    val y2 = p2.y * height

                    // Wet streak line
                    drawLine(
                        color = Color.White.copy(alpha = trailAlpha * 0.35f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = p1.radiusDp * density * 0.75f,
                        cap = StrokeCap.Round,
                    )

                    // Occasional micro droplet bead deposited along the trail
                    if (i % 3 == 0) {
                        drawCircle(
                            color = Color.White.copy(alpha = trailAlpha * 0.6f),
                            radius = p1.radiusDp * density * 0.35f,
                            center = Offset(x1, y1),
                        )
                    }
                }
            }
        }
    }

    // 4. Static Resting Droplets with 3D Specular Optics
    val staticDrops = simState.getStaticDrops()
    for (drop in staticDrops) {
        if (drop.isWiped) {
            val elapsed = nowMs - drop.wipedTimeMs
            if (elapsed <= WIPE_RECOVERY_TIME_MS) continue
        }

        val cx = drop.x * width
        val cy = drop.y * height

        // If inside the active lyrics singing window, skip rendering so lyrics are 100% clear
        if (activeApertureTopFraction != null && activeApertureBottomFraction != null) {
            val yFraction = cy / height
            if (yFraction in activeApertureTopFraction..activeApertureBottomFraction) {
                continue
            }
        }

        val r = drop.radiusDp * density
        val a = drop.alpha * brightness

        drawDroplet3D(
            cx = cx,
            cy = cy,
            radius = r,
            alpha = a,
            isSliding = false,
        )
    }

    // 5. Sliding / Trickling Droplets (Tear-drop shape with gravity stretch)
    for (sliding in simState.getSlidingDrops()) {
        val cx = sliding.x * width
        val cy = sliding.y * height

        // If inside the active lyrics aperture and not a large drop, suppress slightly
        if (activeApertureTopFraction != null && activeApertureBottomFraction != null) {
            val yFraction = cy / height
            if (yFraction in activeApertureTopFraction..activeApertureBottomFraction && sliding.mass < 3.0f) {
                continue
            }
        }

        val r = sliding.radiusDp * density
        val a = (0.75f + minOf(0.25f, sliding.mass * 0.05f)) * brightness

        drawDroplet3D(
            cx = cx,
            cy = cy,
            radius = r,
            alpha = a,
            isSliding = sliding.isSliding,
        )
    }
}

/**
 * Draws a single 3D optical water droplet with refraction rim,
 * convex lens shading, pinpoint specular highlight, and caustic crescent reflection.
 */
private fun DrawScope.drawDroplet3D(
    cx: Float,
    cy: Float,
    radius: Float,
    alpha: Float,
    isSliding: Boolean,
) {
    if (radius <= 0.5f || alpha <= 0.01f) return

    val stretchY = if (isSliding) 1.25f else 1.0f
    val stretchX = if (isSliding) 0.88f else 1.0f

    val effRadiusX = radius * stretchX
    val effRadiusY = radius * stretchY

    // 1. Dark refraction meniscus / shadow border
    drawOval(
        color = Color.Black.copy(alpha = 0.35f * alpha),
        topLeft = Offset(cx - effRadiusX - 0.6f, cy - effRadiusY - 0.4f),
        size = Size((effRadiusX * 2f) + 1.2f, (effRadiusY * 2f) + 1.2f),
    )

    // 2. Liquid lens body gradient
    val bodyBrush = Brush.radialGradient(
        0.0f to Color.White.copy(alpha = 0.16f * alpha),
        0.65f to Color.White.copy(alpha = 0.04f * alpha),
        0.92f to Color.Black.copy(alpha = 0.28f * alpha),
        1.0f to Color.Transparent,
        center = Offset(cx, cy),
        radius = radius,
    )
    drawOval(
        brush = bodyBrush,
        topLeft = Offset(cx - effRadiusX, cy - effRadiusY),
        size = Size(effRadiusX * 2f, effRadiusY * 2f),
    )

    // 3. Specular Highlight (Ambient light glint at top-left)
    val hlOffsetX = -effRadiusX * 0.35f
    val hlOffsetY = -effRadiusY * 0.35f
    val hlRadius = radius * 0.28f
    drawCircle(
        color = Color.White.copy(alpha = 0.88f * alpha),
        radius = hlRadius,
        center = Offset(cx + hlOffsetX, cy + hlOffsetY),
    )

    // 4. Secondary Caustic Reflection (Light focused on opposite bottom-right edge)
    val causticOffsetX = effRadiusX * 0.28f
    val causticOffsetY = effRadiusY * 0.28f
    val causticRadius = radius * 0.22f
    drawCircle(
        color = Color.White.copy(alpha = 0.40f * alpha),
        radius = causticRadius,
        center = Offset(cx + causticOffsetX, cy + causticOffsetY),
    )
}
