/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.isLowDataModeActive
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.resolver.PlaybackFormatSelector

@Immutable
data class DownloadableSong(
    val id: String,
    val title: String,
    val artistNames: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
)

@Immutable
data class DownloadEstimate(
    val totalBytes: Long,
    val totalDurationSeconds: Long,
    val unknownDurationCount: Int,
)

object PlaylistDownloadCalculator {
    fun calculateDownloadEstimate(
        songs: Collection<DownloadableSong>,
        bitrateBps: Int,
    ): DownloadEstimate {
        var totalDurationSeconds = 0L
        var unknownDurationCount = 0

        for (song in songs) {
            if (song.durationSeconds > 0) {
                totalDurationSeconds += song.durationSeconds
            } else {
                unknownDurationCount++
            }
        }

        val estimatedBytes = totalDurationSeconds * (bitrateBps / 8L)
        return DownloadEstimate(
            totalBytes = estimatedBytes,
            totalDurationSeconds = totalDurationSeconds,
            unknownDurationCount = unknownDurationCount,
        )
    }

    fun formatDurationHoursMinutes(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0 min"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours} hr ${minutes} min"
            hours > 0 -> "${hours} hr"
            minutes > 0 -> "${minutes} min"
            else -> "${totalSeconds}s"
        }
    }
}

enum class PlaylistDownloadSelectionMode {
    ALL,
    CUSTOM,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDownloadSheet(
    title: String,
    thumbnailUrl: String?,
    songs: List<DownloadableSong>,
    downloads: Map<String, Download>,
    hasMoreSongs: Boolean = false,
    onLoadMore: () -> Unit = {},
    onDismiss: () -> Unit,
    onStartDownload: (List<DownloadableSong>) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Compute effective audio quality & bitrate using the downloader's own logic
    val isLowData = remember { context.isLowDataModeActive() }
    var effectiveAudioQuality by remember { mutableStateOf(AudioQuality.AUTO) }

    LaunchedEffect(isLowData) {
        effectiveAudioQuality = DownloadUtil.resolveDownloadAudioQuality(context, isLowData)
    }

    val effectiveBitrateBps = remember(effectiveAudioQuality, isLowData) {
        PlaybackFormatSelector.resolveTargetBitrateBps(effectiveAudioQuality, isLowData)
    }

    val qualityLabel = remember(effectiveAudioQuality, isLowData) {
        val quality = PlaybackFormatSelector.resolveEffectiveAudioQuality(effectiveAudioQuality, isLowData)
        val kbps = effectiveBitrateBps / 1000
        "${quality.name.lowercase().replaceFirstChar { it.uppercase() }} (${kbps} kbps)"
    }

    // Trigger background loading of subsequent pages while sheet is open
    LaunchedEffect(hasMoreSongs, songs.size) {
        if (hasMoreSongs) {
            onLoadMore()
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectionMode by rememberSaveable { mutableStateOf(PlaylistDownloadSelectionMode.ALL) }
    var customSelectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showStorageWarningDialog by remember { mutableStateOf(false) }

    // Helper partition of un-downloaded songs
    val eligibleSongs = remember(songs, downloads) {
        songs.filter { song ->
            downloads[song.id]?.state != Download.STATE_COMPLETED
        }
    }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            val q = searchQuery.trim().lowercase()
            songs.filter {
                it.title.lowercase().contains(q) || it.artistNames.lowercase().contains(q)
            }
        }
    }

    val filteredEligibleSongs = remember(filteredSongs, downloads) {
        filteredSongs.filter { song ->
            downloads[song.id]?.state != Download.STATE_COMPLETED
        }
    }

    // Derive effective selection
    val effectiveSelectedIds by remember(selectionMode, customSelectedIds, eligibleSongs, searchQuery, filteredEligibleSongs) {
        derivedStateOf {
            when (selectionMode) {
                PlaylistDownloadSelectionMode.ALL -> {
                    if (searchQuery.isNotBlank()) {
                        filteredEligibleSongs.map { it.id }.toSet()
                    } else {
                        eligibleSongs.map { it.id }.toSet()
                    }
                }
                PlaylistDownloadSelectionMode.CUSTOM -> {
                    customSelectedIds.intersect(eligibleSongs.map { it.id }.toSet())
                }
            }
        }
    }

    val selectedSongs = remember(songs, effectiveSelectedIds) {
        songs.filter { it.id in effectiveSelectedIds }
    }

    val estimate = remember(selectedSongs, effectiveBitrateBps) {
        PlaylistDownloadCalculator.calculateDownloadEstimate(selectedSongs, effectiveBitrateBps)
    }

    val usableStorageBytes = remember {
        runCatching {
            StorageLocationRepository.cacheDirectory(context, StorageFolderKind.DOWNLOADS).usableSpace
        }.getOrDefault(0L)
    }

    fun handleDownloadConfirmed() {
        if (selectedSongs.isNotEmpty()) {
            onStartDownload(selectedSongs)
            onDismiss()
        }
    }

    fun handleDownloadClick() {
        if (estimate.totalBytes > usableStorageBytes && usableStorageBytes > 0L) {
            showStorageWarningDialog = true
        } else {
            handleDownloadConfirmed()
        }
    }

    fun toggleRow(songId: String) {
        val currentlySelected = songId in effectiveSelectedIds
        when (selectionMode) {
            PlaylistDownloadSelectionMode.ALL -> {
                // Switch to CUSTOM with this item toggled
                val base = if (searchQuery.isNotBlank()) {
                    filteredEligibleSongs.map { it.id }.toMutableSet()
                } else {
                    eligibleSongs.map { it.id }.toMutableSet()
                }
                if (currentlySelected) {
                    base.remove(songId)
                } else {
                    base.add(songId)
                }
                customSelectedIds = base
                selectionMode = PlaylistDownloadSelectionMode.CUSTOM
            }
            PlaylistDownloadSelectionMode.CUSTOM -> {
                customSelectedIds = if (currentlySelected) {
                    customSelectedIds - songId
                } else {
                    customSelectedIds + songId
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = Color.Transparent,
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left: Two-state toggle button: "Select all" / "Custom"
                        val isSelectAllMode = selectionMode == PlaylistDownloadSelectionMode.ALL
                        val toggleText = if (isSelectAllMode) {
                            if (searchQuery.isNotBlank()) {
                                stringResource(R.string.playlist_download_select_results, filteredEligibleSongs.size)
                            } else {
                                stringResource(R.string.playlist_download_select_all)
                            }
                        } else {
                            stringResource(R.string.playlist_download_custom)
                        }

                        OutlinedButton(
                            onClick = {
                                if (isSelectAllMode) {
                                    // Switch to custom with cleared selection
                                    customSelectedIds = emptySet()
                                    selectionMode = PlaylistDownloadSelectionMode.CUSTOM
                                } else {
                                    // Switch to select all
                                    selectionMode = PlaylistDownloadSelectionMode.ALL
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelectAllMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelectAllMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                            ),
                        ) {
                            Text(
                                text = toggleText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelectAllMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        // Right: Download button: "Download (N, ~X MB)"
                        val formattedSize = remember(estimate.totalBytes) { formatFileSize(estimate.totalBytes) }
                        Button(
                            onClick = { handleDownloadClick() },
                            enabled = selectedSongs.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_download_button, selectedSongs.size, formattedSize),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
            ) {
                // Top drag indicator and close row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // HEADER: Image + Title and Facts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = qualityLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }

                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Text(
                                text = stringResource(R.string.playlist_download_songs_selected, selectedSongs.size, songs.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val formattedDuration = remember(estimate.totalDurationSeconds) {
                            PlaylistDownloadCalculator.formatDurationHoursMinutes(estimate.totalDurationSeconds)
                        }
                        val formattedEstimateSize = remember(estimate.totalBytes) {
                            formatFileSize(estimate.totalBytes)
                        }

                        Text(
                            text = "$formattedDuration • Estimated size ~$formattedEstimateSize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (estimate.unknownDurationCount > 0) {
                            val unknownNote = if (estimate.unknownDurationCount == 1) {
                                stringResource(R.string.playlist_download_one_unknown_duration)
                            } else {
                                stringResource(R.string.playlist_download_unknown_durations, estimate.unknownDurationCount)
                            }
                            Text(
                                text = unknownNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field (local filter)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.playlist_download_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // LazyList of songs
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(
                        items = filteredSongs,
                        key = { it.id },
                    ) { song ->
                        val isDownloaded = downloads[song.id]?.state == Download.STATE_COMPLETED
                        val isSelected = song.id in effectiveSelectedIds

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isDownloaded) {
                                    toggleRow(song.id)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (song.thumbnailUrl != null) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isDownloaded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val durationText = if (song.durationSeconds > 0) makeTimeString(song.durationSeconds * 1000L) else ""
                                val subtitle = if (durationText.isNotEmpty()) "${song.artistNames} • $durationText" else song.artistNames
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDownloaded) 0.6f else 0.85f),
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isDownloaded) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.playlist_download_downloaded),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            } else {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { toggleRow(song.id) },
                                )
                            }
                        }
                    }

                    if (hasMoreSongs) {
                        item(key = "loading_more_songs_indicator") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.playlist_download_loading_more, songs.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStorageWarningDialog) {
        val formattedNeeded = remember(estimate.totalBytes) { formatFileSize(estimate.totalBytes) }
        val formattedFree = remember(usableStorageBytes) { formatFileSize(usableStorageBytes) }

        AlertDialog(
            onDismissRequest = { showStorageWarningDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.playlist_download_storage_warning_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.playlist_download_storage_warning_message, formattedNeeded, formattedFree),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStorageWarningDialog = false
                        handleDownloadConfirmed()
                    },
                ) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageWarningDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

fun SongItem.toDownloadableSong(): DownloadableSong =
    DownloadableSong(
        id = id,
        title = title,
        artistNames = artists.joinToString { it.name },
        durationSeconds = duration ?: 0,
        thumbnailUrl = thumbnail,
    )

fun PlaylistSong.toDownloadableSong(): DownloadableSong =
    DownloadableSong(
        id = song.id,
        title = song.song.title,
        artistNames = song.artists.joinToString { it.name },
        durationSeconds = song.song.duration,
        thumbnailUrl = song.song.thumbnailUrl,
    )

fun Song.toDownloadableSong(): DownloadableSong =
    DownloadableSong(
        id = id,
        title = song.title,
        artistNames = artists.joinToString { it.name },
        durationSeconds = song.duration,
        thumbnailUrl = song.thumbnailUrl,
    )
