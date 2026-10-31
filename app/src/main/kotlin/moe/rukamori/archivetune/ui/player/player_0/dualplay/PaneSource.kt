/*
 * Ozone Player (2026) | Dual Play Pane Source Abstraction
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.dualplay.DualPlayEngine
import moe.rukamori.archivetune.playback.dualplay.DualPlaySheetTarget
import moe.rukamori.archivetune.playback.dualplay.DualPlayState
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState

/**
 * Unified abstraction representing the data and action source for a player pane (Player A or Player B).
 * Every pane-level menu, sheet, and background reads strictly from this source.
 */
interface PaneSource {
    val target: DualPlaySheetTarget
    val uiState: PlayerUiState
    val queueState: QueueUiState
    val mediaItem: MediaItem?
    val mediaMetadata: MediaMetadata?
    val positionFlow: StateFlow<Long>

    val hasArtistId: Boolean
    val hasAlbumId: Boolean
    val artistId: String?
    val albumId: String?

    fun onAction(action: PlayerAction)
    fun onSeek(pos: Float)
    fun onSeekStarted()
}

/**
 * Creates and remembers a [PaneSource] for Player A.
 * Wraps existing single-player state and actions.
 */
@Composable
fun rememberPaneSourceA(
    state: PlayerUiState,
    queueState: QueueUiState,
    playbackProgress: StateFlow<Long>,
    mediaMetadata: MediaMetadata?,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit = {},
): PaneSource {
    val artId = mediaMetadata?.artists?.firstOrNull()?.id ?: state.artistId
    val albId = mediaMetadata?.album?.id ?: state.albumId
    return remember(state, queueState, playbackProgress, mediaMetadata, onAction, onSeek) {
        object : PaneSource {
            override val target: DualPlaySheetTarget = DualPlaySheetTarget.PLAYER_A
            override val uiState: PlayerUiState = state
            override val queueState: QueueUiState = queueState
            override val mediaItem: MediaItem? = null
            override val mediaMetadata: MediaMetadata? = mediaMetadata
            override val positionFlow: StateFlow<Long> = playbackProgress
            override val hasArtistId: Boolean = !artId.isNullOrBlank()
            override val hasAlbumId: Boolean = !albId.isNullOrBlank()
            override val artistId: String? = artId
            override val albumId: String? = albId

            override fun onAction(action: PlayerAction) = onAction(action)
            override fun onSeek(pos: Float) = onSeek(pos)
            override fun onSeekStarted() = onSeekStarted()
        }
    }
}

/**
 * Creates and remembers a [PaneSource] for Player B.
 * Wraps Player B's state, media item, queue, and isolated controls.
 * While Dual Play is active, no action or property leaks into Player A.
 */
@Composable
fun rememberPaneSourceB(
    baseState: PlayerUiState,
    dualState: DualPlayState,
    engine: DualPlayEngine,
    vibrantColorB: Int,
    gradientColorB: Int,
    mediaMetadataB: MediaMetadata?,
    onOpenLyricsB: () -> Unit,
    onOpenQueueB: () -> Unit,
    onShareTrack: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
): PaneSource {
    val itemB = dualState.playerBMediaItem
    val titleB = itemB?.mediaMetadata?.title?.toString() ?: "(No Song B)"
    val artistB = itemB?.mediaMetadata?.artist?.toString() ?: ""
    val albumB = itemB?.mediaMetadata?.albumTitle?.toString()
    val artworkUriB = itemB?.mediaMetadata?.artworkUri?.toString() ?: ""
    val trackIdB = itemB?.mediaId ?: ""

    val artIdB = mediaMetadataB?.artists?.firstOrNull()?.id
    val albIdB = mediaMetadataB?.album?.id

    val uiStateB = remember(
        baseState,
        itemB,
        titleB,
        artistB,
        albumB,
        artworkUriB,
        trackIdB,
        dualState.playerBIsPlaying,
        dualState.playerBPlaybackState,
        dualState.playerBDuration,
        dualState.playerBIsLiked,
        vibrantColorB,
        gradientColorB,
        dualState.playerBShuffleModeEnabled,
        dualState.playerBRepeatMode,
    ) {
        baseState.copy(
            trackUrl = trackIdB,
            title = titleB,
            artist = artistB,
            album = albumB,
            artistId = artIdB,
            albumId = albIdB,
            coverUrl = artworkUriB,
            isPlaying = dualState.playerBIsPlaying,
            isLoading = dualState.playerBPlaybackState == androidx.media3.common.Player.STATE_BUFFERING,
            durationMs = dualState.playerBDuration,
            isLiked = dualState.playerBIsLiked,
            vibrantColor = vibrantColorB,
            gradientColor = gradientColorB,
            isImmersiveEnabled = true,
            isLyricsVisible = false,
            isQueueVisible = false,
            shuffleState = if (dualState.playerBShuffleModeEnabled) "on" else "off",
            repeatState = when (dualState.playerBRepeatMode) {
                androidx.media3.common.Player.REPEAT_MODE_ONE -> "one"
                androidx.media3.common.Player.REPEAT_MODE_ALL -> "all"
                else -> "off"
            },
        )
    }

    val queueStateB = remember(
        dualState.playerBQueueWindows,
        dualState.playerBQueueIndex,
        dualState.playerBQueueSongCount,
        dualState.playerBQueueDurationMs,
    ) {
        QueueUiState(
            queueWindows = dualState.playerBQueueWindows,
            currentWindowIndex = dualState.playerBQueueIndex,
            title = "Song B Queue",
            songCount = dualState.playerBQueueSongCount,
            queueDurationMs = dualState.playerBQueueDurationMs,
        )
    }

    return remember(
        uiStateB,
        queueStateB,
        itemB,
        mediaMetadataB,
        engine,
        vibrantColorB,
        gradientColorB,
    ) {
        object : PaneSource {
            override val target: DualPlaySheetTarget = DualPlaySheetTarget.PLAYER_B
            override val uiState: PlayerUiState = uiStateB
            override val queueState: QueueUiState = queueStateB
            override val mediaItem: MediaItem? = itemB
            override val mediaMetadata: MediaMetadata? = mediaMetadataB
            override val positionFlow: StateFlow<Long> = engine.playerBPosition
            override val hasArtistId: Boolean = !artIdB.isNullOrBlank()
            override val hasAlbumId: Boolean = !albIdB.isNullOrBlank()
            override val artistId: String? = artIdB
            override val albumId: String? = albIdB

            override fun onAction(action: PlayerAction) {
                when (action) {
                    is PlayerAction.PlayPause -> {
                        if (dualState.playerBIsPlaying) engine.pauseB() else engine.playB()
                    }
                    is PlayerAction.Next -> engine.nextB()
                    is PlayerAction.Previous -> engine.prevB()
                    is PlayerAction.Seek -> engine.seekB(action.positionMs)
                    is PlayerAction.Like, is PlayerAction.ToggleLike -> engine.toggleLikeB()
                    is PlayerAction.Shuffle -> engine.toggleShuffleB()
                    is PlayerAction.Repeat -> engine.toggleRepeatB()
                    is PlayerAction.Lyrics -> onOpenLyricsB()
                    is PlayerAction.Queue -> onOpenQueueB()
                    is PlayerAction.PlayQueueItem -> {
                        val idx = engine.findWindowIndexByUidB(action.windowUid)
                        if (idx >= 0) engine.playQueueItemB(idx)
                    }
                    is PlayerAction.RemoveQueueItem -> {
                        val idx = engine.findWindowIndexByUidB(action.windowUid)
                        if (idx >= 0) engine.removeQueueItemB(idx)
                    }
                    is PlayerAction.MoveQueueItem -> {
                        val from = engine.findWindowIndexByUidB(action.fromUid)
                        val to = engine.findWindowIndexByUidB(action.toUid)
                        if (from >= 0 && to >= 0) engine.moveQueueItemB(from, to)
                    }
                    is PlayerAction.Share -> {
                        if (trackIdB.isNotBlank()) {
                            onShareTrack("https://music.youtube.com/watch?v=$trackIdB")
                        }
                    }
                    is PlayerAction.OpenArtist -> {
                        if (!artIdB.isNullOrBlank()) {
                            onNavigate("artist/$artIdB")
                        }
                    }
                    is PlayerAction.OpenAlbum -> {
                        if (!albIdB.isNullOrBlank()) {
                            onNavigate("album/$albIdB")
                        }
                    }
                    else -> {
                        // Do not forward foreign single-player actions to main player
                    }
                }
            }

            override fun onSeek(pos: Float) {
                engine.seekB(pos.toLong())
            }

            override fun onSeekStarted() {}
        }
    }
}
