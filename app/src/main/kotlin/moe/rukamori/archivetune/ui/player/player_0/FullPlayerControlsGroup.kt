/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerBottomBar
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.utils.bounceClick
import timber.log.Timber

internal object FullPlayerControlsDefaults {
    val MetadataToSeekbarGap = 8.dp
    val CompactMetadataToSeekbarGap = 4.dp
    val SeekbarToTransportGap = 14.dp
    val MaxControlsBlockLift = 24.dp
}

@Immutable
internal data class CompactedControlsLayout(
    val rootHeight: Float = 0f,
    val capsuleHeaderHeight: Float = 0f,
    val toolbarBottom: Float = 0f,
    val m0: Float = 0f,
    val b0: Float = 0f,
    val d: Float = 0f,
    val g: Float = 0f,
) {
    val isReady: Boolean
        get() = rootHeight > 0f && capsuleHeaderHeight > 0f && toolbarBottom > 0f && m0 > 0f && b0 > m0

    val bc: Float
        get() = b0 - d

    val gc: Float
        get() = bc - m0

    val qPeek: Float
        get() = capsuleHeaderHeight + 0.55f * (rootHeight - capsuleHeaderHeight)

    val deltaPeek: Float
        get() = if (isReady) minOf(0f, qPeek - g - bc) else 0f

    val deficit: Float
        get() = if (isReady) {
            maxOf(0f, toolbarBottom - (m0 + deltaPeek))
        } else {
            0f
        }

    val isFeasible: Boolean
        get() = isReady && deficit <= 0f

    val effectiveDeltaPeek: Float
        get() = if (isFeasible) deltaPeek else 0f
}

internal fun computeCompactedControlsLayout(
    rootHeight: Float,
    capsuleHeaderHeight: Float,
    toolbarBottom: Float,
    m0: Float,
    b0: Float,
    transportLift: Float,
    gapG: Float,
): CompactedControlsLayout {
    return CompactedControlsLayout(
        rootHeight = rootHeight,
        capsuleHeaderHeight = capsuleHeaderHeight,
        toolbarBottom = toolbarBottom,
        m0 = m0,
        b0 = b0,
        d = transportLift.coerceAtLeast(0f),
        g = gapG,
    )
}

@SuppressLint("SuspiciousIndentation")
@Composable
internal fun FullPlayerControlsGroup(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    slideOffset: () -> Float,
    controlsOffsetY: () -> Dp,
    queueFractionProvider: () -> Float,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    onOpenSettingsMenu: (PlayerMenuScreen) -> Unit,
    onOpenQueue: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    rootHeightPx: Float = 0f,
    capsuleHeaderHeightPx: Float = 0f,
    toolbarBottomInRootPx: Float = 0f,
    rootCoordinates: LayoutCoordinates? = null,
) {
    val density = LocalDensity.current
    val transportLiftDp = 10.dp
    val metadataShiftDp =
        FullPlayerControlsDefaults.MetadataToSeekbarGap -
                FullPlayerControlsDefaults.CompactMetadataToSeekbarGap

    val transportLiftPx = with(density) { transportLiftDp.toPx() }
    val metadataShiftPx = with(density) { metadataShiftDp.toPx() }
    val maxBlockLiftPx = with(density) { FullPlayerControlsDefaults.MaxControlsBlockLift.toPx() }
    val gapGPx = with(density) { SettingsDimensions.PlayerControlsVerticalGap.roundToPx().toFloat() }

    var m0Px by remember { mutableFloatStateOf(0f) }
    var b0Px by remember { mutableFloatStateOf(0f) }

    val layoutInfo = remember(
        rootHeightPx, capsuleHeaderHeightPx, toolbarBottomInRootPx,
        m0Px, b0Px, transportLiftPx, gapGPx
    ) {
        computeCompactedControlsLayout(
            rootHeight = rootHeightPx,
            capsuleHeaderHeight = capsuleHeaderHeightPx,
            toolbarBottom = toolbarBottomInRootPx,
            m0 = m0Px,
            b0 = b0Px,
            transportLift = transportLiftPx,
            gapG = gapGPx,
        )
    }

    LaunchedEffect(layoutInfo.isReady, layoutInfo.isFeasible, layoutInfo.deficit) {
        if (layoutInfo.isReady && !layoutInfo.isFeasible) {
            Timber.tag("FullPlayerGeometry").e(
                "Feasibility deficit: H=%.1f, C=%.1f, T=%.1f, M0=%.1f, B0=%.1f, d=%.1f, Qpeek=%.1f, g=%.1f, deficit=%.1f",
                layoutInfo.rootHeight, layoutInfo.capsuleHeaderHeight, layoutInfo.toolbarBottom,
                layoutInfo.m0, layoutInfo.b0, layoutInfo.d, layoutInfo.qPeek, layoutInfo.g, layoutInfo.deficit
            )
        }
    }

    val isMainControlsInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.57f }
    }
    val isLikeInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.4275f }
    }
    val isBottomBarInteractive by remember {
        derivedStateOf { queueFractionProvider() < 0.15f }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = SettingsDimensions.PlayerControlsHorizontalPadding)
            .widthIn(max = 420.dp)
            .offset { IntOffset(x = 0, y = controlsOffsetY().roundToPx()) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val root = rootCoordinates
                    if (root != null && root.isAttached && coords.isAttached) {
                        m0Px = root.localPositionOf(coords, Offset.Zero).y
                        b0Px = root.localPositionOf(coords, Offset(0f, coords.size.height.toFloat())).y
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val q = queueFractionProvider().coerceIn(0f, 1f)
                        val p = (q / 0.45f).coerceIn(0f, 1f)

                        translationY = p * layoutInfo.effectiveDeltaPeek
                        alpha = if (q <= 0.45f) {
                            1f
                        } else {
                            (1f - (q - 0.45f) / 0.13f).coerceIn(0f, 1f)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val q = queueFractionProvider().coerceIn(0f, 1f)
                            val p = (q / 0.45f).coerceIn(0f, 1f)
                            translationY = p * metadataShiftPx
                        }
                ) {
                    PlayerMetadata(
                        title = state.title,
                        artist = state.artist,
                        state = state,
                        onAction = onAction,
                        onMoreClick = { onOpenSettingsMenu(PlayerMenuScreen.SETTINGS) },
                        isVisible = isVisible,
                        enabled = isMainControlsInteractive,
                        likeFractionProvider = queueFractionProvider,
                        likeEnabled = isLikeInteractive,
                    )
                }

                Spacer(modifier = Modifier.height(FullPlayerControlsDefaults.MetadataToSeekbarGap))

                BoundPlayerControls(
                    state = state,
                    playbackProgress = playbackProgress,
                    onAction = onAction,
                    onSeek = onSeek,
                    onSeekStarted = onSeekStarted,
                    isMainControlsInteractive = isMainControlsInteractive,
                    transportLiftPx = transportLiftPx,
                    queueFractionProvider = queueFractionProvider,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val q = queueFractionProvider().coerceIn(0f, 1f)
                    val p = (q / 0.20f).coerceIn(0f, 1f)
                    alpha = (1f - p).coerceIn(0f, 1f)
                }
        ) {
            PlayerBottomBar(
                state = state,
                onAction = onAction,
                onOpenQueue = onOpenQueue,
                enabled = isBottomBarInteractive,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun BoundPlayerControls(
    state: PlayerUiState,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStarted: () -> Unit,
    isMainControlsInteractive: Boolean,
    transportLiftPx: Float,
    queueFractionProvider: () -> Float,
) {
    val currentProgress by playbackProgress.collectAsStateWithLifecycle()

    val isShuffleActive = state.shuffleState != "off"
    val shuffleColor = if (isShuffleActive) Color(state.vibrantColor) else Color.White.copy(alpha = 0.5f)
    val shuffleIcon = when (state.shuffleState) {
        "smart" -> R.drawable.ic_shuffle_mix
        else -> R.drawable.ic_shuffle
    }

    val isRepeatActive = state.repeatState != "off"
    val repeatColor = if (isRepeatActive) Color(state.vibrantColor) else Color.White.copy(alpha = 0.5f)
    val repeatIcon = when (state.repeatState) {
        "one" -> R.drawable.ic_repeat_one
        "all", "on" -> R.drawable.ic_repeat_on
        else -> R.drawable.ic_repeat
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val q = queueFractionProvider().coerceIn(0f, 1f)
                val p = (q / 0.45f).coerceIn(0f, 1f)
                translationY = -p * transportLiftPx
            }
    ) {
        PlayerControls(
            isPlaying = state.isPlaying,
            position = currentProgress,
            duration = state.durationMs,
            onSeek = { targetPositionMs ->
                onSeek(targetPositionMs.toFloat())
            },
            onPlayPause = { onAction(PlayerAction.PlayPause) },
            onPrev = { onAction(PlayerAction.Previous) },
            onNext = { onAction(PlayerAction.Next) },
            modifier = Modifier.fillMaxWidth(),
            onSeekStarted = onSeekStarted,
            shuffleContent = {
                Box(
                    modifier = Modifier
                        .bounceClick(pressedScale = 0.86f, enabled = isMainControlsInteractive) {
                            onAction(PlayerAction.Shuffle)
                        }
                        .size(44.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = shuffleIcon),
                        contentDescription = stringResource(R.string.shuffle),
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(shuffleColor),
                    )
                }
            },
            repeatContent = {
                Box(
                    modifier = Modifier
                        .bounceClick(pressedScale = 0.86f, enabled = isMainControlsInteractive) {
                            onAction(PlayerAction.Repeat)
                        }
                        .size(44.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = repeatIcon),
                        contentDescription = stringResource(R.string.repeat),
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(repeatColor),
                    )
                }
            }
        )
    }
}
