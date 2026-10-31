/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.MiniPlayerDropState
import moe.rukamori.archivetune.PlayerDockGeometry
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.playback.dualplay.DualPlaySheetTarget
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun UnifiedPlayerSheetV2(
    state: PlayerUiState,
    queueState: QueueUiState = QueueUiState(),
    updateState: UpdateState = UpdateState.NoUpdate,
    onAction: (PlayerAction) -> Unit,
    onCloseLyricsClick: () -> Unit,
    onSearchLyricsClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onSeekStarted: () -> Unit,
    playbackProgress: StateFlow<Long>,
    bottomBarHeight: Dp = 0.dp,
    useRail: Boolean = false,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    onExpansionFractionChanged: (Float) -> Unit = {},
    onLyricsClick: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onCloseQueueClick: () -> Unit = {},
    dropState: MiniPlayerDropState = MiniPlayerDropState.Default,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val (hapticFeedbackEnabled) = rememberPreference(EnableHapticFeedbackKey, true)

    val playerConnection = LocalPlayerConnection.current
    val dualPlayEngine = playerConnection?.dualPlayEngine
    val dualPlayState by (dualPlayEngine?.state ?: flowOf(null)).collectAsState(null)
    var sheetTarget by remember { mutableStateOf(DualPlaySheetTarget.PLAYER_A) }

    LaunchedEffect(dualPlayState?.enabled, dualPlayState?.splitViewActive) {
        if (dualPlayState?.enabled != true || dualPlayState?.splitViewActive != true) {
            sheetTarget = DualPlaySheetTarget.PLAYER_A
        }
    }

    var isLyricsMenuVisible by remember { mutableStateOf(false) }
    var isQueueMenuVisible by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var menuInitialScreen by remember { mutableStateOf(PlayerMenuScreen.SETTINGS) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val collapsedHorizontalInset = remember(maxWidth, useRail) {
            PlayerDockGeometry.collapsedHorizontalInset(
                availableWidth = maxWidth,
                useRail = useRail,
            )
        }

        val motionScope = rememberPlayerSheetMotionScope(
            state = state,
            maxHeight = maxHeight,
            maxWidth = maxWidth,
            bottomBarHeight = bottomBarHeight,
            useRail = useRail,
            collapsedHorizontalInset = collapsedHorizontalInset,
            density = density,
            onExpansionFractionChanged = onExpansionFractionChanged,
            onCloseLyricsClick = {
                onCloseLyricsClick()
            },
            onOpenQueue = onOpenQueue,
            onCloseQueueClick = {
                onCloseQueueClick()
            },
        )

        LaunchedEffect(motionScope.queueFractionProvider(), motionScope.lyricsFractionProvider()) {
            if (motionScope.queueFractionProvider() <= 0.01f && motionScope.lyricsFractionProvider() <= 0.01f) {
                sheetTarget = DualPlaySheetTarget.PLAYER_A
            }
        }

        val gestures = PlayerSheetGestures(
            motionScope = motionScope,
            state = state,
            onAction = onAction,
            onCloseLyricsClick = {
                onCloseLyricsClick()
            },
            view = view,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            onDragStartedPosition = { pos ->
                if (dualPlayState?.enabled == true && dualPlayState?.splitViewActive == true) {
                    val isSheetOpen = motionScope.queueFractionProvider() > 0.05f || motionScope.lyricsFractionProvider() > 0.05f
                    if (!isSheetOpen) {
                        val h = motionScope.screenHeightPx
                        sheetTarget = if (pos.y > h / 2f) DualPlaySheetTarget.PLAYER_B else DualPlaySheetTarget.PLAYER_A
                    }
                }
            },
        )

        val pillShape = rememberPlayerSheetPillShape(
            density = density,
            expansionFraction = motionScope.expansionFraction,
            isDocked = dropState.isDocked,
        )
        val miniHazeStyle = rememberMiniHazeStyle(
            pureBlack = pureBlack,
            blurRadius = blurRadius
        )
        val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

        val backgroundGradient = rememberBackgroundGradient(state.gradientColor)

        PlayerSheetScaffold(
            sheetVisualState = motionScope.sheetVisualState,
            currentSheetState = motionScope.currentSheetState,
            expansionFraction = motionScope.expansionFraction,
            offsetAnimatable = motionScope.offsetAnimatable,
            visualOvershootScaleY = motionScope.visualOvershootScaleY,
            miniDismissGestureHandler = gestures.miniDismissGestureHandler,
            dragHandler = gestures.dragHandler,
            screenHeightPx = motionScope.screenHeightPx,
            screenWidthPx = motionScope.screenWidthPx,
            density = density,
            pillShape = pillShape,
            dropState = dropState,
            content = {
                Box(
                    modifier = Modifier
                        .playerSheetBackdrop(
                            hazeState = hazeState,
                            hazeStyle = miniHazeStyle,
                            backgroundBrush = backgroundGradient,
                            pureBlack = pureBlack,
                            expansionFractionProvider = { motionScope.expansionFraction.value },
                        )
                        .playerSheetGlassBorder(
                            expansionFractionProvider = { motionScope.expansionFraction.value },
                            density = density
                        )
                ) {
                    UnifiedPlayerSheetLayers(
                        state = state,
                        queueState = queueState,
                        updateState = updateState,
                        miniHorizontalInset = collapsedHorizontalInset,
                        expansionFractionProvider = { motionScope.expansionFraction.value },
                        lyricsFractionProvider = motionScope.lyricsFractionProvider,
                        queueFractionProvider = motionScope.queueFractionProvider,
                        playbackProgress = playbackProgress,
                        fullPlayerVisualState = motionScope.fullPlayerVisualState,
                        onAction = onAction,
                        onCloseLyricsClick = {
                            onCloseLyricsClick()
                        },
                        onCloseQueueClick = {
                            motionScope.handleCloseQueue()
                        },
                        onMoreQueueClick = { isQueueMenuVisible = true },
                        onOpenQueue = {
                            sheetTarget = DualPlaySheetTarget.PLAYER_A
                            motionScope.handleOpenQueue()
                        },
                        onMoreLyricsClick = { isLyricsMenuVisible = true },
                        onSearchLyricsClick = onSearchLyricsClick,
                        onCollapseClick = { motionScope.collapse() },
                        onExpandClick = { motionScope.expand() },
                        onSeek = onSeek,
                        onSeekStarted = onSeekStarted,
                        onBackgroundStyleChanged = onBackgroundStyleChanged,
                        onImmersiveChanged = onImmersiveChanged,
                        onOpenSettingsMenu = { screen ->
                            menuInitialScreen = screen
                            showSettingsMenu = true
                        },
                        dragHandler = gestures.dragHandler,
                        sheetTarget = sheetTarget,
                        onOpenQueueB = {
                            sheetTarget = DualPlaySheetTarget.PLAYER_B
                            motionScope.handleOpenQueue()
                        },
                        onOpenLyricsA = {
                            sheetTarget = DualPlaySheetTarget.PLAYER_A
                            onAction(PlayerAction.Lyrics)
                        },
                        onOpenLyricsB = {
                            sheetTarget = DualPlaySheetTarget.PLAYER_B
                            onAction(PlayerAction.Lyrics)
                        },
                    )
                }
            }
        )

        val isTargetB = sheetTarget == DualPlaySheetTarget.PLAYER_B && dualPlayEngine != null && dualPlayState != null
        val activeDialogQueueState = if (isTargetB) {
            QueueUiState(
                queueWindows = dualPlayState?.playerBQueueWindows ?: emptyList(),
                currentWindowIndex = dualPlayState?.playerBQueueIndex ?: -1,
                title = "Song B Queue",
                songCount = dualPlayState?.playerBQueueSongCount ?: 0,
                queueDurationMs = dualPlayState?.playerBQueueDurationMs ?: 0L,
            )
        } else {
            queueState
        }
        val activeDialogState = if (isTargetB && dualPlayState?.playerBMediaItem != null) {
            val itemB = dualPlayState?.playerBMediaItem
            state.copy(
                trackUrl = itemB?.mediaId ?: "",
                title = itemB?.mediaMetadata?.title?.toString() ?: "",
                artist = itemB?.mediaMetadata?.artist?.toString() ?: "",
                album = itemB?.mediaMetadata?.albumTitle?.toString(),
            )
        } else {
            state
        }

        PlayerSheetDialogs(
            state = activeDialogState,
            queueState = activeDialogQueueState,
            updateState = updateState,
            onAction = { action ->
                if (isTargetB && dualPlayEngine != null) {
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
                        else -> onAction(action)
                    }
                } else {
                    onAction(action)
                }
            },
            onBackgroundStyleChanged = onBackgroundStyleChanged,
            onImmersiveChanged = onImmersiveChanged,
            isLyricsMenuVisible = isLyricsMenuVisible,
            onDismissLyricsMenu = { isLyricsMenuVisible = false },
            isQueueMenuVisible = isQueueMenuVisible,
            onDismissQueueMenu = { isQueueMenuVisible = false },
            showSettingsMenu = showSettingsMenu,
            menuInitialScreen = menuInitialScreen,
            onDismissSettingsMenu = { showSettingsMenu = false }
        )
    }
}
