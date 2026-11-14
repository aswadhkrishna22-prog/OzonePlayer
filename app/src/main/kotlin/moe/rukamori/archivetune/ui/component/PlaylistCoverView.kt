/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistCoverStyle

sealed interface CoverDebugState {
    data object Loading : CoverDebugState
    data object Cached : CoverDebugState
    data object Generated : CoverDebugState
    data object ClassicSetting : CoverDebugState
    data object Custom : CoverDebugState
    data class Failed(val reason: String) : CoverDebugState

    fun label(): String = when (this) {
        Loading -> "loading"
        Cached -> "new: cached"
        Generated -> "new: generated"
        ClassicSetting -> "classic: setting"
        Custom -> "custom"
        is Failed -> "failed: $reason"
    }
}

@Composable
private fun VinylDiscItem(
    model: Any?,
    size: Dp,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    elevation: Dp = 8.dp,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
                shadowElevation = elevation.toPx()
                shape = CircleShape
                clip = true
            }
            .background(Color(0xFF141414), CircleShape)
            .border(BorderStroke(1.5.dp, Color(0xFF282828)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            onState = onState,
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = this.size.minDimension / 2f
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = maxR * 0.96f,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            for (i in 1..4) {
                val grooveRadius = maxR * (0.38f + i * 0.12f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = grooveRadius,
                    style = Stroke(width = 0.75.dp.toPx()),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.18f),
                    radius = grooveRadius + 0.5.dp.toPx(),
                    style = Stroke(width = 0.75.dp.toPx()),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f),
                        ),
                    ),
                    shape = CircleShape,
                ),
        )

        Box(
            modifier = Modifier
                .size(size * 0.24f)
                .clip(CircleShape)
                .background(Color(0xFF181818))
                .border(BorderStroke(1.dp, Color(0xFF333333)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.08f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(BorderStroke(0.75.dp, Color(0xFF555555)), CircleShape),
            )
        }
    }
}

@Composable
fun ClassicCollageCover(
    thumbnails: List<String>,
    modifier: Modifier = Modifier,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    if (thumbnails.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val halfWidth = maxWidth / 2f
        val halfHeight = maxHeight / 2f
        listOf(
            Alignment.TopStart,
            Alignment.TopEnd,
            Alignment.BottomStart,
            Alignment.BottomEnd,
        ).fastForEachIndexed { index, alignment ->
            AsyncImage(
                model = thumbnails.getOrNull(index),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(alignment)
                    .size(halfWidth, halfHeight),
                onState = onState,
            )
        }
    }
}

@Composable
fun PlaylistModernDiscsCover(
    thumbnails: List<String>,
    modifier: Modifier = Modifier,
    onBgState: (AsyncImagePainter.State) -> Unit = {},
    onMidState: (AsyncImagePainter.State) -> Unit = {},
    onLeftState: (AsyncImagePainter.State) -> Unit = {},
    onRightState: (AsyncImagePainter.State) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerSize = maxWidth
        val discBaseSize = containerSize * 0.48f

        // 1. Background Image (Song 0)
        AsyncImage(
            model = thumbnails.firstOrNull(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onState = onBgState,
        )

        // Dark depth gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )

        // 2. Left Disc (Song 2)
        if (thumbnails.size >= 3) {
            val leftModel = thumbnails.getOrNull(2) ?: thumbnails.getOrNull(1)
            VinylDiscItem(
                model = leftModel,
                size = discBaseSize * 0.88f,
                rotation = -12f,
                elevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = -containerSize * 0.20f, y = -containerSize * 0.04f)
                    .zIndex(1f),
                onState = onLeftState,
            )
        }

        // 3. Right Disc (Song 3)
        if (thumbnails.size >= 4) {
            val rightModel = thumbnails.getOrNull(3) ?: thumbnails.firstOrNull()
            VinylDiscItem(
                model = rightModel,
                size = discBaseSize * 0.88f,
                rotation = 12f,
                elevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = containerSize * 0.20f, y = -containerSize * 0.04f)
                    .zIndex(2f),
                onState = onRightState,
            )
        }

        // 4. Middle Disc (Song 1)
        if (thumbnails.size >= 2) {
            val midModel = thumbnails.getOrNull(1) ?: thumbnails.firstOrNull()
            VinylDiscItem(
                model = midModel,
                size = discBaseSize,
                rotation = 0f,
                elevation = 14.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 0.dp, y = containerSize * 0.06f)
                    .zIndex(3f),
                onState = onMidState,
            )
        }
    }
}

/**
 * Universal Playlist Cover supporting Modern Discs, Classic 2x2 Collage,
 * Custom User covers, debug state telemetry, and release fallback.
 */
@Composable
fun PlaylistCoverView(
    thumbnails: List<String>,
    modifier: Modifier = Modifier,
    isCustomCover: Boolean = false,
    coverStyle: PlaylistCoverStyle = PlaylistCoverStyle.DISCS,
    shape: Shape = RoundedCornerShape(16.dp),
    placeHolder: (@Composable () -> Unit)? = null,
) {
    var bgState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    var midState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    var leftState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    var rightState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val currentState: CoverDebugState = remember(
        isCustomCover,
        coverStyle,
        thumbnails,
        bgState,
        midState,
        leftState,
        rightState,
    ) {
        if (isCustomCover) {
            CoverDebugState.Custom
        } else if (coverStyle == PlaylistCoverStyle.COLLAGE) {
            CoverDebugState.ClassicSetting
        } else if (thumbnails.isEmpty()) {
            CoverDebugState.Custom
        } else if (thumbnails.size == 1) {
            when (val s = bgState) {
                is AsyncImagePainter.State.Error ->
                    CoverDebugState.Failed(s.result.throwable.message?.take(18) ?: "image error")
                is AsyncImagePainter.State.Loading, is AsyncImagePainter.State.Empty ->
                    CoverDebugState.Loading
                is AsyncImagePainter.State.Success -> {
                    val isCached = s.result.dataSource.name in listOf("MEMORY", "DISK")
                    if (isCached) CoverDebugState.Cached else CoverDebugState.Generated
                }
            }
        } else {
            val requiredStates = when (thumbnails.size) {
                2 -> listOf(bgState, midState)
                3 -> listOf(bgState, midState, leftState)
                else -> listOf(bgState, midState, leftState, rightState)
            }

            val errorState = requiredStates.filterIsInstance<AsyncImagePainter.State.Error>().firstOrNull()
            if (errorState != null) {
                CoverDebugState.Failed(errorState.result.throwable.message?.take(18) ?: "load failed")
            } else if (requiredStates.any { it is AsyncImagePainter.State.Loading || it is AsyncImagePainter.State.Empty }) {
                CoverDebugState.Loading
            } else {
                val allCached = requiredStates.filterIsInstance<AsyncImagePainter.State.Success>()
                    .all { it.result.dataSource.name in listOf("MEMORY", "DISK") }
                if (allCached) CoverDebugState.Cached else CoverDebugState.Generated
            }
        }
    }

    if (!BuildConfig.DEBUG && currentState is CoverDebugState.Failed) {
        LaunchedEffect(currentState) {
            Log.w(
                "PlaylistCover",
                "Modern discs generator failed: ${(currentState as CoverDebugState.Failed).reason}, falling back to classic collage",
            )
        }
    }

    Box(
        modifier = modifier
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            thumbnails.isEmpty() -> {
                if (placeHolder != null) {
                    placeHolder()
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            isCustomCover || thumbnails.size == 1 -> {
                AsyncImage(
                    model = thumbnails.firstOrNull(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { bgState = it },
                )
            }

            coverStyle == PlaylistCoverStyle.COLLAGE -> {
                ClassicCollageCover(
                    thumbnails = thumbnails,
                    modifier = Modifier.fillMaxSize(),
                    onState = { bgState = it },
                )
            }

            !BuildConfig.DEBUG && currentState is CoverDebugState.Failed -> {
                ClassicCollageCover(
                    thumbnails = thumbnails,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                PlaylistModernDiscsCover(
                    thumbnails = thumbnails,
                    modifier = Modifier.fillMaxSize(),
                    onBgState = { bgState = it },
                    onMidState = { midState = it },
                    onLeftState = { leftState = it },
                    onRightState = { rightState = it },
                )
            }
        }

        if (BuildConfig.DEBUG) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .zIndex(10f),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.8f),
            ) {
                Text(
                    text = currentState.label(),
                    color = when (currentState) {
                        is CoverDebugState.Failed -> Color(0xFFFF5252)
                        CoverDebugState.Cached, CoverDebugState.Generated -> Color(0xFF69F0AE)
                        else -> Color.White.copy(alpha = 0.9f)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
