package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: (() -> Unit)? = null,
    shuffleContent: @Composable (() -> Unit)? = null,
    repeatContent: @Composable (() -> Unit)? = null,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = duration.coerceAtLeast(0L)
    val safePosition = position.coerceIn(0L, safeDuration.coerceAtLeast(1L))
    val displayPosition = if (dragFraction != null) {
        ((dragFraction ?: 0f) * safeDuration).toLong().coerceIn(0L, safeDuration)
    } else {
        safePosition
    }
    val remaining = (safeDuration - displayPosition).coerceAtLeast(0L)

    Column(modifier.padding(horizontal = 28.dp)) {
        SeekBar(
            position = safePosition,
            duration = safeDuration,
            onSeek = onSeek,
            onSeekStarted = onSeekStarted,
            dragFraction = dragFraction,
            onDragFractionChange = { dragFraction = it }
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val t = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(fmt(displayPosition), style = t)
            Text("-" + fmt(remaining), style = t)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shuffleContent != null) {
                shuffleContent()
            }
            PressIcon(Icons.Rounded.SkipPrevious, 44.dp, onPrev)
            PressIcon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                72.dp,
                onPlayPause
            )
            PressIcon(Icons.Rounded.SkipNext, 44.dp, onNext)
            if (repeatContent != null) {
                repeatContent()
            }
        }
    }
}

@Composable
fun PressIcon(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "s"
    )
    val haptic = LocalHapticFeedback.current
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(src, indication = null) {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Throwable) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            }
    )
}

@Composable
fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekStarted: (() -> Unit)? = null,
    dragFraction: Float? = null,
    onDragFractionChange: ((Float?) -> Unit)? = null,
) {
    var internalDrag by remember { mutableStateOf<Float?>(null) }
    val drag = dragFraction ?: internalDrag
    val safeDuration = duration.coerceAtLeast(0L)
    val safePosition = position.coerceIn(0L, safeDuration.coerceAtLeast(1L))
    val frac = drag ?: if (safeDuration > 0L) (safePosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) else 0f
    val active = drag != null
    val h by animateDpAsState(if (active) 10.dp else 5.dp, spring(), label = "h")
    var w by remember { mutableFloatStateOf(1f) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(safeDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onSeekStarted?.invoke()
                        val start = (offset.x / w).coerceIn(0f, 1f)
                        if (onDragFractionChange != null) {
                            onDragFractionChange(start)
                        } else {
                            internalDrag = start
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val current = (change.position.x / w).coerceIn(0f, 1f)
                        if (onDragFractionChange != null) {
                            onDragFractionChange(current)
                        } else {
                            internalDrag = current
                        }
                    },
                    onDragEnd = {
                        val finalFrac = drag ?: 0f
                        onSeek((finalFrac * safeDuration).toLong())
                        if (onDragFractionChange != null) {
                            onDragFractionChange(null)
                        } else {
                            internalDrag = null
                        }
                    },
                    onDragCancel = {
                        if (onDragFractionChange != null) {
                            onDragFractionChange(null)
                        } else {
                            internalDrag = null
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(h)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
        )
        Box(
            Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .height(h)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
