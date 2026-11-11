/*
 * Ozone Player (2026) | Rolling Artwork Shared Component
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R

// ── Rolling Artwork Constants ──────────────────────────────────────────
const val ROLLING_ARTWORK_DURATION_MS = 830
val RollingArtworkEasing = FastOutSlowInEasing
const val ARTWORK_CROSSFADE_DURATION_MS = 880
val DefaultArtworkSlotSize = 40.dp
val DefaultSlotPaddingExtra = 10.dp

// Backward-compatible alias for mini player constants
const val MINI_ROLL_DURATION_MS = ROLLING_ARTWORK_DURATION_MS
val MiniRollEasing = RollingArtworkEasing

/**
 * Keyed strictly by mediaId so same-song URL refreshes do not trigger roll animation.
 */
@Immutable
class RollingArtworkTarget(
    val mediaId: String,
    val model: Any?,
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
object RollingArtworkMath {
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
        queueRestoreCompleted: Boolean = true,
        animationsDisabled: Boolean = false,
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

    fun shouldAnimateLyricsRoll(
        isFirstComposition: Boolean,
        isSheetVisible: Boolean,
        wasSheetVisible: Boolean,
        isTargetSwitched: Boolean = false,
        animationsDisabled: Boolean = false,
        previousMediaId: String?,
        currentMediaId: String?,
    ): Boolean {
        return !isFirstComposition &&
            isSheetVisible &&
            wasSheetVisible &&
            !isTargetSwitched &&
            !animationsDisabled &&
            !previousMediaId.isNullOrBlank() &&
            !currentMediaId.isNullOrBlank() &&
            currentMediaId != previousMediaId
    }
}

/**
 * Forwarding alias for backwards compatibility with tests and callers.
 */
object MiniRollMath {
    fun calculateRollRotation(translationX: Float, radius: Float): Float =
        RollingArtworkMath.calculateRollRotation(translationX, radius)

    fun isForwardDirection(previousIndex: Int, currentIndex: Int): Boolean =
        RollingArtworkMath.isForwardDirection(previousIndex, currentIndex)

    fun shouldAnimateRoll(
        isFirstComposition: Boolean,
        isVisible: Boolean,
        wasVisible: Boolean,
        queueRestoreCompleted: Boolean = true,
        animationsDisabled: Boolean = false,
        previousMediaId: String?,
        currentMediaId: String?,
    ): Boolean = RollingArtworkMath.shouldAnimateRoll(
        isFirstComposition = isFirstComposition,
        isVisible = isVisible,
        wasVisible = wasVisible,
        queueRestoreCompleted = queueRestoreCompleted,
        animationsDisabled = animationsDisabled,
        previousMediaId = previousMediaId,
        currentMediaId = currentMediaId,
    )
}

/**
 * Reusable rolling artwork component shared across Mini Player and Lyrics Sheet.
 *
 * Enforces:
 * - Physics: roll without slipping (\u03b8 = tx / radius * 180 / \u03c0).
 * - Clipping: clipped to [size + slotPaddingExtra * 2] so disks never draw over adjacent text.
 * - Direction: forward rolls left out / right in; backward mirrored.
 * - Suppression: no animation on first composition, visibility changes, or same song refreshes.
 * - Instant swap when system animator scale is 0 or LocalAnimationsDisabled is true.
 * - Asynchronous placeholder and crossfade without delaying the roll.
 * - Seamless continuation of disk spinning (via [spinRotationProvider]).
 */
@Composable
fun RollingArtwork(
    mediaId: String,
    model: Any?,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.drawable.mascot_1,
    size: Dp = DefaultArtworkSlotSize,
    slotPaddingExtra: Dp = DefaultSlotPaddingExtra,
    isForward: Boolean = true,
    isRollingAllowed: Boolean = true,
    spinRotationProvider: () -> Float = { 0f },
    contentDescription: String? = null,
) {
    val context = LocalContext.current
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

    var isFirstComposition by remember { mutableStateOf(true) }
    var lastMediaId by remember { mutableStateOf<String?>(null) }

    val shouldAnimate = !isFirstComposition &&
        !animationsDisabled &&
        isRollingAllowed &&
        !lastMediaId.isNullOrBlank() &&
        mediaId.isNotBlank() &&
        mediaId != lastMediaId

    SideEffect {
        isFirstComposition = false
        lastMediaId = mediaId
    }

    val currentArtworkTarget = remember(mediaId) {
        RollingArtworkTarget(
            mediaId = mediaId,
            model = model,
            placeholderResId = placeholderResId,
        )
    }

    val clipWidth = size + slotPaddingExtra * 2
    val rollDistance = size + slotPaddingExtra

    Box(
        modifier = modifier
            .size(size)
            .wrapContentSize(align = Alignment.Center, unbounded = true),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .requiredSize(width = clipWidth, height = size)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val radiusPx = with(density) { (size / 2).toPx() }
            val rollDistancePx = with(density) { rollDistance.toPx() }

            AnimatedContent(
                targetState = currentArtworkTarget,
                transitionSpec = {
                    if (!shouldAnimate) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(animationSpec = tween(ROLLING_ARTWORK_DURATION_MS, easing = RollingArtworkEasing)) togetherWith
                         fadeOut(animationSpec = tween(ROLLING_ARTWORK_DURATION_MS, easing = RollingArtworkEasing))).apply {
                            targetContentZIndex = 1f
                        }
                    }
                },
                label = "RollingArtworkContent"
            ) { target ->
                val rollProgress by transition.animateFloat(
                    transitionSpec = {
                        if (!shouldAnimate) {
                            snap()
                        } else {
                            tween(durationMillis = ROLLING_ARTWORK_DURATION_MS, easing = RollingArtworkEasing)
                        }
                    },
                    label = "RollProgress"
                ) { enterExitState ->
                    when (enterExitState) {
                        EnterExitState.PreEnter -> 0f
                        EnterExitState.Visible -> 1f
                        EnterExitState.PostExit -> 2f
                    }
                }

                Box(
                    modifier = Modifier
                        .size(size)
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
                            rotationZ = RollingArtworkMath.calculateRollRotation(tx, radiusPx)
                            alpha = a
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val activeModel = if (target.mediaId == mediaId) model else target.model
                    RollingArtworkDisk(
                        model = activeModel,
                        placeholderResId = target.placeholderResId,
                        size = size,
                        spinRotationProvider = spinRotationProvider,
                        contentDescription = contentDescription,
                    )
                }
            }
        }
    }
}

@Composable
fun RollingArtwork(
    mediaId: String,
    coverUrl: String,
    placeholderResId: Int,
    size: Dp = DefaultArtworkSlotSize,
    slotPaddingExtra: Dp = DefaultSlotPaddingExtra,
    isForward: Boolean = true,
    isRollingAllowed: Boolean = true,
    spinRotationProvider: () -> Float = { 0f },
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
) = RollingArtwork(
    mediaId = mediaId,
    model = coverUrl,
    placeholderResId = placeholderResId,
    size = size,
    slotPaddingExtra = slotPaddingExtra,
    isForward = isForward,
    isRollingAllowed = isRollingAllowed,
    spinRotationProvider = spinRotationProvider,
    contentDescription = contentDescription,
    modifier = modifier,
)

@Composable
private fun RollingArtworkDisk(
    model: Any?,
    placeholderResId: Int,
    size: Dp,
    spinRotationProvider: () -> Float,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val targetModel = (model as? String)?.takeIf { it.isNotBlank() } ?: (model?.takeIf { it !is String })

    Box(
        modifier = modifier
            .size(size)
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = spinRotationProvider() }
            .clip(CircleShape)
    ) {
        if (targetModel == null) {
            Image(
                painter = painterResource(id = placeholderResId),
                contentDescription = contentDescription ?: stringResource(R.string.mini_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val request = remember(targetModel) {
                ImageRequest.Builder(context)
                    .data(targetModel)
                    .size(96)
                    .crossfade(ARTWORK_CROSSFADE_DURATION_MS)
                    .allowHardware(true)
                    .memoryCacheKey("artwork:$targetModel")
                    .diskCacheKey("artwork:$targetModel")
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription ?: stringResource(R.string.mini_album_art),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = placeholderResId),
                error = painterResource(id = placeholderResId),
            )
        }
    }
}
