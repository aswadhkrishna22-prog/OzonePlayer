/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.dualplay.DualPlaySheetTarget
import moe.rukamori.archivetune.ui.player.player_0.MiniPlayerContentInternal
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.scoped.FullPlayerVisualState
import moe.rukamori.archivetune.ui.player.player_0.scoped.SheetVerticalDragGestureHandler
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
internal fun UnifiedPlayerSheetLayers(
    state: PlayerUiState,
    queueState: QueueUiState,
    updateState: UpdateState,
    miniHorizontalInset: Dp = FloatingToolbarHorizontalPadding,
    expansionFractionProvider: () -> Float,
    lyricsFractionProvider: () -> Float,
    queueFractionProvider: () -> Float,
    playbackProgress: StateFlow<Long>,
    fullPlayerVisualState: FullPlayerVisualState,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onCloseQueueClick: () -> Unit = {},
    onMoreQueueClick: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onMoreLyricsClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onCollapseClick: () -> Unit,
    onExpandClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    dragHandler: SheetVerticalDragGestureHandler? = null,
    sheetTarget: DualPlaySheetTarget = DualPlaySheetTarget.PLAYER_A,
    onOpenQueueB: () -> Unit = {},
    onOpenLyricsA: () -> Unit = {},
    onOpenLyricsB: () -> Unit = {},
) {
    val density = LocalDensity.current.density
    val canvasState = rememberPlayerCanvasState(state.trackUrl, state.title, state.artist)

    val playerConnection = LocalPlayerConnection.current
    val dualPlayEngine = playerConnection?.dualPlayEngine
    val dualState by (dualPlayEngine?.state ?: flowOf(null)).collectAsState(null)
    val itemB = dualState?.playerBMediaItem
    val metadataB = remember(itemB) {
        if (itemB == null) null
        else itemB.metadata ?: MediaMetadata(
            id = itemB.mediaId,
            title = itemB.mediaMetadata.title?.toString() ?: "",
            artists = listOf(MediaMetadata.Artist(id = null, name = itemB.mediaMetadata.artist?.toString() ?: "")),
            duration = 0,
            thumbnailUrl = itemB.mediaMetadata.artworkUri?.toString(),
        )
    }
    val database = playerConnection?.service?.database
    val lyricsBFlow = remember(itemB?.mediaId) {
        if (itemB?.mediaId != null) database?.lyrics(itemB.mediaId) ?: flowOf(null) else flowOf(null)
    }

    LaunchedEffect(sheetTarget, itemB?.mediaId) {
        if (sheetTarget == DualPlaySheetTarget.PLAYER_B && itemB != null) {
            val meta = metadataB
            val s = playerConnection?.service
            if (meta != null && s != null) {
                val db = s.database
                if (db.lyrics(meta.id).first() == null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val lyr = s.lyricsHelper.getLyrics(meta)
                            db.query {
                                insertLyricsIfAbsent(id = meta.id, lyrics = lyr)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    val activeState = if (sheetTarget == DualPlaySheetTarget.PLAYER_B && itemB != null) {
        state.copy(
            title = itemB.mediaMetadata.title?.toString() ?: "(No Song B)",
            artist = itemB.mediaMetadata.artist?.toString() ?: "",
            coverUrl = itemB.mediaMetadata.artworkUri?.toString() ?: "",
            isPlaying = dualState?.playerBIsPlaying == true,
            durationMs = dualState?.playerBDuration ?: 0L,
            trackUrl = itemB.mediaId,
            isLiked = dualState?.playerBIsLiked == true,
            isImmersiveEnabled = true,
        )
    } else {
        state
    }

    val activeProgress = if (sheetTarget == DualPlaySheetTarget.PLAYER_B && dualPlayEngine != null) {
        dualPlayEngine.playerBPosition
    } else {
        playbackProgress
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = expansionFractionProvider().coerceIn(0f, 1f)
                }
        ) {
            PlayerBackgroundLayers(
                state = state,
                canvasState = canvasState,
                expansionFractionProvider = expansionFractionProvider,
                lyricsFractionProvider = lyricsFractionProvider,
                queueFractionProvider = queueFractionProvider,
                onColorsExtracted = { vibrant, darkMuted, gradient ->
                    onAction(PlayerAction.UpdateColors(vibrant, darkMuted, gradient))
                },
            )
        }

        val isMiniPlayerVisible by remember {
            derivedStateOf { expansionFractionProvider() < 0.05f }
        }

        val showMiniPlayer by remember {
            derivedStateOf { expansionFractionProvider() < 1f }
        }

        if (showMiniPlayer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fraction = expansionFractionProvider()
                        alpha = (1f - (fraction / 0.3f)).coerceIn(0f, 1f)
                    }
                    .padding(horizontal = miniHorizontalInset)
            ) {
                MiniPlayerContentInternal(
                    state = state,
                    expansionFractionProvider = expansionFractionProvider,
                    playbackProgress = playbackProgress,
                    onAction = onAction,
                    onMediaAreaClick = onExpandClick,
                    isVisible = isMiniPlayerVisible
                )
            }
        }

            val isFullPlayerVisible by remember {
                derivedStateOf { expansionFractionProvider() > 0.05f }
            }

            if (isFullPlayerVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val lyricsFraction = lyricsFractionProvider()
                            val expansionFraction = expansionFractionProvider()

                            alpha = expansionFraction.coerceIn(0f, 1f) *
                                    (1f - lyricsFraction.coerceIn(0f, 1f))

                            translationY = if (expansionFraction <= 0.005f) {
                                10000f
                            } else {
                                (1f - expansionFraction) * (150f * density) -
                                        (200f * density * lyricsFraction)
                            }
                        }
                ) {
                    FullPlayer(
                        state = state,
                        canvasState = canvasState,
                        playbackProgress = playbackProgress,
                        updateState = updateState,
                        slideOffset = expansionFractionProvider,
                        onCollapseClick = onCollapseClick,
                        onAction = onAction,
                        onSeek = onSeek,
                        onBackgroundStyleChanged = onBackgroundStyleChanged,
                        onOpenSettingsMenu = onOpenSettingsMenu,
                        onOpenQueue = onOpenQueue,
                        onSeekStarted = onSeekStarted,
                        lyricsFractionProvider = lyricsFractionProvider,
                        queueFractionProvider = queueFractionProvider,
                        isVisible = isFullPlayerVisible,
                        onOpenQueueB = onOpenQueueB,
                        onOpenLyricsA = onOpenLyricsA,
                        onOpenLyricsB = onOpenLyricsB,
                    )
                }
            }

            val isLyricsMounted by remember {
                derivedStateOf { lyricsFractionProvider() > 0.001f }
            }

            if (isLyricsMounted) {
                PlayerLyricsLayer(
                    state = activeState,
                    lyricsFractionProvider = lyricsFractionProvider,
                    playbackProgress = activeProgress,
                    onAction = { action ->
                        if (sheetTarget == DualPlaySheetTarget.PLAYER_B) {
                            if (action is PlayerAction.Like || action is PlayerAction.ToggleLike) {
                                dualPlayEngine?.toggleLikeB()
                            } else if (action is PlayerAction.PlayPause) {
                                if (dualState?.playerBIsPlaying == true) dualPlayEngine?.pauseB() else dualPlayEngine?.playB()
                            }
                        } else {
                            onAction(action)
                        }
                    },
                    onCloseLyricsClick = onCloseLyricsClick,
                    onMoreLyricsClick = onMoreLyricsClick,
                    onSearchLyricsClick = onSearchLyricsClick,
                    onSeek = { seekPos ->
                        if (sheetTarget == DualPlaySheetTarget.PLAYER_B) {
                            dualPlayEngine?.seekB(seekPos.toLong())
                        } else {
                            onSeek(seekPos)
                        }
                    },
                    onSeekStarted = onSeekStarted,
                    dragHandler = dragHandler,
                    playerOverride = if (sheetTarget == DualPlaySheetTarget.PLAYER_B) dualPlayEngine?.getPlayerB() else null,
                    mediaMetadataOverride = if (sheetTarget == DualPlaySheetTarget.PLAYER_B) metadataB else null,
                    lyricsOverride = if (sheetTarget == DualPlaySheetTarget.PLAYER_B) lyricsBFlow else null,
                    sheetTarget = sheetTarget,
                )
            }

            val isQueueMounted by remember {
                derivedStateOf { queueFractionProvider() > 0.001f }
            }

            if (isQueueMounted) {
                val activeQueueState = if (sheetTarget == DualPlaySheetTarget.PLAYER_B && dualState != null) {
                    QueueUiState(
                        queueWindows = dualState?.playerBQueueWindows ?: emptyList(),
                        currentWindowIndex = dualState?.playerBQueueIndex ?: -1,
                        title = "Song B Queue",
                        songCount = dualState?.playerBQueueSongCount ?: 0,
                        queueDurationMs = dualState?.playerBQueueDurationMs ?: 0L,
                    )
                } else {
                    queueState
                }

                PlayerQueueLayer(
                    state = activeState,
                    queueState = activeQueueState,
                    queueFractionProvider = queueFractionProvider,
                    onAction = { action ->
                        if (sheetTarget == DualPlaySheetTarget.PLAYER_B && dualPlayEngine != null) {
                            when (action) {
                                is PlayerAction.PlayQueueItem -> {
                                    val idx = dualPlayEngine.findWindowIndexByUidB(action.windowUid)
                                    if (idx >= 0) dualPlayEngine.playQueueItemB(idx)
                                }
                                is PlayerAction.RemoveQueueItem -> {
                                    val idx = dualPlayEngine.findWindowIndexByUidB(action.windowUid)
                                    if (idx >= 0) dualPlayEngine.removeQueueItemB(idx)
                                }
                                is PlayerAction.MoveQueueItem -> {
                                    val from = dualPlayEngine.findWindowIndexByUidB(action.fromUid)
                                    val to = dualPlayEngine.findWindowIndexByUidB(action.toUid)
                                    if (from >= 0 && to >= 0) dualPlayEngine.moveQueueItemB(from, to)
                                }
                                is PlayerAction.PlayPause -> {
                                    if (dualState?.playerBIsPlaying == true) dualPlayEngine.pauseB() else dualPlayEngine.playB()
                                }
                                else -> {}
                            }
                        } else {
                            onAction(action)
                        }
                    },
                    onCloseQueueClick = onCloseQueueClick,
                    onMoreQueueClick = onMoreQueueClick,
                    dragHandler = dragHandler,
                    sheetTarget = sheetTarget,
                )
        }
    }
}
