/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.playlistimport.AddPlaylistImportState
import moe.rukamori.archivetune.playlistimport.AddPlaylistPreviewState
import moe.rukamori.archivetune.playlistimport.AddPlaylistViewModel
import moe.rukamori.archivetune.playlistimport.ImportedPlaylist
import moe.rukamori.archivetune.playlistimport.PlaylistServiceType
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.ozoneClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistBottomSheet(
    onDismissRequest: () -> Unit,
    onPlaylistAdded: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val url by viewModel.url.collectAsStateWithLifecycle()
    val serviceType by viewModel.serviceType.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // Auto-fill clipboard link if it's a supported playlist link
    LaunchedEffect(Unit) {
        val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
        if (clipText.isNotEmpty() && url.isEmpty()) {
            if (clipText.startsWith("http://", ignoreCase = true) ||
                clipText.startsWith("https://", ignoreCase = true) ||
                clipText.startsWith("spotify:", ignoreCase = true)
            ) {
                viewModel.onUrlChanged(clipText, triggerAutoFetch = true)
            }
        }
    }

    // Handle import completion
    LaunchedEffect(importState) {
        if (importState is AddPlaylistImportState.Success) {
            val savedPlaylist = (importState as AddPlaylistImportState.Success).playlist
            viewModel.reset()
            onPlaylistAdded(savedPlaylist)
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismissRequest()
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.BottomSheetHorizontalPadding)
                .padding(bottom = SettingsDimensions.BottomSheetBottomPadding)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = SettingsDimensions.GlassBorderThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            tonalElevation = SettingsDimensions.BottomSheetTonalElevation,
            shadowElevation = SettingsDimensions.BottomSheetShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                    .padding(
                        top = SettingsDimensions.BottomSheetContentPaddingTop,
                        bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = SettingsDimensions.BottomSheetDragHandleBottomPadding)
                        .size(
                            width = SettingsDimensions.BottomSheetDragHandleWidth,
                            height = SettingsDimensions.BottomSheetDragHandleHeight,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )

                // Header
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.add_playlist),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.paste_public_playlist_link),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Rounded URL Input with Paste Button
                OutlinedTextField(
                    value = url,
                    onValueChange = { viewModel.onUrlChanged(it, triggerAutoFetch = true) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.playlist_link_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            if (url.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .ozoneClickable(onClick = { viewModel.onUrlChanged("") }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .border(
                                        width = SettingsDimensions.GlassBorderThickness,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .ozoneClickable {
                                        val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                        if (clipText.isNotEmpty()) {
                                            viewModel.onUrlChanged(clipText, triggerAutoFetch = true)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.paste),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.fetchPreview()
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Service Auto-Detect Chip
                if (url.isNotBlank()) {
                    ServiceDetectionChip(serviceType = serviceType)
                }

                // Preview / Status Section
                when (val preview = previewState) {
                    is AddPlaylistPreviewState.Idle -> {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    is AddPlaylistPreviewState.Loading -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                            border = BorderStroke(
                                width = SettingsDimensions.GlassBorderThickness,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.fetching_playlist_preview),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is AddPlaylistPreviewState.Success -> {
                        PlaylistPreviewCard(
                            playlist = preview.playlist,
                            isDuplicate = preview.isDuplicate,
                        )
                    }

                    is AddPlaylistPreviewState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            border = BorderStroke(
                                width = SettingsDimensions.GlassBorderThickness,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preview.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                TextButton(onClick = { viewModel.fetchPreview() }) {
                                    Text(
                                        text = stringResource(R.string.retry),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                // Error during import
                if (importState is AddPlaylistImportState.Error) {
                    Text(
                        text = (importState as AddPlaylistImportState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                // Primary "Add Playlist" Button
                val isImporting = importState is AddPlaylistImportState.Importing
                val canSubmit = previewState is AddPlaylistPreviewState.Success && !isImporting

                Button(
                    onClick = { viewModel.onAddClicked(forceDuplicate = false) },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.importing_playlist),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.add_playlist),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }

    // Duplicate Prompt Dialog
    val currentImportState = importState
    if (currentImportState is AddPlaylistImportState.DuplicatePrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicatePrompt() },
            title = {
                Text(
                    text = stringResource(R.string.playlist_already_in_library),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.duplicate_playlist_prompt,
                        currentImportState.playlist.name,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onAddClicked(forceDuplicate = true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(stringResource(R.string.add_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicatePrompt() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ServiceDetectionChip(
    serviceType: PlaylistServiceType,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor, label) = when (serviceType) {
        PlaylistServiceType.SPOTIFY -> Triple(
            Color(0xFF1DB954).copy(alpha = 0.16f),
            Color(0xFF1DB954),
            "Spotify",
        )

        PlaylistServiceType.YOUTUBE_MUSIC -> Triple(
            Color(0xFFFF0033).copy(alpha = 0.16f),
            Color(0xFFFF0033),
            "YouTube Music",
        )

        PlaylistServiceType.YOUTUBE -> Triple(
            Color(0xFFFF0000).copy(alpha = 0.16f),
            Color(0xFFFF0000),
            "YouTube",
        )

        PlaylistServiceType.UNSUPPORTED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.unsupported_link),
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = SettingsDimensions.GlassBorderThickness,
                color = textColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            color = textColor,
        )
    }
}

@Composable
private fun PlaylistPreviewCard(
    playlist: ImportedPlaylist,
    isDuplicate: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(
            width = SettingsDimensions.GlassBorderThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = playlist.coverUrl,
                placeholder = painterResource(R.drawable.queue_music),
                error = painterResource(R.drawable.queue_music),
                fallback = painterResource(R.drawable.queue_music),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${playlist.tracks.size} ${stringResource(R.string.tracks_label)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = playlist.serviceType.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (isDuplicate) {
                    Text(
                        text = stringResource(R.string.playlist_already_in_library),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
