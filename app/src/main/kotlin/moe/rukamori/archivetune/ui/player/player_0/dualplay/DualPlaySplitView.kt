/*
 * Ozone Player (2026) | Dual Play Split View
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.dualplay.DualPlayEngine
import moe.rukamori.archivetune.playback.dualplay.DualPlayState
import moe.rukamori.archivetune.playback.dualplay.EarSelection
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.player.player_0.FullPlayerControlsDefaults
import moe.rukamori.archivetune.ui.player.player_0.PlayerBackgroundLayers
import moe.rukamori.archivetune.ui.player.player_0.PlayerControls
import moe.rukamori.archivetune.ui.player.player_0.PlayerMetadata
import moe.rukamori.archivetune.ui.player.player_0.PlayerPaletteExtractor
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerBottomBar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.utils.bounceClick
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DualPlaySplitView(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    onSeekA: (Float) -> Unit,
    onCollapseClick: () -> Unit = {},
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = LocalYumaHaptics.current
    val playerConnection = LocalPlayerConnection.current
    val engine = playerConnection?.dualPlayEngine ?: return

    val dualState by engine.state.collectAsState(DualPlayState())

    // ── Song B state & dynamic palette extraction ──────────────────────────────
    val itemB = dualState.playerBMediaItem
    val titleB = itemB?.mediaMetadata?.title?.toString() ?: "(No Song B)"
    val artistB = itemB?.mediaMetadata?.artist?.toString() ?: ""
    val artworkUriB = itemB?.mediaMetadata?.artworkUri?.toString()

    var vibrantColorB by remember { mutableIntStateOf(0xFF4DD0E1.toInt()) }
    var gradientColorB by remember { mutableIntStateOf(0xFF1E1E24.toInt()) }

    key(itemB?.mediaId ?: artworkUriB) {
        PlayerPaletteExtractor(
            targetUrl = artworkUriB,
            trackUrl = itemB?.mediaId ?: "playerB",
            onColorsExtracted = { vibrant, _, gradient ->
                vibrantColorB = vibrant
                gradientColorB = gradient
            }
        )
    }

    val stateA = remember(state) {
        state.copy(
            isImmersiveEnabled = true,
            isLyricsVisible = false,
            isQueueVisible = false,
        )
    }

    val stateB = remember(
        itemB,
        vibrantColorB,
        gradientColorB,
        dualState.playerBIsPlaying,
        dualState.playerBDuration,
        state.isBlurBackgroundEnabled,
        state.isAlbumCoverGlowEnabled,
    ) {
        state.copy(
            trackUrl = itemB?.mediaId ?: "song_b",
            title = titleB,
            artist = artistB,
            coverUrl = artworkUriB ?: "",
            isPlaying = dualState.playerBIsPlaying,
            durationMs = dualState.playerBDuration,
            isLiked = false,
            vibrantColor = vibrantColorB,
            gradientColor = gradientColorB,
            isImmersiveEnabled = true,
            isLyricsVisible = false,
            isQueueVisible = false,
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val dividerFractionAnim = remember { Animatable(0.50f) }

    var controlsHeightPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalHeightPx = constraints.maxHeight.toFloat()
        val totalHeightDp = with(density) { totalHeightPx.toDp() }

        val minPaneHeightPx = maxOf(controlsHeightPx.toFloat(), with(density) { 250.dp.toPx() })
        val minFraction = (minPaneHeightPx / totalHeightPx).coerceIn(0.20f, 0.45f)
        val maxFraction = 1f - minFraction

        val snapTargets = remember(minFraction, maxFraction) {
            floatArrayOf(
                0.30f.coerceIn(minFraction, maxFraction),
                0.50f,
                0.70f.coerceIn(minFraction, maxFraction)
            )
        }

        val currentFraction = dividerFractionAnim.value.coerceIn(minFraction, maxFraction)

        val topPaneHeightDp = totalHeightDp * currentFraction
        val bottomPaneHeightDp = totalHeightDp * (1f - currentFraction)

        val navBarBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Column(modifier = Modifier.fillMaxSize()) {
            // ── TOP PANE: Song A ──────────────────────────────────────────────
            ImmersiveNowPlayingPane(
                paneHeightDp = topPaneHeightDp,
                paneState = stateA,
                title = state.title,
                artist = state.artist,
                isPlaying = state.isPlaying,
                positionFlow = playbackProgress,
                durationMs = state.durationMs,
                currentEar = dualState.earSelectionA,
                disabledEar = dualState.earSelectionB,
                onEarSelected = { engine.setEarA(it) },
                isLikeEnabled = true,
                onAction = onAction,
                onSeek = { onSeekA(it.toFloat()) },
                onPlayPause = { onAction(PlayerAction.PlayPause) },
                onPrev = { onAction(PlayerAction.Previous) },
                onNext = { onAction(PlayerAction.Next) },
                isNextEnabled = true,
                isPrevEnabled = true,
                shuffleContent = null, // uses default
                repeatContent = null,  // uses default
                isBottomBarEnabled = true,
                onOpenQueue = onOpenQueue,
                isTopPane = true,
                onCollapseClick = onCollapseClick,
                onOpenSettingsMenu = onOpenSettingsMenu,
                volume = dualState.volumeA,
                onVolumeChange = { engine.setVolumeA(it) },
                onSwapEars = { engine.swapEars() },
                onDisableDualPlay = { engine.disable() },
                bottomGapDp = 16.dp, // 16dp gap below lyrics/queue row (Amendment 3)
                onControlsHeightMeasured = { if (it > controlsHeightPx) controlsHeightPx = it },
            )

            // ── BOTTOM PANE: Song B ───────────────────────────────────────────
            val isRepeatOneB = dualState.playerBRepeatMode == Player.REPEAT_MODE_ONE
            val repeatIconB = if (isRepeatOneB) R.drawable.ic_repeat_one else R.drawable.ic_repeat
            val repeatTintB = if (isRepeatOneB) Color(vibrantColorB) else Color.White.copy(alpha = 0.5f)

            ImmersiveNowPlayingPane(
                paneHeightDp = bottomPaneHeightDp,
                paneState = stateB,
                title = titleB,
                artist = artistB,
                isPlaying = dualState.playerBIsPlaying,
                positionFlow = engine.playerBPosition,
                durationMs = dualState.playerBDuration,
                currentEar = dualState.earSelectionB,
                disabledEar = dualState.earSelectionA,
                onEarSelected = { engine.setEarB(it) },
                isLikeEnabled = false, // Heart dimmed and disabled for B
                onAction = {},
                onSeek = { engine.seekB(it) },
                onPlayPause = {
                    if (dualState.playerBIsPlaying) engine.pauseB() else engine.playB()
                },
                onPrev = { engine.seekB(0L) }, // Previous seeks to start
                onNext = {},                   // Next disabled
                isNextEnabled = false,         // Dimmed and disabled
                isPrevEnabled = true,
                shuffleContent = {
                    // Dimmed and disabled Shuffle for B
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_shuffle),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.38f)),
                        )
                    }
                },
                repeatContent = {
                    // Repeat toggles OFF / ONE on Player B
                    Box(
                        modifier = Modifier
                            .bounceClick(pressedScale = 0.86f) {
                                engine.toggleRepeatB()
                            }
                            .size(44.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = repeatIconB),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(repeatTintB),
                        )
                    }
                },
                isBottomBarEnabled = false, // Lyrics & Queue dimmed and disabled
                onOpenQueue = {},
                isTopPane = false,
                onCollapseClick = {},
                onOpenSettingsMenu = null,
                volume = dualState.volumeB,
                onVolumeChange = { engine.setVolumeB(it) },
                onSwapEars = { engine.swapEars() },
                onDisableDualPlay = { engine.disable() },
                bottomGapDp = navBarBottomDp + 8.dp,
                onControlsHeightMeasured = { if (it > controlsHeightPx) controlsHeightPx = it },
            )
        }

        // ── FLOATING 3-DOT DIVIDER HANDLE ─────────────────────────────────────
        val draggableState = rememberDraggableState { deltaPx ->
            val newFraction = (dividerFractionAnim.value + deltaPx / totalHeightPx).coerceIn(minFraction, maxFraction)
            coroutineScope.launch {
                dividerFractionAnim.snapTo(newFraction)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .offset {
                    IntOffset(
                        x = 0,
                        y = with(density) { (topPaneHeightDp - 24.dp).roundToPx() }
                    )
                }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        val cur = dividerFractionAnim.value
                        val closest = snapTargets.minByOrNull { abs(it - cur) } ?: 0.50f
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.75f))
                    )
                }
            }
        }
    }
}

/**
 * Single instance of the full-bleed immersive Now Playing pane.
 * Anchored to the bottom of its allocated paneHeightDp and clipped at the pane's top.
 */
@Composable
private fun ImmersiveNowPlayingPane(
    paneHeightDp: Dp,
    paneState: PlayerUiState,
    title: String,
    artist: String,
    isPlaying: Boolean,
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    currentEar: EarSelection?,
    disabledEar: EarSelection?,
    onEarSelected: (EarSelection) -> Unit,
    isLikeEnabled: Boolean,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    isNextEnabled: Boolean,
    isPrevEnabled: Boolean,
    shuffleContent: @Composable (() -> Unit)?,
    repeatContent: @Composable (() -> Unit)?,
    isBottomBarEnabled: Boolean,
    onOpenQueue: () -> Unit,
    isTopPane: Boolean,
    onCollapseClick: () -> Unit,
    onOpenSettingsMenu: ((PlayerMenuScreen) -> Unit)?,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onSwapEars: () -> Unit,
    onDisableDualPlay: () -> Unit,
    bottomGapDp: Dp,
    onControlsHeightMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(paneHeightDp)
            .clipToBounds()
    ) {
        // ── 1. Real Player Background Layers (Full bleed artwork, haze blur, gradient) ──
        PlayerBackgroundLayers(
            state = paneState,
            gradientColor = Color(paneState.gradientColor),
            expansionFractionProvider = { 1f },
            lyricsFractionProvider = { 0f },
            queueFractionProvider = { 0f },
            modifier = Modifier.fillMaxSize(),
        )

        // ── 2. Foreground Content (Anchored to Bottom) ────────────────────────
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Upper artwork area: on top pane, detects swipe down to collapse
            if (isTopPane) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 18f) {
                                    onCollapseClick()
                                }
                            }
                        }
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Controls Block Container (Anchored at the bottom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
                    .widthIn(max = 420.dp)
                    .align(Alignment.CenterHorizontally)
                    .onSizeChanged { onControlsHeightMeasured(it.height) }
            ) {
                // (a) Metadata Row: Title, Artist, Ear Buttons (26dp, 3dp gap), Heart, 3-dots
                PlayerMetadata(
                    title = title,
                    artist = artist,
                    state = paneState,
                    onAction = onAction,
                    onMoreClick = { showOptionsMenu = true },
                    isVisible = true,
                    enabled = true,
                    likeEnabled = isLikeEnabled,
                    earButtons = {
                        FullPlayerToolbarEarButtons(
                            currentEar = currentEar,
                            disabledEar = disabledEar,
                            onEarSelected = onEarSelected,
                            circleSize = 26.dp,
                            gap = 3.dp,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                )

                Spacer(modifier = Modifier.height(FullPlayerControlsDefaults.MetadataToSeekbarGap))

                // (b) Controls Row with isolated position polling
                PaneControlsRow(
                    isPlaying = isPlaying,
                    positionFlow = positionFlow,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    onPlayPause = onPlayPause,
                    onPrev = onPrev,
                    onNext = onNext,
                    isNextEnabled = isNextEnabled,
                    isPrevEnabled = isPrevEnabled,
                    shuffleContent = shuffleContent,
                    repeatContent = repeatContent,
                    paneState = paneState,
                    onAction = onAction,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // (c) Bottom Bar: Lyrics & Queue
                PlayerBottomBar(
                    state = paneState,
                    onAction = onAction,
                    onOpenQueue = onOpenQueue,
                    enabled = isBottomBarEnabled,
                )

                // (d) Bottom spacing: 16dp for top pane, nav bar padding for bottom pane
                Spacer(modifier = Modifier.height(bottomGapDp))
            }
        }

        // ── 3. In-Pane Options Menu Dialog ────────────────────────────────────
        if (showOptionsMenu) {
            DualPlayPaneOptionsMenu(
                volume = volume,
                onVolumeChange = onVolumeChange,
                onSwapEars = onSwapEars,
                onDisableDualPlay = onDisableDualPlay,
                onOpenSettings = onOpenSettingsMenu?.let { callback -> { callback(PlayerMenuScreen.SETTINGS) } },
                onDismiss = { showOptionsMenu = false },
            )
        }
    }
}

/**
 * Recomposes ONLY when positionFlow ticks or isPlaying changes,
 * preventing outer pane and background from recomposing on position ticks.
 */
@Composable
private fun PaneControlsRow(
    isPlaying: Boolean,
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    isNextEnabled: Boolean,
    isPrevEnabled: Boolean,
    shuffleContent: @Composable (() -> Unit)?,
    repeatContent: @Composable (() -> Unit)?,
    paneState: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
) {
    val position by positionFlow.collectAsStateWithLifecycle(0L)

    val resolvedShuffleContent: @Composable () -> Unit = shuffleContent ?: {
        val isShuffleActive = paneState.shuffleState != "off"
        val shuffleColor = if (isShuffleActive) Color(paneState.vibrantColor) else Color.White.copy(alpha = 0.5f)
        val shuffleIcon = when (paneState.shuffleState) {
            "smart" -> R.drawable.ic_shuffle_mix
            else -> R.drawable.ic_shuffle
        }
        Box(
            modifier = Modifier
                .bounceClick(pressedScale = 0.86f) {
                    onAction(PlayerAction.Shuffle)
                }
                .size(44.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = shuffleIcon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(shuffleColor),
            )
        }
    }

    val resolvedRepeatContent: @Composable () -> Unit = repeatContent ?: {
        val isRepeatActive = paneState.repeatState != "off"
        val repeatColor = if (isRepeatActive) Color(paneState.vibrantColor) else Color.White.copy(alpha = 0.5f)
        val repeatIcon = when (paneState.repeatState) {
            "one" -> R.drawable.ic_repeat_one
            "all", "on" -> R.drawable.ic_repeat_on
            else -> R.drawable.ic_repeat
        }
        Box(
            modifier = Modifier
                .bounceClick(pressedScale = 0.86f) {
                    onAction(PlayerAction.Repeat)
                }
                .size(44.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = repeatIcon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(repeatColor),
            )
        }
    }

    PlayerControls(
        isPlaying = isPlaying,
        position = position,
        duration = durationMs,
        onSeek = onSeek,
        onPlayPause = onPlayPause,
        onPrev = onPrev,
        onNext = onNext,
        isNextEnabled = isNextEnabled,
        isPrevEnabled = isPrevEnabled,
        shuffleContent = resolvedShuffleContent,
        repeatContent = resolvedRepeatContent,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 3-Dot Options Dialog for each pane in Dual Play split view.
 */
@Composable
private fun DualPlayPaneOptionsMenu(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onSwapEars: () -> Unit,
    onDisableDualPlay: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val haptics = LocalYumaHaptics.current
    val fontFamily = LocalArchiveTuneFontFamily.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0181820))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Text(
                    text = "Pane Options",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                )

                // Volume row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.volume_up),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Volume",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = fontFamily,
                            )
                        }
                        Text(
                            text = "${(volume * 100).roundToInt()}%",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 13.sp,
                            fontFamily = fontFamily,
                        )
                    }
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Swap ears button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.click()
                            onSwapEars()
                            onDismiss()
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.restore),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Swap ears",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = fontFamily,
                    )
                }

                // Player settings (if available)
                if (onOpenSettings != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.click()
                                onDismiss()
                                onOpenSettings()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Settings",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = fontFamily,
                        )
                    }
                }

                // Turn off Dual Play button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.click()
                            onDismiss()
                            onDisableDualPlay()
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Turn off Dual Play",
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = fontFamily,
                    )
                }
            }
        }
    }
}
