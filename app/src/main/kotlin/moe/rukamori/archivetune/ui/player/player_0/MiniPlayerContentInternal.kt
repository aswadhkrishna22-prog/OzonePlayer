package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.player.player_0.buttons.MiniPlayerButtons
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.SoftTextShadow

// ── Mini Player Rolling Artwork Constants ──────────────────────────────
private const val MINI_ROLL_DURATION_MS = 420
private val MiniRollEasing = FastOutSlowInEasing
private const val ARTWORK_CROSSFADE_DURATION_MS = 880
private val ArtworkSlotSize = 40.dp
private val RollClipWidth = 60.dp // 40dp artwork slot + 10dp on each side
private val RollDistance = 50.dp

@Immutable
internal class RollingArtworkTarget(
    val mediaId: String,
    val coverUrl: String,
    val placeholderResId: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RollingArtworkTarget) return false
        return mediaId == other.mediaId
    }

    override fun hashCode(): Int = mediaId.hashCode()
}

/**
 * Math utility for rolling without slipping and roll decision logic.
 */
internal object MiniRollMath {
    /**
     * Roll without slipping: rotation in degrees = (translationX / radius) * (180 / pi)
     */
    fun calculateRollRotation(translationX: Float, radius: Float): Float {
        if (radius <= 0f) return 0f
        return (translationX / radius) * (180f / Math.PI.toFloat())
    }

    fun isForwardDirection(previousIndex: Int, currentIndex: Int): Boolean {
        return if (previousIndex >= 0 && currentIndex >= 0 && previousIndex != currentIndex) {
            currentIndex > previousIndex
        } else {
            true // default to forward when unknown
        }
    }

    fun shouldAnimateRoll(
        isFirstComposition: Boolean,
        isVisible: Boolean,
        wasVisible: Boolean,
        queueRestoreCompleted: Boolean,
        animationsDisabled: Boolean,
        previousMediaId: String?,
        currentMediaId: String?,
    ): Boolean {
        return !isFirstComposition &&
            isVisible &&
            wasVisible &&
            queueRestoreCompleted &&
            !animationsDisabled &&
            !previousMediaId.isNullOrBlank() &&
            !currentMediaId.isNullOrBlank() &&
            currentMediaId != previousMediaId
    }
}

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
        MiniRollMath.isForwardDirection(previousQueueIndex, currentMediaItemIndex)
    }

    val allowAnimation = remember(currentMediaId, isVisible, queueRestoreCompleted, animationsDisabled) {
        MiniRollMath.shouldAnimateRoll(
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

    val currentArtworkTarget = remember(currentMediaId, state.coverUrl, state.placeholderResId) {
        RollingArtworkTarget(
            mediaId = currentMediaId,
            coverUrl = state.coverUrl,
            placeholderResId = state.placeholderResId,
        )
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

            Box(
                modifier = Modifier
                    .requiredSize(width = RollClipWidth, height = ArtworkSlotSize)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val radiusPx = with(density) { (ArtworkSlotSize / 2).toPx() }
                val rollDistancePx = with(density) { RollDistance.toPx() }

                AnimatedContent(
                    targetState = currentArtworkTarget,
                    transitionSpec = {
                        if (!allowAnimation) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(animationSpec = tween(MINI_ROLL_DURATION_MS, easing = MiniRollEasing)) togetherWith
                             fadeOut(animationSpec = tween(MINI_ROLL_DURATION_MS, easing = MiniRollEasing))).apply {
                                targetContentZIndex = 1f
                            }
                        }
                    },
                    label = "MiniRollingArtwork"
                ) { target ->
                    val rollProgress by transition.animateFloat(
                        transitionSpec = {
                            if (!allowAnimation) {
                                snap()
                            } else {
                                tween(durationMillis = MINI_ROLL_DURATION_MS, easing = MiniRollEasing)
                            }
                        },
                        label = "MiniRollProgress"
                    ) { enterExitState ->
                        when (enterExitState) {
                            EnterExitState.PreEnter -> 0f
                            EnterExitState.Visible -> 1f
                            EnterExitState.PostExit -> 2f
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(ArtworkSlotSize)
                            .graphicsLayer {
                                val progress = rollProgress
                                val (tx, a) = if (progress <= 1f) {
                                    val enterRemaining = 1f - progress
                                    val x = if (isForward) rollDistancePx * enterRemaining else -rollDistancePx * enterRemaining
                                    x to progress.coerceIn(0f, 1f)
                                } else {
                                    val exitFraction = progress - 1f
                                    val x = if (isForward) -rollDistancePx * exitFraction else rollDistancePx * exitFraction
                                    x to (1f - exitFraction).coerceIn(0f, 1f)
                                }
                                translationX = tx
                                rotationZ = MiniRollMath.calculateRollRotation(tx, radiusPx)
                                alpha = a
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val activeCoverUrl = if (target.mediaId == currentMediaId) state.coverUrl else target.coverUrl
                        MiniArtworkDisk(
                            coverUrl = activeCoverUrl,
                            placeholderResId = target.placeholderResId,
                            spinRotationProvider = { rotation.value }
                        )
                    }
                }
            }
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

@Composable
private fun MiniArtworkDisk(
    coverUrl: String,
    placeholderResId: Int,
    spinRotationProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val targetUrl = coverUrl.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .size(ArtworkSlotSize)
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = spinRotationProvider() }
            .clip(CircleShape)
    ) {
        if (targetUrl == null) {
            Image(
                painter = painterResource(id = placeholderResId),
                contentDescription = stringResource(R.string.mini_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val request = remember(targetUrl) {
                ImageRequest.Builder(context)
                    .data(targetUrl)
                    .size(96)
                    .crossfade(ARTWORK_CROSSFADE_DURATION_MS)
                    .allowHardware(true)
                    .memoryCacheKey("mini:$targetUrl")
                    .diskCacheKey("mini:$targetUrl")
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.mini_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = placeholderResId),
                error = painterResource(id = placeholderResId),
            )
        }
    }
}
