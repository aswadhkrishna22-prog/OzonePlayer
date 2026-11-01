package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.sin

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
    isNextEnabled: Boolean = true,
    isPrevEnabled: Boolean = true,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = duration.coerceAtLeast(0L)
    val safePosition = position.coerceIn(0L, safeDuration.coerceAtLeast(1L))
    val displayPosition = if (dragFraction != null) {
        ((dragFraction ?: 0f) * safeDuration).toLong().coerceIn(0L, safeDuration)
    } else {
        safePosition
    }
    val remaining = if (safeDuration > 0L) (safeDuration - displayPosition).coerceAtLeast(0L) else 0L

    Column(modifier = modifier) {
        SeekBar(
            position = safePosition,
            duration = safeDuration,
            onSeek = onSeek,
            onSeekStarted = onSeekStarted,
            dragFraction = dragFraction,
            onDragFractionChange = { dragFraction = it },
            isPlaying = isPlaying,
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
            PressIcon(Icons.Rounded.SkipPrevious, 44.dp, onPrev, enabled = isPrevEnabled)
            PressIcon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                72.dp,
                onPlayPause
            )
            PressIcon(Icons.Rounded.SkipNext, 44.dp, onNext, enabled = isNextEnabled)
            if (repeatContent != null) {
                repeatContent()
            }
        }
    }
}

@Composable
fun PressIcon(
    icon: ImageVector,
    size: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "PressIconScale"
    )
    val haptic = LocalHapticFeedback.current
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f),
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = src,
                        indication = null,
                        onClick = {
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } catch (_: Throwable) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onClick()
                        }
                    )
                } else Modifier
            )
    )
}

@Composable
fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStarted: (() -> Unit)? = null,
    dragFraction: Float? = null,
    onDragFractionChange: ((Float?) -> Unit)? = null,
    isPlaying: Boolean = false,
) {
    var internalDrag by remember { mutableStateOf<Float?>(null) }
    val drag = dragFraction ?: internalDrag
    val safeDuration = duration.coerceAtLeast(0L)
    val safePosition = position.coerceIn(0L, safeDuration.coerceAtLeast(1L))
    val frac = drag ?: if (safeDuration > 0L) (safePosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) else 0f
    val active = drag != null

    val targetAmplitude = if (isPlaying && !active) 4.5.dp else if (isPlaying) 2.dp else 0.dp
    val animatedAmplitude by animateDpAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "WaveAmplitude"
    )

    val strokeWidth by animateDpAsState(
        targetValue = if (active) 6.dp else 4.5.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "StrokeWidth"
    )

    val thumbRadius by animateDpAsState(
        targetValue = if (active) 7.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "ThumbRadius"
    )

    // Only run phase animation when actively playing
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastTime = 0L
        val speed = 3.6f
        while (true) {
            withFrameNanos { time ->
                if (lastTime != 0L) {
                    val dt = (time - lastTime) / 1_000_000_000f
                    phase = (phase + dt * speed) % (2f * Math.PI.toFloat())
                }
                lastTime = time
            }
        }
    }

    var widthPx by remember { mutableFloatStateOf(1f) }
    val wavePath = remember { Path() }

    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(safeDuration) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    onSeekStarted?.invoke()
                    var currentFrac = (down.position.x / width).coerceIn(0f, 1f)
                    if (onDragFractionChange != null) {
                        onDragFractionChange(currentFrac)
                    } else {
                        internalDrag = currentFrac
                    }
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            onSeek((currentFrac * safeDuration).toLong())
                            if (onDragFractionChange != null) {
                                onDragFractionChange(null)
                            } else {
                                internalDrag = null
                            }
                            break
                        } else {
                            change.consume()
                            currentFrac = (change.position.x / width).coerceIn(0f, 1f)
                            if (onDragFractionChange != null) {
                                onDragFractionChange(currentFrac)
                            } else {
                                internalDrag = currentFrac
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val w = size.width
            val centerY = size.height / 2f
            val currentStrokeWidth = strokeWidth.toPx()
            val ampPx = animatedAmplitude.toPx()
            val playedW = (frac.coerceIn(0f, 1f) * w).coerceIn(0f, w)

            // 1. Unplayed background track (straight 25% alpha line)
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(0f, centerY),
                end = Offset(w, centerY),
                strokeWidth = currentStrokeWidth,
                cap = StrokeCap.Round
            )

            // 2. Played progress track
            if (playedW > 0.5f) {
                if (ampPx <= 0.05f) {
                    // Straight solid line when paused or flattened
                    drawLine(
                        color = Color.White,
                        start = Offset(0f, centerY),
                        end = Offset(playedW, centerY),
                        strokeWidth = currentStrokeWidth,
                        cap = StrokeCap.Round
                    )
                } else {
                    // Wavy / Squiggly line when playing with zero allocations & smooth curves
                    val waveLengthPx = 28.dp.toPx()
                    val k = (2.0 * Math.PI / waveLengthPx).toFloat()
                    val taperDistance = 22.dp.toPx()

                    wavePath.reset()
                    wavePath.moveTo(0f, centerY)

                    val stepPx = 6.dp.toPx().coerceAtLeast(10f)
                    var prevX = 0f
                    var prevY = centerY
                    var currentX = 0f

                    while (currentX < playedW) {
                        currentX += stepPx
                        if (currentX > playedW) currentX = playedW

                        val startTaper = (currentX / taperDistance).coerceIn(0f, 1f)
                        val endTaper = ((playedW - currentX) / taperDistance).coerceIn(0f, 1f)
                        val envelope = minOf(startTaper, endTaper)

                        val angle = currentX * k - phase
                        val y = centerY + (ampPx * envelope * sin(angle))

                        val midX = (prevX + currentX) / 2f
                        val midY = (prevY + y) / 2f
                        wavePath.quadraticBezierTo(prevX, prevY, midX, midY)

                        prevX = currentX
                        prevY = y
                    }
                    wavePath.lineTo(playedW, prevY)

                    drawPath(
                        path = wavePath,
                        color = Color.White,
                        style = Stroke(
                            width = currentStrokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 3. Thumb glow circle when scrubbing
                val thumbR = thumbRadius.toPx()
                if (thumbR > 0.5f) {
                    drawCircle(
                        color = Color.White,
                        radius = thumbR,
                        center = Offset(playedW, centerY)
                    )
                }
            }
        }
    }
}

fun fmt(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
