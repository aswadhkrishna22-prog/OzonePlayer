package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.player.player_0.buttons.MiniPlayerButtons
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.SoftTextShadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MiniPlayerContentInternal(
    state: PlayerUiState,
    expansionFractionProvider: () -> Float,
    playbackProgress: StateFlow<Long>,
    onAction: (PlayerAction) -> Unit,
    modifier: Modifier = Modifier,
    onMediaAreaClick: () -> Unit,
    isVisible: Boolean = true
) {
    val context = LocalContext.current
    var progressMs by remember { mutableLongStateOf(playbackProgress.value) }

    LaunchedEffect(isVisible, playbackProgress) {
        if (!isVisible) return@LaunchedEffect
        playbackProgress.collect { progressMs = it }
    }

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
    val mediaMetadata by (playerConnection?.mediaMetadata ?: MutableStateFlow(null)).collectAsState()
    val currentMediaItemIndex by (playerConnection?.currentMediaItemIndex ?: MutableStateFlow(-1)).collectAsState()
    val queueRestoreCompleted by (playerConnection?.queueRestoreCompleted ?: MutableStateFlow(true)).collectAsState()

    val currentMediaId = mediaMetadata?.id ?: state.trackUrl.takeIf { it.isNotBlank() } ?: ""

    val localAnimationsDisabled = LocalAnimationsDisabled.current
    val systemAnimationsDisabled = remember(context) {
        runCatching {
            !android.animation.ValueAnimator.areAnimatorsEnabled() ||
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    val animationsDisabled = localAnimationsDisabled || systemAnimationsDisabled

    var lastMediaId by remember { mutableStateOf<String?>(null) }
    var wasVisible by remember { mutableStateOf(false) }
    var isFirstComposition by remember { mutableStateOf(true) }
    var previousQueueIndex by remember { mutableIntStateOf(-1) }

    val isForward = remember(currentMediaId) {
        RollingArtworkMath.isForwardDirection(previousQueueIndex, currentMediaItemIndex)
    }

    val allowAnimation = remember(currentMediaId, isVisible, queueRestoreCompleted, animationsDisabled) {
        RollingArtworkMath.shouldAnimateRoll(
            isFirstComposition = isFirstComposition,
            isVisible = isVisible,
            wasVisible = wasVisible,
            queueRestoreCompleted = queueRestoreCompleted,
            animationsDisabled = animationsDisabled,
            previousMediaId = lastMediaId,
            currentMediaId = currentMediaId,
        )
    }

    SideEffect {
        isFirstComposition = false
        wasVisible = isVisible
        lastMediaId = currentMediaId
        previousQueueIndex = currentMediaItemIndex
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onMediaAreaClick
            )
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularWavyProgressIndicator(
                progress = {
                    val duration = state.durationMs
                    if (duration > 0L) {
                        (progressMs.toFloat() / duration).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxSize(),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            RollingArtwork(
                mediaId = currentMediaId,
                model = state.coverUrl,
                placeholderResId = state.placeholderResId,
                size = 40.dp,
                slotPaddingExtra = 10.dp,
                isForward = isForward,
                isRollingAllowed = allowAnimation,
                spinRotationProvider = { rotation.value }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp),
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
                isVisible = isVisible
            )

            MarqueeText(
                text = state.artist,
                style = TextStyle(
                    color = Color(0xE6FFFFFF),
                    fontSize = 12.sp,
                    shadow = SoftTextShadow
                ),
                maxLines = 1,
                isVisible = isVisible
            )
        }

        MiniPlayerButtons(
            state = state,
            onAction = onAction
        )
    }
}
