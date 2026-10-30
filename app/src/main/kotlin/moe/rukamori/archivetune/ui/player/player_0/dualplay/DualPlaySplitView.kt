/*
 * Ozone Player (2026) | Dual Play Split View
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.dualplay.DualPlayEngine
import moe.rukamori.archivetune.playback.dualplay.DualPlayState
import moe.rukamori.archivetune.playback.dualplay.EarSelection
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.utils.bounceClick
import kotlin.math.abs

private val SnapTargets = floatArrayOf(0.30f, 0.50f, 0.70f)

@Composable
fun DualPlaySplitView(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    onSeekA: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalYumaHaptics.current
    val fontFamily = LocalArchiveTuneFontFamily.current
    val playerConnection = LocalPlayerConnection.current
    val engine = playerConnection?.dualPlayEngine ?: return

    val dualState by engine.state.collectAsState(DualPlayState())
    val progressA by playbackProgress.collectAsState()

    var posB by remember { mutableLongStateOf(0L) }
    var durB by remember { mutableLongStateOf(0L) }

    LaunchedEffect(dualState.playerBIsPlaying, dualState.playerBMediaItem) {
        while (isActive) {
            posB = engine.getPlayerBPosition()
            durB = engine.getPlayerBDuration()
            delay(500L)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val dividerFractionAnim = remember { Animatable(0.50f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeightPx = constraints.maxHeight.toFloat()
        val totalHeightDp = with(LocalDensity.current) { totalHeightPx.toDp() }
        val dividerHeightDp = 48.dp

        val currentFraction = dividerFractionAnim.value.coerceIn(0.25f, 0.75f)

        val topPaneHeightDp = (totalHeightDp * currentFraction - (dividerHeightDp / 2)).coerceAtLeast(160.dp)
        val bottomPaneHeightDp = (totalHeightDp * (1f - currentFraction) - (dividerHeightDp / 2)).coerceAtLeast(160.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            // ── TOP PANE: Song A (Left ear default, buttons R and M) ───────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPaneHeightDp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                CompactPlayerPane(
                    badgeLabel = "SONG A",
                    title = state.title,
                    artist = state.artist,
                    artworkUrl = state.coverUrl.takeIf { it.isNotBlank() } ?: state.coverDrawable,
                    isPlaying = state.isPlaying,
                    positionMs = progressA,
                    durationMs = state.durationMs,
                    volume = dualState.volumeA,
                    earButtons = {
                        PaneAEarButtons(
                            currentEar = dualState.earSelectionA,
                            disabledEar = dualState.earSelectionB,
                            onEarSelected = { ear -> engine.setEarA(ear) },
                        )
                    },
                    hasPrevNext = true,
                    onPlayPause = { onAction(PlayerAction.PlayPause) },
                    onPrev = { onAction(PlayerAction.Previous) },
                    onNext = { onAction(PlayerAction.Next) },
                    onSeek = { targetMs -> onSeekA(targetMs.toFloat()) },
                    onVolumeChange = { v -> engine.setVolumeA(v) },
                    vibrantColor = Color(state.vibrantColor),
                    availableHeightDp = with(LocalDensity.current) { topPaneHeightDp.toPx() } / LocalDensity.current.density,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── DRAGGABLE DIVIDER WITH SWAP BUTTON ────────────────────────────
            val draggableState = rememberDraggableState { deltaPx ->
                val newFraction = (dividerFractionAnim.value + deltaPx / totalHeightPx).coerceIn(0.25f, 0.75f)
                coroutineScope.launch {
                    dividerFractionAnim.snapTo(newFraction)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dividerHeightDp)
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = {
                            val cur = dividerFractionAnim.value
                            val closest = SnapTargets.minByOrNull { abs(it - cur) } ?: 0.50f
                            coroutineScope.launch {
                                haptics.click()
                                dividerFractionAnim.animateTo(
                                    targetValue = closest,
                                    animationSpec = spring(
                                        dampingRatio = 0.75f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                coroutineScope.launch {
                                    haptics.click()
                                    dividerFractionAnim.animateTo(
                                        targetValue = 0.50f,
                                        animationSpec = spring(
                                            dampingRatio = 0.75f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.12f),
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // Slim draggable handle pill
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.45f)),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Swap Ears Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .bounceClick(pressedScale = 0.92f) {
                                haptics.click()
                                engine.swapEars()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.restore),
                            contentDescription = "Swap Ears",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Swap",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                        )
                    }
                }
            }

            // ── BOTTOM PANE: Song B (Right ear default, buttons L and M) ───────
            val itemB = dualState.playerBMediaItem
            val titleB = itemB?.mediaMetadata?.title?.toString() ?: "(No Song B)"
            val artistB = itemB?.mediaMetadata?.artist?.toString() ?: ""
            val artworkB = itemB?.mediaMetadata?.artworkUri

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomPaneHeightDp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                CompactPlayerPane(
                    badgeLabel = "SONG B",
                    title = titleB,
                    artist = artistB,
                    artworkUrl = artworkB,
                    isPlaying = dualState.playerBIsPlaying,
                    positionMs = posB,
                    durationMs = durB,
                    volume = dualState.volumeB,
                    earButtons = {
                        PaneBEarButtons(
                            currentEar = dualState.earSelectionB,
                            disabledEar = dualState.earSelectionA,
                            onEarSelected = { ear -> engine.setEarB(ear) },
                        )
                    },
                    hasPrevNext = false,
                    onPlayPause = {
                        if (dualState.playerBIsPlaying) engine.pauseB() else engine.playB()
                    },
                    onSeek = { targetMs -> engine.seekB(targetMs) },
                    onVolumeChange = { v -> engine.setVolumeB(v) },
                    vibrantColor = Color(0xFF4DD0E1), // Cyan/Teal accent for Song B
                    availableHeightDp = with(LocalDensity.current) { bottomPaneHeightDp.toPx() } / LocalDensity.current.density,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
