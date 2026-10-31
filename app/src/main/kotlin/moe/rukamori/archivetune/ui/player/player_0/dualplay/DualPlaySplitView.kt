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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.player.player_0.sett.CompactMenuRow
import moe.rukamori.archivetune.ui.player.player_0.sett.DownloadMenuContent
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.SpeedDialPinType
import moe.rukamori.archivetune.utils.parseSpeedDialPins
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin
import moe.rukamori.archivetune.extensions.toMediaMetadata
import moe.rukamori.archivetune.playback.dualplay.DualPlaySheetTarget
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.player.player_0.dualplay.rememberPaneSourceA
import moe.rukamori.archivetune.ui.player.player_0.dualplay.rememberPaneSourceB
import moe.rukamori.archivetune.ui.player.player_0.dualplay.PaneSource
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.utils.bounceClick
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

private val zeroWidthRegex = Regex("[\\u200B-\\u200D\\uFEFF\\u2060\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

private fun sanitizeTrackTitle(title: String): String {
    return title.replace(zeroWidthRegex, "").trim()
}

@Composable
fun DualPlaySplitView(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    onSeekA: (Float) -> Unit,
    onCollapseClick: () -> Unit = {},
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenQueueB: () -> Unit = {},
    onOpenLyricsA: () -> Unit = {},
    onOpenLyricsB: () -> Unit = {},
    lyricsFractionProvider: () -> Float = { 0f },
    queueFractionProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val haptics = LocalYumaHaptics.current
    val playerConnection = LocalPlayerConnection.current
    val engine = playerConnection?.dualPlayEngine ?: return

    val dualState by engine.state.collectAsState(DualPlayState())

    // ── Song B state & dynamic palette extraction ──────────────────────────────
    val itemB = dualState.playerBMediaItem
    val rawTitleB = itemB?.mediaMetadata?.title?.toString() ?: "(No Song B)"
    val titleB = remember(rawTitleB) {
        val codePointsHex = rawTitleB.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }
        Timber.tag("DualPlay").d("Title B raw code points: %s | text='%s'", codePointsHex, rawTitleB)
        sanitizeTrackTitle(rawTitleB)
    }
    val artistB = itemB?.mediaMetadata?.artist?.toString() ?: ""
    val artworkUriB = itemB?.mediaMetadata?.artworkUri?.toString()

    val titleA = remember(state.title) {
        sanitizeTrackTitle(state.title)
    }

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

    val stateA = remember(state, titleA) {
        state.copy(
            title = titleA,
            isImmersiveEnabled = true,
            isLyricsVisible = false,
            isQueueVisible = false,
        )
    }

    val stateB = remember(
        itemB,
        titleB,
        vibrantColorB,
        gradientColorB,
        dualState.playerBIsPlaying,
        dualState.playerBDuration,
        dualState.playerBIsLiked,
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
            isLiked = dualState.playerBIsLiked,
            vibrantColor = vibrantColorB,
            gradientColor = gradientColorB,
            isImmersiveEnabled = true,
            isLyricsVisible = false,
            isQueueVisible = false,
        )
    }

    val metadataA = playerConnection?.mediaMetadata?.value
    val paneSourceA = rememberPaneSourceA(
        state = stateA,
        queueState = QueueUiState(),
        playbackProgress = playbackProgress,
        mediaMetadata = metadataA,
        onAction = onAction,
        onSeek = { onSeekA(it) },
    )

    val metadataB = remember(itemB, dualState.playerBDuration) {
        itemB?.toMediaMetadata(dualState.playerBDuration)
    }

    val context = LocalContext.current
    val paneSourceB = rememberPaneSourceB(
        baseState = stateB,
        dualState = dualState,
        engine = engine,
        vibrantColorB = vibrantColorB,
        gradientColorB = gradientColorB,
        mediaMetadataB = metadataB,
        onOpenLyricsB = onOpenLyricsB,
        onOpenQueueB = onOpenQueueB,
        onShareTrack = { url ->
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                putExtra(android.content.Intent.EXTRA_TEXT, url)
                type = "text/plain"
            }
            context.startActivity(
                android.content.Intent.createChooser(sendIntent, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        onNavigate = { _ ->
            onCollapseClick()
        }
    )

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

        val lFraction = lyricsFractionProvider().coerceIn(0f, 1f)
        val qFraction = queueFractionProvider().coerceIn(0f, 1f)
        val lyricsForegroundAlpha = (1f - lFraction).coerceIn(0f, 1f)
        // Bottom pane is physically covered by the queue sheet in half state (q up to 0.45f)
        val bottomPaneQueueAlpha = (1f - (qFraction / 0.35f)).coerceIn(0f, 1f)
        // Top pane is covered when expanding from half (0.45f) to full (1.0f)
        val topPaneQueueAlpha = if (qFraction <= 0.45f) 1f else (1f - (qFraction - 0.45f) / 0.13f).coerceIn(0f, 1f)

        val topForegroundAlpha = minOf(lyricsForegroundAlpha, topPaneQueueAlpha)
        val bottomForegroundAlpha = minOf(lyricsForegroundAlpha, bottomPaneQueueAlpha)
        val dividerAlpha = minOf(lyricsForegroundAlpha, (1f - (qFraction / 0.25f)).coerceIn(0f, 1f))

        Column(modifier = Modifier.fillMaxSize()) {
            // ── TOP PANE: Song A ──────────────────────────────────────────────
            ImmersiveNowPlayingPane(
                paneHeightDp = topPaneHeightDp,
                paneSource = paneSourceA,
                paneState = stateA,
                title = titleA,
                artist = state.artist,
                isPlaying = state.isPlaying,
                positionFlow = playbackProgress,
                durationMs = state.durationMs,
                currentEar = dualState.earSelectionA,
                disabledEar = dualState.earSelectionB,
                onEarSelected = { engine.setEarA(it) },
                isLikeEnabled = true,
                onAction = { action ->
                    if (action is PlayerAction.Lyrics) {
                        onOpenLyricsA()
                    } else {
                        onAction(action)
                    }
                },
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
                controlsHeightPx = controlsHeightPx,
                foregroundAlphaProvider = { topForegroundAlpha },
            )

            // ── BOTTOM PANE: Song B ───────────────────────────────────────────
            val isRepeatOneB = dualState.playerBRepeatMode == Player.REPEAT_MODE_ONE
            val isRepeatAllB = dualState.playerBRepeatMode == Player.REPEAT_MODE_ALL
            val repeatIconB = when {
                isRepeatOneB -> R.drawable.ic_repeat_one
                isRepeatAllB -> R.drawable.ic_repeat_on
                else -> R.drawable.ic_repeat
            }
            val repeatTintB = if (isRepeatOneB || isRepeatAllB) Color(vibrantColorB) else Color.White.copy(alpha = 0.5f)

            val hasNextItemB = dualState.playerBQueueWindows.size > 1 || (playerConnection.dualPlayEngine.getPlayerB()?.hasNextMediaItem() == true)

            ImmersiveNowPlayingPane(
                paneHeightDp = bottomPaneHeightDp,
                paneSource = paneSourceB,
                paneState = stateB,
                title = titleB,
                artist = artistB,
                isPlaying = dualState.playerBIsPlaying,
                positionFlow = engine.playerBPosition,
                durationMs = dualState.playerBDuration,
                currentEar = dualState.earSelectionB,
                disabledEar = dualState.earSelectionA,
                onEarSelected = { engine.setEarB(it) },
                isLikeEnabled = true,
                onAction = { action ->
                    when (action) {
                        is PlayerAction.Like, is PlayerAction.ToggleLike -> {
                            engine.toggleLikeB()
                        }
                        is PlayerAction.Lyrics -> {
                            onOpenLyricsB()
                        }
                        else -> {}
                    }
                },
                onSeek = { engine.seekB(it) },
                onPlayPause = {
                    if (dualState.playerBIsPlaying) engine.pauseB() else engine.playB()
                },
                onPrev = { engine.prevB() },
                onNext = { engine.nextB() },
                isNextEnabled = hasNextItemB,
                isPrevEnabled = true,
                shuffleContent = {
                    val isShuffleActiveB = dualState.playerBShuffleModeEnabled
                    val shuffleColorB = if (isShuffleActiveB) Color(vibrantColorB) else Color.White.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .bounceClick(pressedScale = 0.86f) {
                                engine.toggleShuffleB()
                            }
                            .size(44.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_shuffle),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(shuffleColorB),
                        )
                    }
                },
                repeatContent = {
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
                isBottomBarEnabled = true,
                onOpenQueue = onOpenQueueB,
                isTopPane = false,
                onCollapseClick = {},
                onOpenSettingsMenu = onOpenSettingsMenu,
                volume = dualState.volumeB,
                onVolumeChange = { engine.setVolumeB(it) },
                onSwapEars = { engine.swapEars() },
                onDisableDualPlay = { engine.disable() },
                bottomGapDp = navBarBottomDp + 8.dp,
                onControlsHeightMeasured = { if (it > controlsHeightPx) controlsHeightPx = it },
                controlsHeightPx = controlsHeightPx,
                foregroundAlphaProvider = { bottomForegroundAlpha },
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
                .graphicsLayer {
                    alpha = dividerAlpha
                }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    enabled = dividerAlpha >= 0.95f,
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
                .pointerInput(dividerAlpha >= 0.95f) {
                    if (dividerAlpha >= 0.95f) {
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
                    }
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
    paneSource: PaneSource,
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
    controlsHeightPx: Int = 0,
    foregroundAlphaProvider: () -> Float = { 1f },
    modifier: Modifier = Modifier,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val paneHeightPx = with(density) { paneHeightDp.toPx() }
    val effectiveControlsHeightPx = if (controlsHeightPx > 0) {
        controlsHeightPx.toFloat()
    } else {
        with(density) { 260.dp.toPx() }
    }
    val controlsTopPx = (paneHeightPx - effectiveControlsHeightPx).coerceAtLeast(0f)
    val clearArtworkFadeEndY = controlsTopPx + with(density) { 16.dp.toPx() }

    val isForegroundInteractive by remember(foregroundAlphaProvider) {
        derivedStateOf { foregroundAlphaProvider() >= 0.95f }
    }

    var localGradientColor by remember(paneState.gradientColor) { mutableIntStateOf(paneState.gradientColor) }
    var localVibrantColor by remember(paneState.vibrantColor) { mutableIntStateOf(paneState.vibrantColor) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(paneHeightDp)
            .clipToBounds()
    ) {
        // ── 1. Real Player Background Layers (Full bleed artwork, haze blur, gradient) ──
        PlayerBackgroundLayers(
            state = paneState.copy(gradientColor = localGradientColor, vibrantColor = localVibrantColor),
            gradientColor = Color(localGradientColor),
            expansionFractionProvider = { 1f },
            lyricsFractionProvider = { 0f },
            queueFractionProvider = { 0f },
            onColorsExtracted = { vibrant, _, gradient ->
                localVibrantColor = vibrant
                localGradientColor = gradient
            },
            clearArtworkFadeEndY = clearArtworkFadeEndY,
            modifier = Modifier.fillMaxSize(),
        )

        // ── 2. Foreground Content (Anchored to Bottom) ────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = foregroundAlphaProvider()
                },
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Upper artwork area: on top pane, detects swipe down to collapse
            if (isTopPane && isForegroundInteractive) {
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
                    onAction = { if (isForegroundInteractive) onAction(it) },
                    onMoreClick = { if (isForegroundInteractive) showOptionsMenu = true },
                    isVisible = true,
                    enabled = isForegroundInteractive,
                    likeEnabled = isLikeEnabled && isForegroundInteractive,
                    earButtons = {
                        FullPlayerToolbarEarButtons(
                            currentEar = currentEar,
                            disabledEar = disabledEar,
                            onEarSelected = { if (isForegroundInteractive) onEarSelected(it) },
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
                    onSeek = { if (isForegroundInteractive) onSeek(it) },
                    onPlayPause = { if (isForegroundInteractive) onPlayPause() },
                    onPrev = { if (isForegroundInteractive) onPrev() },
                    onNext = { if (isForegroundInteractive) onNext() },
                    isNextEnabled = isNextEnabled && isForegroundInteractive,
                    isPrevEnabled = isPrevEnabled && isForegroundInteractive,
                    shuffleContent = shuffleContent,
                    repeatContent = repeatContent,
                    paneState = paneState,
                    onAction = { if (isForegroundInteractive) onAction(it) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // (c) Bottom Bar: Lyrics & Queue
                PlayerBottomBar(
                    state = paneState,
                    onAction = { if (isForegroundInteractive) onAction(it) },
                    onOpenQueue = { if (isForegroundInteractive) onOpenQueue() },
                    enabled = isBottomBarEnabled && isForegroundInteractive,
                )

                // (d) Bottom spacing: 16dp for top pane, nav bar padding for bottom pane
                Spacer(modifier = Modifier.height(bottomGapDp))
            }
        }

        // ── 3. In-Pane Options Menu Dialog ────────────────────────────────────
        if (showOptionsMenu) {
            DualPlayPaneOptionsMenu(
                paneSource = paneSource,
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

private enum class DualPlayMenuSubScreen {
    MAIN,
    DETAILS,
    DOWNLOAD,
}

/**
 * 3-Dot Options Dialog for each pane in Dual Play split view.
 * Both Pane A and Pane B menus are built from this single function so they cannot diverge.
 * Track actions operate on the song of this pane via [paneSource].
 */
@Composable
private fun DualPlayPaneOptionsMenu(
    paneSource: PaneSource,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onSwapEars: () -> Unit,
    onDisableDualPlay: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val haptics = LocalYumaHaptics.current
    val fontFamily = LocalArchiveTuneFontFamily.current
    var currentSubScreen by remember { mutableStateOf(DualPlayMenuSubScreen.MAIN) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    val state = paneSource.uiState
    val songId = state.trackUrl

    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(songId) { SpeedDialPin(type = SpeedDialPinType.SONG, id = songId) }
    val isPinned = remember(speedDialPins, songPin) {
        speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
    }

    val paneLabel = if (paneSource.target == DualPlaySheetTarget.PLAYER_A) "Song A Options" else "Song B Options"

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0181820))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            when (currentSubScreen) {
                DualPlayMenuSubScreen.DETAILS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentSubScreen = DualPlayMenuSubScreen.MAIN }
                                .padding(bottom = 12.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.track_details),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily,
                            )
                        }
                        if (songId.isNotBlank()) {
                            ShowMediaInfo(videoId = songId)
                        }
                    }
                }
                DualPlayMenuSubScreen.DOWNLOAD -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentSubScreen = DualPlayMenuSubScreen.MAIN }
                                .padding(bottom = 12.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.download),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily,
                            )
                        }
                        DownloadMenuContent(
                            state = state,
                            onDismissRequest = onDismiss,
                        )
                    }
                }
                DualPlayMenuSubScreen.MAIN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = paneLabel,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = fontFamily,
                                )
                                if (state.title.isNotBlank()) {
                                    Text(
                                        text = "${state.title} • ${state.artist}",
                                        color = Color.White.copy(alpha = 0.70f),
                                        fontSize = 12.sp,
                                        fontFamily = fontFamily,
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (onOpenSettings != null) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            haptics.click()
                                            onDismiss()
                                            onOpenSettings()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings),
                                        contentDescription = "Settings",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

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
                                .padding(vertical = 8.dp, horizontal = 4.dp),
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

                        // ── Track Actions ──
                        val actionsCount = 6 +
                            (if (paneSource.hasArtistId) 1 else 0) +
                            (if (paneSource.hasAlbumId) 1 else 0)
                        var rowIndex = 0

                        // Share track
                        CompactMenuRow(
                            title = stringResource(R.string.share),
                            subtitle = "Share track link",
                            iconResId = R.drawable.ic_share,
                            onClick = {
                                onDismiss()
                                paneSource.onAction(PlayerAction.Share)
                            },
                            index = rowIndex++,
                            count = actionsCount,
                        )

                        // Add to playlist
                        CompactMenuRow(
                            title = stringResource(R.string.add_to_playlist_title),
                            subtitle = stringResource(R.string.add_to_playlist_desc),
                            iconResId = R.drawable.playlist_add,
                            onClick = {
                                showAddToPlaylistDialog = true
                            },
                            showArrow = true,
                            index = rowIndex++,
                            count = actionsCount,
                        )

                        // Download
                        CompactMenuRow(
                            title = stringResource(R.string.download),
                            subtitle = stringResource(R.string.save_track_offline),
                            iconResId = R.drawable.download,
                            onClick = {
                                currentSubScreen = DualPlayMenuSubScreen.DOWNLOAD
                            },
                            showArrow = true,
                            index = rowIndex++,
                            count = actionsCount,
                        )

                        // Track Details
                        CompactMenuRow(
                            title = stringResource(R.string.track_details),
                            subtitle = stringResource(R.string.track_details_desc),
                            iconResId = R.drawable.ic_about,
                            onClick = {
                                currentSubScreen = DualPlayMenuSubScreen.DETAILS
                            },
                            showArrow = true,
                            index = rowIndex++,
                            count = actionsCount,
                        )

                        // View Artist (if available)
                        if (paneSource.hasArtistId) {
                            CompactMenuRow(
                                title = stringResource(R.string.view_artist),
                                subtitle = state.artist,
                                iconResId = R.drawable.artist,
                                onClick = {
                                    onDismiss()
                                    paneSource.onAction(PlayerAction.OpenArtist)
                                },
                                showArrow = true,
                                index = rowIndex++,
                                count = actionsCount,
                            )
                        }

                        // View Album (if available)
                        if (paneSource.hasAlbumId) {
                            CompactMenuRow(
                                title = stringResource(R.string.view_album),
                                subtitle = state.album.orEmpty(),
                                iconResId = R.drawable.album,
                                onClick = {
                                    onDismiss()
                                    paneSource.onAction(PlayerAction.OpenAlbum)
                                },
                                showArrow = true,
                                index = rowIndex++,
                                count = actionsCount,
                            )
                        }

                        // Start Radio
                        CompactMenuRow(
                            title = stringResource(R.string.start_radio_title),
                            subtitle = stringResource(R.string.start_radio_desc),
                            iconResId = R.drawable.radio,
                            onClick = {
                                onDismiss()
                                paneSource.onAction(PlayerAction.StartRadio)
                            },
                            index = rowIndex++,
                            count = actionsCount,
                        )

                        // Pin to speed dial
                        CompactMenuRow(
                            title = if (isPinned) stringResource(R.string.unpin_track) else stringResource(R.string.pin_track),
                            subtitle = if (isPinned) stringResource(R.string.remove_from_speed_dial_title) else stringResource(R.string.pin_to_speed_dial_title),
                            iconResId = if (isPinned) R.drawable.bookmark_filled else R.drawable.bookmark,
                            isActive = isPinned,
                            activeIconTint = Color(state.vibrantColor),
                            onClick = {
                                val updated = toggleSpeedDialPin(speedDialPins, songPin)
                                onSpeedDialSongIdsChange(serializeSpeedDialPins(updated))
                            },
                            index = rowIndex++,
                            count = actionsCount,
                        )

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
                                .padding(vertical = 8.dp, horizontal = 4.dp),
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
    }

    if (showAddToPlaylistDialog && songId.isNotBlank()) {
        AddToPlaylistDialog(
            isVisible = showAddToPlaylistDialog,
            onGetSong = { listOf(songId) },
            onDismiss = { showAddToPlaylistDialog = false },
        )
    }
}
