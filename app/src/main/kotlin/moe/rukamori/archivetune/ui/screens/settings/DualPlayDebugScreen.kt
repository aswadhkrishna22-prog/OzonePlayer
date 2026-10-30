/*
 * Ozone Player (2026) | Dual Play Debug Screen
 * GNU General Public License v3.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.flow.flowOf
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.dualplay.DualPlayState
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.GlassScaffold
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain

@Composable
fun DualPlayDebugScreen(navController: NavController) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val engine = playerConnection?.dualPlayEngine

    val dualState by (engine?.state ?: flowOf(DualPlayState())).collectAsState(DualPlayState())
    val currentSongA by (playerConnection?.currentSong ?: flowOf(null)).collectAsState(null)
    val isPlayingA by (playerConnection?.isPlaying ?: flowOf(false)).collectAsState(false)

    val librarySongs by (playerConnection?.database?.songsByNameAsc() ?: flowOf(emptyList())).collectAsState(emptyList())

    var showSongPicker by remember { mutableStateOf(false) }

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual Play (Debug)") },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                colors = GlassDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Warning banner if unsupported audio format (Amendment 3)
            if (dualState.channelSplitUnsupportedFormat != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Channel split not applied",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = dualState.channelSplitUnsupportedFormat ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // Master Dual Play Toggle
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Dual Play",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Left ear = Song A | Right ear = Song B (Stereo headphones required)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = dualState.enabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val success = engine?.enable() == true
                                if (!success) {
                                    Toast.makeText(
                                        context,
                                        "Connect stereo headphones first (A2DP, BLE, Wired, USB)",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            } else {
                                engine?.disable()
                            }
                        },
                    )
                }
            }

            // Song A (Left Ear) Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "🎧 SONG A — LEFT EAR ONLY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = currentSongA?.song?.title ?: playerConnection?.mediaMetadata?.value?.title ?: "No Song Playing in Player A",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (isPlayingA) engine?.pauseA() else engine?.playA()
                            },
                        ) {
                            Icon(
                                painter = painterResource(if (isPlayingA) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isPlayingA) "Pause A" else "Play A")
                        }
                    }

                    Column {
                        Text(
                            text = "Volume A: ${(dualState.volumeA * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = dualState.volumeA,
                            onValueChange = { engine?.setVolumeA(it) },
                            valueRange = 0f..1f,
                        )
                    }
                }
            }

            // Song B (Right Ear) Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "🎧 SONG B — RIGHT EAR ONLY",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = dualState.playerBMediaItem?.mediaMetadata?.title?.toString()
                            ?: "(No song loaded for Player B — pick below)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (dualState.playerBError != null) {
                        Text(
                            text = "Error: ${dualState.playerBError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                if (dualState.playerBIsPlaying) engine?.pauseB() else engine?.playB()
                            },
                            enabled = dualState.enabled && dualState.playerBMediaItem != null,
                        ) {
                            Icon(
                                painter = painterResource(if (dualState.playerBIsPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (dualState.playerBIsPlaying) "Pause B" else "Play B")
                        }

                        Button(
                            onClick = { showSongPicker = true },
                            enabled = dualState.enabled,
                        ) {
                            Text("Pick Song B")
                        }
                    }

                    Column {
                        Text(
                            text = "Volume B: ${(dualState.volumeB * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = dualState.volumeB,
                            onValueChange = { engine?.setVolumeB(it) },
                            valueRange = 0f..1f,
                            enabled = dualState.enabled,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Song picker dialog for Player B
    if (showSongPicker) {
        AlertDialog(
            onDismissRequest = { showSongPicker = false },
            title = { Text("Select Song for Player B (Right Ear)") },
            text = {
                if (librarySongs.isEmpty()) {
                    Text("No local songs found in library.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                    ) {
                        items(librarySongs, key = { it.id }) { item: Song ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        engine?.setMediaItemB(item.toMediaItem())
                                        engine?.playB()
                                        showSongPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                            ) {
                                Text(
                                    text = item.song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.artists.joinToString { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongPicker = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
