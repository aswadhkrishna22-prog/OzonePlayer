/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.musicrecognition

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.musicrecognition.CoverArt

private enum class OverlayPhase {
    RADAR,
    SUCCESS,
    ERROR,
}

@Composable
fun OzoneSearchOverlayContent(
    state: OverlayState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPlayTrack: (RecognizedTrack) -> Unit,
    onOpenInOzone: (RecognizedTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    LaunchedEffect(state) {
        if (state is OverlayState.Success) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val phase =
            when (state) {
                is OverlayState.Success -> OverlayPhase.SUCCESS
                is OverlayState.Error -> OverlayPhase.ERROR
                else -> OverlayPhase.RADAR
            }

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (
                    fadeIn(animationSpec = tween(320)) +
                        scaleIn(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                        )
                ).togetherWith(
                    fadeOut(animationSpec = tween(180)) +
                        scaleOut(animationSpec = tween(180)),
                )
            },
            label = "OverlayContentMorph",
        ) { targetPhase ->
            when (targetPhase) {
                OverlayPhase.RADAR -> {
                    GoogleSoundSearchRadarCard(
                        isProcessing = state is OverlayState.Processing,
                        onCancel = onDismiss,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }

                OverlayPhase.SUCCESS -> {
                    val track = (state as? OverlayState.Success)?.track
                    if (track != null) {
                        GlassmorphicResultCard(
                            track = track,
                            onClose = onDismiss,
                            onPlay = { onPlayTrack(track) },
                            onOpen = { onOpenInOzone(track) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                }

                OverlayPhase.ERROR -> {
                    val err = state as? OverlayState.Error
                    OverlayErrorCard(
                        message = err?.message ?: stringResource(R.string.ozone_search_no_match),
                        canRetry = err?.canRetry ?: true,
                        onRetry = onRetry,
                        onClose = onDismiss,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleSoundSearchRadarCard(
    isProcessing: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")

    val pulseScale =
        infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.08f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "CenterPulse",
        )

    val radarProgress =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "RadarSweep",
        )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceElevated = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface

    val backgroundBrush =
        remember(surfaceElevated, surfaceColor) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        surfaceElevated.copy(alpha = 0.88f),
                        surfaceColor.copy(alpha = 0.82f),
                    ),
            )
        }
    val borderBrush =
        remember(primaryColor) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.10f),
                        primaryColor.copy(alpha = 0.25f),
                    ),
            )
        }
    val discShape = remember { MaterialShapes.Cookie9Sided.toShape() }

    Box(
        modifier =
            modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* keep overlay open */ },
                ).shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                ).clip(RoundedCornerShape(32.dp))
                .background(backgroundBrush)
                .border(
                    width = 1.2.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(32.dp),
                ).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.16f),
                    contentColor = primaryColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.ozone_search),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val progress = radarProgress.value
                    val centerOffset = center
                    val minRadius = 40.dp.toPx()
                    val maxRadius = 88.dp.toPx()
                    val radiusSpan = maxRadius - minRadius
                    val baseStroke = 2.5.dp.toPx()

                    for (i in 0..2) {
                        val ringP = (progress + i * 0.333f) % 1f
                        val ringColor =
                            when (i) {
                                0 -> primaryColor
                                1 -> tertiaryColor
                                else -> secondaryColor
                            }
                        val currentRadius = minRadius + radiusSpan * ringP
                        val ringAlpha = ((1f - ringP) * 0.70f).coerceIn(0f, 1f)
                        val stroke = baseStroke * (1f - ringP * 0.4f)
                        drawCircle(
                            color = ringColor.copy(alpha = ringAlpha),
                            radius = currentRadius,
                            center = centerOffset,
                            style = Stroke(width = stroke),
                        )
                    }
                }

                Surface(
                    modifier =
                        Modifier
                            .size(88.dp)
                            .graphicsLayer {
                                val s = pulseScale.value
                                scaleX = s
                                scaleY = s
                            },
                    shape = discShape,
                    color =
                        if (isProcessing) {
                            tertiaryColor
                        } else {
                            primaryColor
                        },
                    shadowElevation = 12.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Crossfade(
                            targetState = isProcessing,
                            label = "CenterIconFade",
                        ) { processing ->
                            Icon(
                                painter =
                                    if (processing) {
                                        painterResource(R.drawable.search)
                                    } else {
                                        painterResource(R.drawable.mic)
                                    },
                                contentDescription = null,
                                tint =
                                    if (processing) {
                                        MaterialTheme.colorScheme.onTertiary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Crossfade(
                    targetState = isProcessing,
                    label = "StatusTitleFade",
                ) { processing ->
                    Text(
                        text =
                            if (processing) {
                                stringResource(R.string.ozone_search_identifying)
                            } else {
                                stringResource(R.string.ozone_search_listening)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.ozone_search_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GlassmorphicResultCard(
    track: RecognizedTrack,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier =
            modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent closing on card click */ },
                ).shadow(
                    elevation = 32.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                ).clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp).copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                            ),
                    ),
                ).border(
                    width = 1.2.dp,
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.42f),
                                    Color.White.copy(alpha = 0.12f),
                                    primaryColor.copy(alpha = 0.28f),
                                ),
                        ),
                    shape = RoundedCornerShape(32.dp),
                ).padding(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.16f),
                    contentColor = primaryColor,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_music),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.ozone_search),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(
                    artworkUrl = track.coverArtHqUrl ?: track.coverArtUrl,
                    displaySize = 74.dp,
                    shape = RoundedCornerShape(18.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    track.album?.takeIf(String::isNotBlank)?.let { albumName ->
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onPlay,
                    modifier =
                        Modifier
                            .weight(0.42f)
                            .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ozone_search_play_preview),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Button(
                    onClick = onOpen,
                    modifier =
                        Modifier
                            .weight(0.58f)
                            .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ozone_search_open_in_app),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayErrorCard(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* keep open */ },
                ).shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                ).clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp).copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            ),
                    ),
                ).border(
                    width = 1.2.dp,
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.10f),
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                                ),
                        ),
                    shape = RoundedCornerShape(32.dp),
                ).padding(22.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search_off),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.ozone_search),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.ozone_search_no_match_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.replay),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ozone_search_try_again))
                    }
                }

                OutlinedButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}
