/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.lyrics_0

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.dualplay.DualPlaySheetTarget
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.player.player_0.CapsuleDefaults
import moe.rukamori.archivetune.ui.player.player_0.OverlayCapsuleShell
import moe.rukamori.archivetune.ui.player.player_0.RollingArtwork
import moe.rukamori.archivetune.ui.player.player_0.RollingArtworkMath
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.SoftTextShadow

@Composable
fun LyricsHeader(
    state: PlayerUiState,
    animateProgressProvider: () -> Float,
    onCloseClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    sheetTarget: DualPlaySheetTarget = DualPlaySheetTarget.PLAYER_A,
    mediaMetadataOverride: MediaMetadata? = null,
    playerOverride: Player? = null,
) {
    // Spring button click responses
    val closeInteractionSource = remember { MutableInteractionSource() }
    val closePressed by closeInteractionSource.collectIsPressedAsState()
    val closeScale by animateFloatAsState(if (closePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    val moreInteractionSource = remember { MutableInteractionSource() }
    val morePressed by moreInteractionSource.collectIsPressedAsState()
    val moreScale by animateFloatAsState(if (morePressed) 0.92f else 1f, spring(dampingRatio = 0.5f))

    // Isolated vinyl record spin animation (15s continuous)
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.isPlaying, isVisible) {
        if (state.isPlaying && isVisible) {
            while (true) {
                val currentDegree = rotation.value % 360f
                rotation.snapTo(currentDegree)
                rotation.animateTo(
                    targetValue = currentDegree + 360f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing)
                )
            }
        }
    }

    val playerConnection = LocalPlayerConnection.current
    val currentMediaMetadata by (playerConnection?.mediaMetadata ?: MutableStateFlow(null)).collectAsState()
    val defaultQueueIndex by (playerConnection?.currentMediaItemIndex ?: MutableStateFlow(-1)).collectAsState()

    val currentMediaId = when {
        mediaMetadataOverride?.id?.isNotBlank() == true -> mediaMetadataOverride.id
        playerOverride?.currentMediaItem?.mediaId?.isNotBlank() == true -> playerOverride.currentMediaItem?.mediaId ?: ""
        sheetTarget == DualPlaySheetTarget.PLAYER_A && !currentMediaMetadata?.id.isNullOrBlank() -> currentMediaMetadata?.id ?: ""
        else -> state.trackUrl.takeIf { it.isNotBlank() } ?: ""
    }

    val currentQueueIndex = if (playerOverride != null) playerOverride.currentMediaItemIndex else defaultQueueIndex

    val sheetProgress = animateProgressProvider()
    val isSheetVisible = isVisible && sheetProgress > 0.05f

    var wasSheetVisible by remember { mutableStateOf(false) }
    var isFirstComposition by remember { mutableStateOf(true) }
    var lastMediaId by remember { mutableStateOf<String?>(null) }
    var lastSheetTarget by remember { mutableStateOf(sheetTarget) }
    var previousQueueIndex by remember { mutableIntStateOf(-1) }

    val isTargetSwitched = sheetTarget != lastSheetTarget

    val isForward = remember(currentMediaId) {
        RollingArtworkMath.isForwardDirection(previousQueueIndex, currentQueueIndex)
    }

    val allowAnimation = remember(currentMediaId, isSheetVisible, isTargetSwitched) {
        RollingArtworkMath.shouldAnimateLyricsRoll(
            isFirstComposition = isFirstComposition,
            isSheetVisible = isSheetVisible,
            wasSheetVisible = wasSheetVisible,
            isTargetSwitched = isTargetSwitched,
            animationsDisabled = false,
            previousMediaId = lastMediaId,
            currentMediaId = currentMediaId,
        )
    }

    SideEffect {
        isFirstComposition = false
        wasSheetVisible = isSheetVisible
        lastMediaId = currentMediaId
        lastSheetTarget = sheetTarget
        previousQueueIndex = currentQueueIndex
    }

    OverlayCapsuleShell(
        isBlurBackgroundEnabled = state.isBlurBackgroundEnabled,
        darkMutedColor = state.darkMutedColor,
        modifier = modifier,
        progressProvider = animateProgressProvider,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(CapsuleDefaults.Shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = closeScale; scaleY = closeScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = closeInteractionSource, indication = null) { onCloseClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.ic_collapse), contentDescription = stringResource(R.string.collapse), modifier = Modifier.size(20.dp))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RollingArtwork(
                    mediaId = currentMediaId,
                    model = state.coverUrl,
                    placeholderResId = state.placeholderResId,
                    size = 40.dp,
                    slotPaddingExtra = 10.dp,
                    isForward = isForward,
                    isRollingAllowed = allowAnimation,
                    spinRotationProvider = { rotation.value },
                    contentDescription = stringResource(R.string.mini_album_art),
                )

                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    MarqueeText(
                        text = state.title,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = SoftTextShadow
                        ),
                        maxLines = 1,
                        modifier = Modifier,
                        isVisible = isVisible
                    )

                    MarqueeText(
                        text = state.artist,
                        style = TextStyle(
                            color = Color(0xD9FFFFFF),
                            fontSize = 12.sp,
                            shadow = SoftTextShadow
                        ),
                        maxLines = 1,
                        modifier = Modifier,
                        isVisible = isVisible
                    )
                }
            }

            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = moreScale; scaleY = moreScale }
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = moreInteractionSource, indication = null) { onMoreClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(painter = painterResource(id = R.drawable.ic_more), contentDescription = stringResource(R.string.more_label), modifier = Modifier.size(20.dp))
            }
        }
    }
}
