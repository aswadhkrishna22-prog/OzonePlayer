/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.ViewParent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.exoplayer.offline.Download
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
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

    fun resolveExpectedCodec(
        quality: AudioQuality,
        networkMeteredOrLowData: Boolean,
    ): String? {
        val effectiveQuality = PlaybackFormatSelector.resolveEffectiveAudioQuality(quality, networkMeteredOrLowData)
        return when (effectiveQuality) {
            AudioQuality.HIGHEST -> "AAC"
            AudioQuality.HIGH -> "Opus"
            AudioQuality.LOW -> "Opus"
            AudioQuality.AUTO -> if (networkMeteredOrLowData) "Opus" else "AAC"
        }
    }

    fun formatQualityChip(
        audioQuality: AudioQuality,
        networkMeteredOrLowData: Boolean,
        bitrateBps: Int,
    ): String {
        val codec = resolveExpectedCodec(audioQuality, networkMeteredOrLowData)
        val kbps = bitrateBps / 1000
        val effectiveQuality = PlaybackFormatSelector.resolveEffectiveAudioQuality(audioQuality, networkMeteredOrLowData)
        return if (!codec.isNullOrBlank()) {
            "$codec, up to $kbps kbps"
        } else {
            val qualityName = effectiveQuality.name.lowercase().replaceFirstChar { it.uppercase() }
            "$qualityName, up to $kbps kbps"
        }
    }
}

enum class PlaylistDownloadSelectionMode {
    ALL,
    CUSTOM,
}

private fun findDialogWindow(view: View): android.view.Window? {
    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) {
            return parent.window
        }
        parent = parent.parent
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
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
    accentColor: Color? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val animationsDisabled = LocalAnimationsDisabled.current

    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val windowManager = remember(context) { context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager }

    val isBlurSupported = remember(animationsDisabled) {
        if (animationsDisabled) return@remember false
        if (powerManager?.isPowerSaveMode == true) return@remember false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                windowManager?.isCrossWindowBlurEnabled == true
            }.getOrDefault(false)
        } else {
            false
        }
    }

    // Attach native cross-window blur on Android 12+ while dialog window is alive
    DisposableEffect(view, isBlurSupported) {
        val window = findDialogWindow(view)
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isBlurSupported) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val blurPx = (24 * view.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    runCatching {
                        window.attributes = window.attributes.apply {
                            blurBehindRadius = blurPx
                        }
                    }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null) {
                runCatching {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
    }

    // Smooth entrance and exit animation (fade + scale 0.96 to 1 + 24dp rise in 280ms)
    val animatable = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    fun animateDismiss() {
        if (isDismissing) return
        isDismissing = true
        if (animationsDisabled) {
            onDismiss()
        } else {
            coroutineScope.launch {
                animatable.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                )
                onDismiss()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (animationsDisabled) {
            animatable.snapTo(1f)
        } else {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
        }
    }

    BackHandler(enabled = true) {
        animateDismiss()
    }

    // Compute effective audio quality & bitrate using downloader's logic
    val isLowData = remember { context.isLowDataModeActive() }
    var effectiveAudioQuality by remember { mutableStateOf(AudioQuality.AUTO) }

    LaunchedEffect(isLowData) {
        effectiveAudioQuality = DownloadUtil.resolveDownloadAudioQuality(context, isLowData)
    }

    val effectiveBitrateBps = remember(effectiveAudioQuality, isLowData) {
        PlaybackFormatSelector.resolveTargetBitrateBps(effectiveAudioQuality, isLowData)
    }

    val qualityChipText = remember(effectiveAudioQuality, isLowData, effectiveBitrateBps) {
        PlaylistDownloadCalculator.formatQualityChip(effectiveAudioQuality, isLowData, effectiveBitrateBps)
    }

    // Extract playlist cover vibrant tint if not passed explicitly
    var extractedCoverColor by remember { mutableStateOf<Color?>(null) }
    val coverColor = accentColor ?: extractedCoverColor

    LaunchedEffect(thumbnailUrl, accentColor) {
        if (accentColor == null && thumbnailUrl != null) {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(100, 100)
                .allowHardware(false)
                .build()
            val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
            val bitmap = result?.image?.toBitmap()
            if (bitmap != null) {
                val palette = withContext(Dispatchers.Default) {
                    Palette.from(bitmap).maximumColorCount(16).generate()
                }
                val vibrantRgb = palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.swatches.firstOrNull()?.rgb
                if (vibrantRgb != null) {
                    extractedCoverColor = Color(vibrantRgb)
                }
            }
        }
    }

    // Card surface color: 66% alpha when blurred, 94% fallback when unsupported, tinted with cover color
    val cardBaseColor = MaterialTheme.colorScheme.surface
    val effectiveAlpha = if (isBlurSupported) 0.66f else 0.94f
    val baseSurfaceColor = cardBaseColor.copy(alpha = effectiveAlpha)
    val surfaceColor = remember(baseSurfaceColor, coverColor) {
        if (coverColor != null) {
            coverColor.copy(alpha = 0.08f).compositeOver(baseSurfaceColor)
        } else {
            baseSurfaceColor
        }
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

    // Partition downloadable songs
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

    val wholePlaylistEstimate = remember(songs, effectiveBitrateBps) {
        PlaylistDownloadCalculator.calculateDownloadEstimate(songs, effectiveBitrateBps)
    }

    val usableStorageBytes = remember {
        runCatching {
            StorageLocationRepository.cacheDirectory(context, StorageFolderKind.DOWNLOADS).usableSpace
        }.getOrDefault(0L)
    }

    fun handleDownloadConfirmed() {
        if (selectedSongs.isNotEmpty()) {
            onStartDownload(selectedSongs)
            animateDismiss()
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
        val currentSet = effectiveSelectedIds.toMutableSet()
        if (currentlySelected) {
            currentSet.remove(songId)
        } else {
            currentSet.add(songId)
        }
        customSelectedIds = currentSet
        val allEligibleIds = eligibleSongs.map { it.id }.toSet()
        if (allEligibleIds.isNotEmpty() && currentSet.containsAll(allEligibleIds)) {
            selectionMode = PlaylistDownloadSelectionMode.ALL
        } else {
            selectionMode = PlaylistDownloadSelectionMode.CUSTOM
        }
    }

    val isSearchActive = searchQuery.isNotBlank()
    val areAllTargetSelected by remember(isSearchActive, filteredEligibleSongs, eligibleSongs, effectiveSelectedIds) {
        derivedStateOf {
            if (isSearchActive) {
                filteredEligibleSongs.isNotEmpty() && filteredEligibleSongs.all { it.id in effectiveSelectedIds }
            } else {
                eligibleSongs.isNotEmpty() && eligibleSongs.all { it.id in effectiveSelectedIds }
            }
        }
    }

    fun handleToggleClick() {
        if (areAllTargetSelected) {
            if (isSearchActive) {
                customSelectedIds = effectiveSelectedIds - filteredEligibleSongs.map { it.id }.toSet()
            } else {
                customSelectedIds = emptySet()
            }
            selectionMode = PlaylistDownloadSelectionMode.CUSTOM
        } else {
            if (isSearchActive) {
                customSelectedIds = effectiveSelectedIds + filteredEligibleSongs.map { it.id }.toSet()
                selectionMode = PlaylistDownloadSelectionMode.CUSTOM
            } else {
                customSelectedIds = eligibleSongs.map { it.id }.toSet()
                selectionMode = PlaylistDownloadSelectionMode.ALL
            }
        }
    }

    val toggleButtonText = if (areAllTargetSelected) {
        stringResource(R.string.playlist_download_custom)
    } else {
        if (isSearchActive) {
            stringResource(R.string.playlist_download_select_results, filteredEligibleSongs.size)
        } else {
            stringResource(R.string.playlist_download_select_all)
        }
    }

    val downloadedCount = remember(songs, downloads) {
        songs.count { downloads[it.id]?.state == Download.STATE_COMPLETED }
    }
    val unknownDurationCount = remember(songs) {
        songs.count { it.durationSeconds <= 0 }
    }

    val selectedStatusText = remember(selectedSongs.size, songs.size, downloadedCount, unknownDurationCount) {
        buildString {
            append("${selectedSongs.size} of ${songs.size} selected")
            if (downloadedCount > 0) {
                append(" ($downloadedCount already downloaded)")
            }
            if (unknownDurationCount > 0) {
                val unknownText = if (unknownDurationCount == 1) {
                    "1 song without duration"
                } else {
                    "$unknownDurationCount songs without duration"
                }
                append(" • $unknownText")
            }
        }
    }

    Dialog(
        onDismissRequest = { animateDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val maxSheetHeight = maxHeight * 0.82f

            // Black scrim (38% alpha) with smooth fade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = animatable.value
                    }
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { animateDismiss() },
                    ),
            )

            // Floating card container with 10dp margins and navigationBarsPadding
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .heightIn(max = maxSheetHeight)
                        .graphicsLayer {
                            alpha = animatable.value
                            scaleX = 0.96f + 0.04f * animatable.value
                            scaleY = 0.96f + 0.04f * animatable.value
                            translationY = (1f - animatable.value) * 24.dp.toPx()
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                    shadowElevation = 16.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        // 1. HEADER: small cover (56dp, 14dp radius), playlist title (up to 2 lines, 17sp medium), round X button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (thumbnailUrl != null) {
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
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

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 22.sp,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { animateDismiss() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = stringResource(android.R.string.cancel),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        // 2. CHIPS: wrapping row of chips (quality, N songs, total duration, up to ~X MB)
                        val totalDurationText = remember(wholePlaylistEstimate.totalDurationSeconds) {
                            PlaylistDownloadCalculator.formatDurationHoursMinutes(wholePlaylistEstimate.totalDurationSeconds)
                        }
                        val wholePlaylistSizeText = remember(wholePlaylistEstimate.totalBytes) {
                            formatFileSize(wholePlaylistEstimate.totalBytes)
                        }

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlaylistChip(text = qualityChipText)
                            PlaylistChip(text = stringResource(R.string.playlist_download_songs_count, songs.size))
                            PlaylistChip(text = totalDurationText)
                            PlaylistChip(text = stringResource(R.string.playlist_download_whole_estimate, wholePlaylistSizeText))
                        }

                        // Status line: "N of M selected" and "(k already downloaded)"
                        Text(
                            text = selectedStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )

                        // 3. Search field (local filter)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.playlist_download_search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = coverColor ?: MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 6.dp),
                        )

                        val primaryColor = MaterialTheme.colorScheme.primary
                        val checkboxCheckedColor = remember(coverColor, primaryColor) {
                            if (coverColor != null && coverColor.luminance() >= 0.15f) {
                                coverColor
                            } else {
                                primaryColor
                            }
                        }
                        val checkboxCheckmarkColor = if (checkboxCheckedColor.luminance() > 0.5f) Color.Black else Color.White

                        // 4. LazyColumn of songs (shrinks with weight(1f) when keyboard opens)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
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
                                        .padding(vertical = 5.dp, horizontal = 2.dp)
                                        .graphicsLayer {
                                            if (isDownloaded) alpha = 0.45f
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (song.thumbnailUrl != null) {
                                        AsyncImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.music_note),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val durationText = if (song.durationSeconds > 0) makeTimeString(song.durationSeconds * 1000L) else ""
                                        val subtitle = if (durationText.isNotEmpty()) "${song.artistNames} • $durationText" else song.artistNames
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isDownloaded) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.playlist_download_downloaded),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    } else {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { toggleRow(song.id) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = checkboxCheckedColor,
                                                uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                checkmarkColor = checkboxCheckmarkColor,
                                            ),
                                        )
                                    }
                                }
                            }

                            if (hasMoreSongs) {
                                item(key = "loading_more_songs_indicator") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = coverColor ?: MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.playlist_download_loading_more, songs.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // 5. BOTTOM BAR: left outlined pill toggle, right filled accent pill
                        val formattedEstimateSize = remember(estimate.totalBytes) { formatFileSize(estimate.totalBytes) }
                        val accentFillColor = coverColor ?: MaterialTheme.colorScheme.primary
                        val isAccentBright = accentFillColor.luminance() > 0.5f
                        val accentContentColor = if (isAccentBright) Color.Black else Color.White
                        val downloadButtonText = stringResource(R.string.playlist_download_button, selectedSongs.size, formattedEstimateSize)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { handleToggleClick() },
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = toggleButtonText,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                )
                            }

                            Button(
                                onClick = { handleDownloadClick() },
                                enabled = selectedSongs.isNotEmpty(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentFillColor,
                                    contentColor = accentContentColor,
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = downloadButtonText,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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

@Composable
private fun PlaylistChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
