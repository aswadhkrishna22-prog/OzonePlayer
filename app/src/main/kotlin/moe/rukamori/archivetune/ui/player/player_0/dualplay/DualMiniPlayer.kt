/*
 * Ozone Player (2026) | Dual Play Mini Player
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.playback.dualplay.DualPlayEngine
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.utils.bounceClick
import kotlin.math.max
import kotlin.math.min

// ── Oklab Perceptual Color Interpolation ────────────────────────────────────────

private fun srgbToLinear(c: Float): Float {
    return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055) / 1.055), 2.4).toFloat()
}

private fun linearToSrgb(c: Float): Float {
    val clamped = c.coerceIn(0f, 1f)
    return if (clamped <= 0.0031308f) clamped * 12.92f else (1.055f * Math.pow(clamped.toDouble(), 1.0 / 2.4).toFloat() - 0.055f)
}

private data class OklabColor(val l: Float, val a: Float, val b: Float, val alpha: Float)

private fun Color.toOklab(): OklabColor {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val bVal = srgbToLinear(blue)

    val l = Math.cbrt((0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * bVal).toDouble()).toFloat()
    val m = Math.cbrt((0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * bVal).toDouble()).toFloat()
    val s = Math.cbrt((0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * bVal).toDouble()).toFloat()

    return OklabColor(
        l = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        b = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
        alpha = alpha,
    )
}

private fun OklabColor.toColor(): Color {
    val l_ = l + 0.3963377774f * a + 0.2158037573f * b
    val m_ = l - 0.1055613458f * a - 0.0638541728f * b
    val s_ = l - 0.0894841775f * a - 1.2914855480f * b

    val lVal = l_ * l_ * l_
    val mVal = m_ * m_ * m_
    val sVal = s_ * s_ * s_

    val r = linearToSrgb(+4.0767416621f * lVal - 3.3077115913f * mVal + 0.2309699292f * sVal)
    val g = linearToSrgb(-1.2684380046f * lVal + 2.6097574011f * mVal - 0.3413193965f * sVal)
    val bVal = linearToSrgb(-0.0041960863f * lVal - 0.7034186147f * mVal + 1.7076147010f * sVal)

    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), bVal.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
}

private fun lerpOklab(start: Color, stop: Color, fraction: Float): Color {
    val lab1 = start.toOklab()
    val lab2 = stop.toOklab()
    val l = lab1.l + (lab2.l - lab1.l) * fraction
    val a = lab1.a + (lab2.a - lab1.a) * fraction
    val b = lab1.b + (lab2.b - lab1.b) * fraction
    val alpha = lab1.alpha + (lab2.alpha - lab1.alpha) * fraction
    return OklabColor(l, a, b, alpha).toColor()
}

/**
 * Polished Dual Play Mini Player pill:
 *
 * - Wraps its content with 14dp horizontal padding.
 * - 44dp circular artwork of Song A with 360° vinyl spin, CircularWavyProgressIndicator, and subtle 12dp glow.
 * - 18dp gap between artwork and play button on each side.
 * - 40dp middle play/pause button with faint A-to-B gradient fill and white icon.
 * - 44dp circular artwork of Song B with 360° vinyl spin, CircularWavyProgressIndicator, and subtle 12dp glow.
 * - Real Haze frosted glass blur, 25% black base scrim, 5-stop Oklab horizontal gradient tint and 1dp border, soft top highlight.
 * - 600ms smooth animated color transitions when songs change.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
fun DualMiniPlayer(
    paneSourceA: PaneSource,
    paneSourceB: PaneSource,
    onExpandClick: () -> Unit,
    expansionFractionProvider: () -> Float = { 0f },
    isVisible: Boolean = true,
    engine: DualPlayEngine? = null,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current
    val effectiveEngine = engine ?: playerConnection?.dualPlayEngine

    val isPlayingA = paneSourceA.uiState.isPlaying
    val isPlayingB = paneSourceB.uiState.isPlaying
    val anyPlaying = isPlayingA || isPlayingB

    val onPlayPauseBoth: () -> Unit = {
        if (anyPlaying) {
            if (isPlayingA) {
                if (effectiveEngine != null) effectiveEngine.pauseA() else paneSourceA.onAction(PlayerAction.PlayPause)
            }
            if (isPlayingB) {
                if (effectiveEngine != null) effectiveEngine.pauseB() else paneSourceB.onAction(PlayerAction.PlayPause)
            }
        } else {
            if (effectiveEngine != null) effectiveEngine.playA() else paneSourceA.onAction(PlayerAction.PlayPause)
            if (effectiveEngine != null) effectiveEngine.playB() else paneSourceB.onAction(PlayerAction.PlayPause)
        }
    }

    // Dynamic vibrant colors animated smoothly over 600ms
    val rawColorA = if (paneSourceA.uiState.vibrantColor != 0) Color(paneSourceA.uiState.vibrantColor) else Color(0xFF4DD0E1)
    val rawColorB = if (paneSourceB.uiState.vibrantColor != 0) Color(paneSourceB.uiState.vibrantColor) else Color(0xFFBA68C8)

    val colorA by animateColorAsState(
        targetValue = rawColorA,
        animationSpec = tween(durationMillis = 600),
        label = "DualMiniColorA",
    )
    val colorB by animateColorAsState(
        targetValue = rawColorB,
        animationSpec = tween(durationMillis = 600),
        label = "DualMiniColorB",
    )

    // 5-stop Oklab horizontal gradients for pill tint (28%) and border (55%)
    val pillTintStops = remember(colorA, colorB) {
        val cA = colorA.copy(alpha = 0.28f)
        val cB = colorB.copy(alpha = 0.28f)
        arrayOf(
            0.00f to cA,
            0.25f to lerpOklab(cA, cB, 0.25f),
            0.50f to lerpOklab(cA, cB, 0.50f),
            0.75f to lerpOklab(cA, cB, 0.75f),
            1.00f to cB,
        )
    }

    val pillBorderStops = remember(colorA, colorB) {
        val cA = colorA.copy(alpha = 0.55f)
        val cB = colorB.copy(alpha = 0.55f)
        arrayOf(
            0.00f to cA,
            0.25f to lerpOklab(cA, cB, 0.25f),
            0.50f to lerpOklab(cA, cB, 0.50f),
            0.75f to lerpOklab(cA, cB, 0.75f),
            1.00f to cB,
        )
    }

    val pillShape = CircleShape

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Translucent glass pill: wraps content width, 56dp height, centered
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(56.dp)
                .shadow(elevation = 12.dp, shape = pillShape)
                .clip(pillShape)
                .then(
                    if (hazeState != null) {
                        val blurStyle = remember(hazeState) {
                            HazeBlurStyle {
                                backgroundColor(Color.Transparent)
                                blurRadius(20.dp)
                                noiseFactor(0.05f)
                            }
                        }
                        Modifier.hazeBlur(
                            input = HazeInput.Sources(hazeState),
                            style = blurStyle,
                            performanceMode = HazePerformanceMode.Fixed(0.75f),
                        )
                    } else {
                        Modifier
                    }
                )
                // Dark base scrim (black about 25%) under the tint
                .background(Color.Black.copy(alpha = 0.25f))
                // Pill tint: horizontal gradient from A's colour to B's colour with 5 Oklab stops
                .background(Brush.horizontalGradient(colorStops = pillTintStops))
                // Soft top specular highlight
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.22f),
                        0.40f to Color.White.copy(alpha = 0.05f),
                        1.0f to Color.Transparent,
                    )
                )
                // 1dp border: gradient from A's colour to B's colour with 5 Oklab stops
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(colorStops = pillBorderStops),
                    shape = pillShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpandClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Left: Song A Artwork (44dp) with 360° spin & Wavy Progress ────────
                DualMiniArtwork(
                    paneSource = paneSourceA,
                    color = colorA,
                    onExpandClick = onExpandClick,
                    isVisible = isVisible,
                )

                // ── Middle: Round Play / Pause Button (40dp) ───────────────────────────
                DualMiniPlayPauseButton(
                    isPlaying = anyPlaying,
                    colorA = colorA,
                    colorB = colorB,
                    onClick = onPlayPauseBoth,
                )

                // ── Right: Song B Artwork (44dp) with 360° spin & Wavy Progress ───────
                DualMiniArtwork(
                    paneSource = paneSourceB,
                    color = colorB,
                    onExpandClick = onExpandClick,
                    isVisible = isVisible,
                )
            }
        }
    }
}

/**
 * 44dp circular artwork surrounded by a CircularWavyProgressIndicator with:
 * - 360° continuous vinyl spin when playing (15s linear).
 * - Soft blurred 12dp glow pulsing subtly (alpha 0.25 to 0.4, 3s cycle) ONLY while playing & visible.
 * - When paused, stops rotation, stops pulse (alpha 0f), and dims artwork to 60%.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DualMiniArtwork(
    paneSource: PaneSource,
    color: Color,
    onExpandClick: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState = paneSource.uiState
    val isPlaying = uiState.isPlaying
    val durationMs = uiState.durationMs
    val positionFlow = paneSource.positionFlow

    var progressMs by remember(positionFlow) { mutableLongStateOf(positionFlow.value) }

    LaunchedEffect(isVisible, positionFlow) {
        if (!isVisible) return@LaunchedEffect
        positionFlow.collect { progressMs = it }
    }

    // ── 360° Vinyl Continuous Rotation while Playing ────────────────────────
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, isVisible) {
        if (isPlaying && isVisible) {
            while (true) {
                val currentDegree = rotation.value % 360f
                rotation.snapTo(currentDegree)
                rotation.animateTo(
                    targetValue = currentDegree + 360f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing),
                )
            }
        }
    }

    // ── Subtle 12dp Pulsing Glow behind Artwork (3s cycle, only when playing & in foreground) ──
    val infiniteTransition = rememberInfiniteTransition(label = "DualMiniGlow")
    val glowAlpha by if (isPlaying && isVisible) {
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.40f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "GlowAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpandClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Soft blurred glow (about 12dp beyond artwork, 44 + 24 = 68dp)
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .size(68.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.65f),
                                color.copy(alpha = 0.25f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
        }

        // ── Circular Wavy Progress Indicator (44dp) ──────────────────────────
        CircularWavyProgressIndicator(
            progress = {
                if (durationMs > 0L) {
                    (progressMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )

        // ── 36dp Circular Artwork with 360° Vinyl Spin when Playing ─────────
        Box(
            modifier = Modifier
                .size(36.dp)
                .aspectRatio(1f)
                .graphicsLayer {
                    rotationZ = rotation.value
                    alpha = if (isPlaying) 1f else 0.60f
                }
                .clip(CircleShape),
        ) {
            val coverUrl = uiState.coverUrl.takeIf { it.isNotBlank() }
            Crossfade(
                targetState = coverUrl,
                animationSpec = tween(350),
                label = "DualMiniCoverCrossfade",
            ) { url ->
                if (url == null) {
                    Image(
                        painter = painterResource(id = uiState.placeholderResId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    val request = remember(url) {
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(96)
                            .allowHardware(true)
                            .memoryCacheKey("dual_mini:$url")
                            .diskCacheKey("dual_mini:$url")
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = uiState.placeholderResId),
                    )
                }
            }
        }
    }
}

/**
 * 40dp round Play/Pause button with faint A-to-B gradient fill (about 18%) and white icon.
 */
@Composable
private fun DualMiniPlayPauseButton(
    isPlaying: Boolean,
    colorA: Color,
    colorB: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseIcon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow

    val buttonFill = remember(colorA, colorB) {
        val cA = colorA.copy(alpha = 0.18f)
        val cB = colorB.copy(alpha = 0.18f)
        val mid = lerpOklab(cA, cB, 0.5f)
        Brush.horizontalGradient(listOf(cA, mid, cB))
    }
    val buttonBorder = remember(colorA, colorB) {
        val cA = colorA.copy(alpha = 0.35f)
        val cB = colorB.copy(alpha = 0.35f)
        val mid = lerpOklab(cA, cB, 0.5f)
        Brush.horizontalGradient(listOf(cA, mid, cB))
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .background(buttonFill, CircleShape)
            .border(1.dp, buttonBorder, CircleShape)
            .clip(CircleShape)
            .bounceClick(pressedScale = 0.88f) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(playPauseIcon),
            contentDescription = stringResource(R.string.play_pause),
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}
