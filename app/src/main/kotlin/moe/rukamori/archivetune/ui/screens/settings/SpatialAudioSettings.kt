/*
 * Ozone Player (2026) | Spatial Audio Settings Screen
 * GNU General Public License v3.0
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SpatialAudioBassDbKey
import moe.rukamori.archivetune.constants.SpatialAudioCrossfeedKey
import moe.rukamori.archivetune.constants.SpatialAudioDelayMsKey
import moe.rukamori.archivetune.constants.SpatialAudioEnabledKey
import moe.rukamori.archivetune.constants.SpatialAudioMidDbKey
import moe.rukamori.archivetune.constants.SpatialAudioTrebleDbKey
import moe.rukamori.archivetune.playback.processor.SpatialProcessor
import moe.rukamori.archivetune.ui.component.GlassScaffold
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Locale

@Composable
fun SpatialAudioSettings(navController: NavController) {
    val processor = SpatialProcessor.instance

    var enabled by rememberPreference(SpatialAudioEnabledKey, defaultValue = processor.enabled)
    var crossfeed by rememberPreference(SpatialAudioCrossfeedKey, defaultValue = processor.crossfeed)
    var delayMs by rememberPreference(SpatialAudioDelayMsKey, defaultValue = processor.delayMs)
    var bassDb by rememberPreference(SpatialAudioBassDbKey, defaultValue = processor.bassDb)
    var midDb by rememberPreference(SpatialAudioMidDbKey, defaultValue = processor.midDb)
    var trebleDb by rememberPreference(SpatialAudioTrebleDbKey, defaultValue = processor.trebleDb)

    // Ensure live state matches current preference
    processor.enabled = enabled
    processor.crossfeed = crossfeed
    processor.delayMs = delayMs
    processor.bassDb = bassDb
    processor.midDb = midDb
    processor.trebleDb = trebleDb

    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spatial Audio") },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
        ) {
            // Master Toggle
            PreferenceGroup(title = "Master Control") {
                item {
                    SwitchPreference(
                        title = { Text("Enable Spatial Audio") },
                        description = {
                            Text(
                                if (enabled) "Spatial soundstage processing is active"
                                else "Audio passes through unprocessed"
                            )
                        },
                        icon = { Icon(painterResource(R.drawable.waves), null) },
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            processor.enabled = it
                        },
                    )
                }
            }

            // Hint Callout Card (Task 4)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.discover_tune),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "If your phone has Dolby/system spatial audio on, turn it off for best results.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Soundstage parameters
            PreferenceGroup(title = "Soundstage Parameters") {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Crossfeed",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f", crossfeed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "Amount of inter-aural acoustic blend (0.0 = none, 0.6 = wide)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = crossfeed,
                            onValueChange = {
                                crossfeed = it
                                processor.crossfeed = it
                            },
                            valueRange = 0f..0.6f,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Interaural Delay",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f ms", delayMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "Time delay for crossfeed signal simulation (0.1..0.4 ms)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = delayMs,
                            onValueChange = {
                                delayMs = it
                                processor.delayMs = it
                            },
                            valueRange = 0.1f..0.4f,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Acoustic Tone Balance (Biquad EQ)
            PreferenceGroup(title = "Acoustic Tone Balance") {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Bass Shelf (120 Hz)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", bassDb),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Slider(
                            value = bassDb,
                            onValueChange = {
                                bassDb = it
                                processor.bassDb = it
                            },
                            valueRange = -6f..6f,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Mid Peaking (1.5 kHz)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", midDb),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Slider(
                            value = midDb,
                            onValueChange = {
                                midDb = it
                                processor.midDb = it
                            },
                            valueRange = -6f..6f,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Treble Shelf (6.0 kHz)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f dB", trebleDb),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Slider(
                            value = trebleDb,
                            onValueChange = {
                                trebleDb = it
                                processor.trebleDb = it
                            },
                            valueRange = -6f..6f,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                crossfeed = 0.3f
                                delayMs = 0.25f
                                bassDb = 0f
                                midDb = 0f
                                trebleDb = 0f
                                processor.crossfeed = 0.3f
                                processor.delayMs = 0.25f
                                processor.bassDb = 0f
                                processor.midDb = 0f
                                processor.trebleDb = 0f
                            },
                            enabled = enabled,
                        ) {
                            Text("Reset to Defaults")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
