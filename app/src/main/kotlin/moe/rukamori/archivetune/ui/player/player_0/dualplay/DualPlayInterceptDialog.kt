/*
 * Ozone Player (2026) | Dual Play Intercept Dialog
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import moe.rukamori.archivetune.playback.dualplay.PendingDualPlayQueue

@Composable
fun DualPlayInterceptDialog(
    pending: PendingDualPlayQueue,
    hasSongB: Boolean,
    onAddToDualPlay: () -> Unit,
    onAddToSongBQueue: () -> Unit = {},
    onPlayInstead: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(painterResource(R.drawable.equalizer), contentDescription = null)
        },
        title = {
            Text(text = "Dual Play Active", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!pending.artworkUri.isNullOrBlank()) {
                        AsyncImage(
                            model = pending.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pending.songTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!pending.songArtist.isNullOrBlank()) {
                            Text(
                                text = pending.songArtist,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Text(
                    text = if (hasSongB) {
                        "Choose how to play this track in Dual Play:"
                    } else {
                        "Do you want to add this song to Dual Play or play it instead?"
                    },
                    fontSize = 14.sp,
                )

                if (hasSongB) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onAddToDualPlay,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Replace Song B")
                        }

                        FilledTonalButton(
                            onClick = onAddToSongBQueue,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add to Song B queue")
                        }

                        OutlinedButton(
                            onClick = onPlayInstead,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Replace Song A")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!hasSongB) {
                Button(onClick = onAddToDualPlay) {
                    Text("Add to Dual Play")
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        },
        dismissButton = {
            if (!hasSongB) {
                OutlinedButton(onClick = onPlayInstead) {
                    Text("Play instead")
                }
            }
        },
    )
}
