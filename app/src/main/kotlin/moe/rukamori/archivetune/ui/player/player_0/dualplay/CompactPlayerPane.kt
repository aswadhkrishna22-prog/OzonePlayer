/*
 * Ozone Player (2026) | Compact Player Pane for Dual Play
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.utils.bounceClick
import moe.rukamori.archivetune.utils.makeTimeString

/** Minimum pane height in DP required to show the volume slider inline instead of popup. */
val MIN_PANE_HEIGHT_FOR_INLINE_VOLUME = 240.dp

@Composable
fun CompactPlayerPane(
    badgeLabel: String,
    title: String,
    artist: String,
    artworkUrl: Any?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    earButtons: @Composable () -> Unit,
    hasPrevNext: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    vibrantColor: Color = MaterialTheme.colorScheme.primary,
    availableHeightDp: Float = 300f,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalYumaHaptics.current
    val fontFamily = LocalArchiveTuneFontFamily.current
    var showVolumePopup by remember { mutableStateOf(false) }

    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val currentFraction = if (isSeeking) seekProgress else (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    val inlineVolume = availableHeightDp >= 240f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Top Header: Artwork + Title/Artist + Ear Buttons ───────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = badgeLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = vibrantColor,
                    letterSpacing = 1.sp,
                )
                MarqueeText(
                    text = title.ifBlank { "Unknown" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontFamily = fontFamily,
                )
                MarqueeText(
                    text = artist.ifBlank { "Unknown Artist" },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = fontFamily,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Ear Buttons (R/M for Pane A, L/M for Pane B)
            earButtons()
        }

        // ── Middle: Seek bar & times ──────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = currentFraction,
                onValueChange = {
                    isSeeking = true
                    seekProgress = it
                },
                onValueChangeFinished = {
                    val targetMs = (seekProgress * safeDuration).toLong()
                    onSeek(targetMs)
                    isSeeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = vibrantColor,
                    activeTrackColor = vibrantColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val displayPos = if (isSeeking) (seekProgress * safeDuration).toLong() else positionMs
                Text(
                    text = makeTimeString(displayPos),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = fontFamily,
                )
                Text(
                    text = makeTimeString(durationMs),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = fontFamily,
                )
            }
        }

        // ── Bottom: Transport Controls & Volume ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Playback buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasPrevNext) {
                    IconButton(
                        onClick = {
                            haptics.click()
                            onPrev()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Play / Pause Circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(vibrantColor)
                        .bounceClick(pressedScale = 0.88f) {
                            haptics.click()
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp),
                    )
                }

                if (hasPrevNext) {
                    IconButton(
                        onClick = {
                            haptics.click()
                            onNext()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // Volume Control
            if (inlineVolume) {
                Row(
                    modifier = Modifier.width(130.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = "Volume",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                }
            } else {
                Box {
                    IconButton(
                        onClick = { showVolumePopup = !showVolumePopup },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.volume_up),
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (showVolumePopup) {
                        Popup(
                            onDismissRequest = { showVolumePopup = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .width(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E1E1E))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.volume_up),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Slider(
                                        value = volume,
                                        onValueChange = onVolumeChange,
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = vibrantColor,
                                            activeTrackColor = vibrantColor,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
