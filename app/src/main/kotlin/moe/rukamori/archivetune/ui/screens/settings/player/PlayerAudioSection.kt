/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import moe.rukamori.archivetune.constants.EnhanceStrength
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.DualPlayBetaChip
import moe.rukamori.archivetune.ui.component.DualPlayBetaDialog
import moe.rukamori.archivetune.ui.component.DualPlayBetaInfoButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.menu.AutoEqPickerDialog
import moe.rukamori.archivetune.ui.menu.SignalPathDialog

@Composable
internal fun PlayerAudioSection(
    state: PlayerSettingsUiState,
    actions: PlayerSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.player_and_audio),
        modifier = modifier,
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.skip_silence)) },
                icon = { Icon(painterResource(R.drawable.skip_next), null) },
                checked = state.skipSilence,
                onCheckedChange = actions.onSkipSilenceChange,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_normalization)) },
                icon = { Icon(painterResource(R.drawable.volume_up), null) },
                checked = state.audioNormalization,
                onCheckedChange = actions.onAudioNormalizationChange,
            )
        }
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enhance_audio_title)) },
                description = stringResource(R.string.enhance_audio_desc),
                icon = { Icon(painterResource(R.drawable.auto_awesome), null) },
                checked = state.enhanceEnabled,
                onCheckedChange = actions.onEnhanceEnabledChange,
            )
        }
        if (state.enhanceEnabled) {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.enhance_auto_mode_title)) },
                    description = stringResource(R.string.enhance_auto_mode_desc),
                    icon = { Icon(painterResource(R.drawable.speed), null) },
                    checked = state.enhanceAutoMode,
                    onCheckedChange = actions.onEnhanceAutoModeChange,
                )
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
                            text = stringResource(R.string.enhance_strength_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = when (state.enhanceStrength) {
                                EnhanceStrength.LIGHT -> stringResource(R.string.enhance_strength_light)
                                EnhanceStrength.MEDIUM -> stringResource(R.string.enhance_strength_medium)
                                EnhanceStrength.STRONG -> stringResource(R.string.enhance_strength_strong)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = state.enhanceStrength.toIndex().toFloat(),
                        onValueChange = { floatVal ->
                            actions.onEnhanceStrengthChange(EnhanceStrength.fromIndex(floatVal.roundToInt()))
                        },
                        valueRange = 0f..2f,
                        steps = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.enhance_strength_light),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.enhance_strength_medium),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.enhance_strength_strong),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.equalizer)) },
                icon = { Icon(painterResource(R.drawable.equalizer), null) },
                onClick = actions.onEqualizerClick,
                showChevron = true,
            )
        }
        item {
            PreferenceEntry(
                title = { Text("Spatial Audio") },
                description = "Soundstage, crossfeed, and acoustic balance",
                icon = { Icon(painterResource(R.drawable.waves), null) },
                onClick = actions.onSpatialAudioClick,
                showChevron = true,
            )
        }
        item {
            var showAutoEqDialog by remember { mutableStateOf(false) }
            if (showAutoEqDialog) {
                AutoEqPickerDialog(onDismiss = { showAutoEqDialog = false })
            }
            PreferenceEntry(
                title = { Text("Headphone Correction (AutoEq)") },
                description = "Harmonic curve correction for 8,000+ headphones",
                icon = { Icon(painterResource(R.drawable.headphones), null) },
                onClick = { showAutoEqDialog = true },
                showChevron = true,
            )
        }
        item {
            var showSignalPathDialog by remember { mutableStateOf(false) }
            if (showSignalPathDialog) {
                SignalPathDialog(onDismiss = { showSignalPathDialog = false })
            }
            PreferenceEntry(
                title = { Text("Audio Signal Path") },
                description = "Source, decoder, DSP, and output telemetry",
                icon = { Icon(painterResource(R.drawable.volume_up), null) },
                onClick = { showSignalPathDialog = true },
                showChevron = true,
            )
        }
        item {
            var showBetaDialog by remember { mutableStateOf(false) }
            if (showBetaDialog) {
                DualPlayBetaDialog(onDismiss = { showBetaDialog = false })
            }
            PreferenceEntry(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Dual Play")
                        DualPlayBetaChip()
                        DualPlayBetaInfoButton(onClick = { showBetaDialog = true })
                    }
                },
                description = "Two independent songs simultaneously (Left/Right ear)",
                icon = { Icon(painterResource(R.drawable.waves), null) },
                onClick = actions.onDualPlayClick,
                showChevron = true,
            )
        }
    }
}
