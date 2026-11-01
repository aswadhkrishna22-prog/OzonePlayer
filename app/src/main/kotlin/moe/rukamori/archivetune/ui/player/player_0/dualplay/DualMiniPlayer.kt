/*
 * Ozone Player (2026) | Dual Play Mini Player
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.playback.dualplay.DualPlayEngine
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.utils.bounceClick

/**
 * Compact, centered glass pill mini player shown when Dual Play is active and Player B has a song.
 *
 * Contains, from left to right:
 * 1. 40dp circular artwork of Song A with 360° vinyl spin and CircularWavyProgressIndicator.
 * 2. Middle round play/pause button (pauses both if either is playing; plays both if both paused).
 * 3. 40dp circular artwork of Song B with 360° vinyl spin and CircularWavyProgressIndicator.
 *
 * Tapping either artwork (or the pill background) expands the full player in split view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualMiniPlayer(
    paneSourceA: PaneSource,
    paneSourceB: PaneSource,
    onExpandClick: () -> Unit,
    expansionFractionProvider: () -> Float = { 0f },
    isVisible: Boolean = true,
    engine: DualPlayEngine? = null,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Pill container: ~56% width, centered horizontally
        Box(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .widthIn(min = 210.dp, max = 270.dp)
                .height(MiniPlayerHeight)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xE61A1A22))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.10f),
                        ),
                    ),
                    shape = RoundedCornerShape(32.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpandClick,
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Left: Song A Artwork with 360° spin & Wavy Progress ────────
                DualMiniArtwork(
                    paneSource = paneSourceA,
                    onExpandClick = onExpandClick,
                    isVisible = isVisible,
                )

                // ── Middle: Round Play / Pause Button ───────────────────────────
                DualMiniPlayPauseButton(
                    isPlaying = anyPlaying,
                    onClick = onPlayPauseBoth,
                )

                // ── Right: Song B Artwork with 360° spin & Wavy Progress ───────
                DualMiniArtwork(
                    paneSource = paneSourceB,
                    onExpandClick = onExpandClick,
                    isVisible = isVisible,
                )
            }
        }
    }
}

/**
 * Circular artwork surrounded by a CircularWavyProgressIndicator, exactly matching
 * the standard mini player's visual style.
 *
 * - Artwork spins 360° continuously when playing (15s per rotation, linear).
 * - When paused, stops rotation and dims to 60% alpha.
 * - CircularWavyProgressIndicator tracks that pane's playback progress with its vibrant tint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DualMiniArtwork(
    paneSource: PaneSource,
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

    val indicatorColor = if (uiState.vibrantColor != 0) Color(uiState.vibrantColor) else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpandClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // ── Circular Wavy Progress Indicator (matching standard mini player) ──
        CircularWavyProgressIndicator(
            progress = {
                if (durationMs > 0L) {
                    (progressMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            modifier = Modifier.fillMaxSize(),
            color = indicatorColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )

        // ── 40dp Circular Artwork with 360° Vinyl Spin when Playing ─────────
        Box(
            modifier = Modifier
                .size(40.dp)
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
 * Middle round Play/Pause button styled identically to the current mini player.
 */
@Composable
private fun DualMiniPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseIcon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow

    Box(
        modifier = modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clip(CircleShape)
            .bounceClick(pressedScale = 0.88f) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(playPauseIcon),
            contentDescription = stringResource(R.string.play_pause),
            modifier = Modifier.size(30.dp),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}
