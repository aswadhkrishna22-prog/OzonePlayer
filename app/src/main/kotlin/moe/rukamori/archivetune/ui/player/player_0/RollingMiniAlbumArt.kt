package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R

@Composable
internal fun RollingMiniAlbumArt(
    coverUrl: String,
    placeholderResId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    trackKey: String = coverUrl,
    size: Dp = 40.dp,
    rollDistance: Dp = 48.dp,
    isVisible: Boolean = true,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, isVisible) {
        if (isPlaying && isVisible) {
            while (true) {
                val currentDegree = rotation.value % 360f
                rotation.snapTo(currentDegree)
                rotation.animateTo(
                    targetValue = currentDegree + 360f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing),
                )
            }
        }
    }

    var displayedKey by remember { mutableStateOf(trackKey) }
    var displayedCoverUrl by remember { mutableStateOf(coverUrl) }
    var displayedPlaceholder by remember { mutableIntStateOf(placeholderResId) }

    var outgoingCoverUrl by remember { mutableStateOf<String?>(null) }
    var outgoingPlaceholder by remember { mutableIntStateOf(placeholderResId) }

    val rollAnimatable = remember { Animatable(1f) }
    val animationsDisabled = LocalAnimationsDisabled.current

    LaunchedEffect(trackKey) {
        if (trackKey != displayedKey) {
            if (displayedKey.isNotEmpty() && !animationsDisabled) {
                outgoingCoverUrl = displayedCoverUrl
                outgoingPlaceholder = displayedPlaceholder
                displayedKey = trackKey
                displayedCoverUrl = coverUrl
                displayedPlaceholder = placeholderResId

                rollAnimatable.snapTo(0f)
                rollAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 380f,
                    ),
                )
            } else {
                displayedKey = trackKey
                displayedCoverUrl = coverUrl
                displayedPlaceholder = placeholderResId
                rollAnimatable.snapTo(1f)
            }
        } else {
            displayedCoverUrl = coverUrl
            displayedPlaceholder = placeholderResId
        }
    }

    val rollDistancePx = with(LocalDensity.current) { rollDistance.toPx() }
    val progress = rollAnimatable.value
    val isTransitioning = progress < 1f && !animationsDisabled

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (isTransitioning) {
            SingleDisc(
                coverUrl = outgoingCoverUrl,
                placeholderResId = outgoingPlaceholder,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -progress * rollDistancePx
                        rotationZ = rotation.value - (progress * 360f)
                        alpha = (1f - progress * 1.2f).coerceIn(0f, 1f)
                    }
                    .clip(CircleShape),
            )
        }

        SingleDisc(
            coverUrl = displayedCoverUrl,
            placeholderResId = displayedPlaceholder,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isTransitioning) {
                        translationX = (1f - progress) * rollDistancePx
                        rotationZ = rotation.value + ((1f - progress) * 360f)
                        alpha = (progress * 1.5f).coerceIn(0f, 1f)
                    } else {
                        rotationZ = rotation.value
                    }
                }
                .clip(CircleShape),
        )
    }
}

@Composable
private fun SingleDisc(
    coverUrl: String?,
    placeholderResId: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val targetUrl = coverUrl?.takeIf { it.isNotBlank() }

    if (targetUrl == null) {
        Image(
            painter = painterResource(id = placeholderResId),
            contentDescription = stringResource(R.string.mini_album_art),
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        val request = remember(targetUrl) {
            ImageRequest.Builder(context)
                .data(targetUrl)
                .size(96)
                .allowHardware(true)
                .memoryCacheKey("mini:$targetUrl")
                .diskCacheKey("mini:$targetUrl")
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.mini_album_art),
            modifier = modifier,
            contentScale = ContentScale.Crop,
            error = painterResource(id = placeholderResId),
        )
    }
}
