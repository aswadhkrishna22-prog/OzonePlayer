/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AnimateSearchResultsKey
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnCandidate
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnDecryptor
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnQualityCache
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnQualityTier
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.formatIdForSource
import moe.rukamori.archivetune.playback.queues.JioSaavnQueue
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.spotify.PlaybackResolutionDecision
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.spotify.mapping.SpotifyEntityMapper
import moe.rukamori.archivetune.ui.menu.VersionPickerBottomSheet
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.screens.playlist.isResolvedAs
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyAlbumSectionRow
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyArtistSectionRow
import moe.rukamori.archivetune.ui.screens.spotifyhome.SpotifyPlaylistSectionRow
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.OnlineSearchSource
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel
import moe.rukamori.archivetune.viewmodels.SpotifySearchFilter

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptics = rememberYumaHaptics()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val searchSource by viewModel.searchSource.collectAsStateWithLifecycle()
    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val spotifyFilter by viewModel.spotifyFilter.collectAsStateWithLifecycle()
    val searchSort by viewModel.sort.collectAsStateWithLifecycle()
    val searchSummary = viewModel.summaryPage
    val spotifyResult = viewModel.spotifyResult
    val isSpotifyLoading = viewModel.isSpotifyLoading
    val spotifyError = viewModel.spotifyError
    val jioSaavnResult = viewModel.jioSaavnResult
    val isJioSaavnLoading = viewModel.isJioSaavnLoading
    val jioSaavnError = viewModel.jioSaavnError

    var resolvingTrackId by remember { mutableStateOf<String?>(null) }
    val (isSearchSlideEnabled) = rememberPreference(AnimateSearchResultsKey, defaultValue = true)
    val isSlideAnimationActive = rememberSearchSlideAnimationEnabled(isSearchSlideEnabled)

    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }
    val allModeSections =
        buildList<SearchSummary> {
            searchSummary
                ?.summaries
                ?.firstOrNull()
                ?.takeIf { it.items.isNotEmpty() }
                ?.let(::add)

            listOf(
                FILTER_SONG to stringResource(R.string.filter_songs),
                FILTER_VIDEO to stringResource(R.string.filter_videos),
                FILTER_ALBUM to stringResource(R.string.filter_albums),
                FILTER_ARTIST to stringResource(R.string.filter_artists),
                FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
            ).forEach { (sectionFilter, sectionTitle) ->
                viewModel.viewStateMap[sectionFilter.value]
                    ?.items
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { items ->
                        add(SearchSummary(title = sectionTitle, items = viewModel.sortedItems(items, searchSort)))
                    }
            }
        }
    val isAllModeLoaded =
        searchSummary != null ||
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            ).all { viewModel.viewStateMap.containsKey(it.value) }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        OnlineSearchItemRow(
            item = item,
            navController = navController,
            playerConnection = playerConnection,
            isPlaying = isPlaying,
            mediaMetadata = mediaMetadata,
            coroutineScope = coroutineScope,
            menuState = menuState,
            haptics = haptics,
        )
    }

    val onSpotifyTrackClick: (SpotifyTrack, List<SpotifyTrack>) -> Unit = { track, trackList ->
        val isActive = track.isResolvedAs(mediaMetadata)
        if (isActive) {
            playerConnection.player.togglePlayPause()
        } else {
            coroutineScope.launch {
                resolvingTrackId = track.id
                try {
                    when (val decision = SpotifyPlaybackResolver.resolveCandidateDecision(track, database, viewModel.query)) {
                        is PlaybackResolutionDecision.Resolved -> {
                            playerConnection.playQueue(
                                SpotifyTracksQueue(
                                    allTracks = trackList,
                                    startIndex = trackList.indexOf(track).coerceAtLeast(0),
                                    preloadItem = decision.metadata,
                                    title = "Spotify: ${viewModel.query}",
                                    totalCount = trackList.size,
                                    hasCustomOrder = true,
                                    contextQuery = viewModel.query,
                                ),
                            )
                        }
                        is PlaybackResolutionDecision.Ambiguous -> {
                            menuState.show {
                                VersionPickerBottomSheet(
                                    spotifyTrackId = track.id,
                                    targetTitle = track.name,
                                    targetArtists = track.artists.map { it.name },
                                    targetDurationMs = track.durationMs.toLong(),
                                    targetAlbum = track.album?.name,
                                    targetThumbnail = SpotifyEntityMapper.getTrackThumbnail(track).orEmpty(),
                                    initialCandidates = decision.topCandidates,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        }
                        is PlaybackResolutionDecision.NotFound -> {
                            Toast.makeText(context, "Could not find a playable version of this song", Toast.LENGTH_SHORT).show()
                        }
                    }
                } finally {
                    if (resolvingTrackId == track.id) {
                        resolvingTrackId = null
                    }
                }
            }
        }
    }

    val onJioSaavnTrackClick: (JioSaavnCandidate, List<JioSaavnCandidate>) -> Unit = { candidate, candidateList ->
        val isActive = mediaMetadata?.id == "jiosaavn_${candidate.id}"
        if (isActive) {
            playerConnection.player.togglePlayPause()
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                resolvingTrackId = candidate.id
                try {
                    val mediaItems = candidateList.mapNotNull { item ->
                        val rawUrl = JioSaavnDecryptor.decryptMediaUrl(item.encryptedMediaUrl) ?: return@mapNotNull null
                        val itemMediaId = "jiosaavn_${item.id}"
                        JioSaavnQualityCache.storeRawUrl(itemMediaId, rawUrl)
                        val workingTier = JioSaavnQualityCache.getWorkingTier(itemMediaId)
                        val streamUrl = JioSaavnDecryptor.getUrlForTier(rawUrl, workingTier)
                        val nominalBitrate = if (workingTier.nominalBitrateKbps > 0) workingTier.nominalBitrateKbps * 1000 else 160000

                        val itemMetadata = MediaMetadata(
                            id = itemMediaId,
                            title = item.title,
                            artists = item.artists.map { MediaMetadata.Artist(id = null, name = it) },
                            duration = item.durationSec.toInt(),
                            thumbnailUrl = item.imageUrl?.replace("150x150", "500x500"),
                            album = item.album?.let { MediaMetadata.Album(id = "jiosaavn_album_${item.id}", title = it) },
                        )

                        database.insert(itemMetadata)
                        val formatEntity = FormatEntity(
                            id = formatIdForSource(itemMediaId, PlaybackSource.FLAC),
                            itag = 0,
                            mimeType = "audio/mp4",
                            codecs = "mp4a.40.2",
                            bitrate = nominalBitrate,
                            sampleRate = 44100,
                            contentLength = (nominalBitrate.toLong() / 8L) * item.durationSec,
                            loudnessDb = null,
                            playbackUrl = streamUrl,
                            bitsPerSample = 16,
                        )
                        database.upsert(formatEntity)
                        database.upsert(formatEntity.copy(id = itemMediaId))

                        MediaItem.Builder()
                            .setMediaId(itemMediaId)
                            .setUri(streamUrl.toUri())
                            .setCustomCacheKey(itemMediaId)
                            .setTag(itemMetadata)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(item.title)
                                    .setSubtitle(item.artists.joinToString(", "))
                                    .setArtist(item.artists.joinToString(", "))
                                    .setArtworkUri(itemMetadata.thumbnailUrl?.toUri())
                                    .setAlbumTitle(item.album)
                                    .setIsPlayable(true)
                                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build(),
                            )
                            .build()
                    }

                    if (mediaItems.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not resolve JioSaavn audio stream", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    val targetMediaId = "jiosaavn_${candidate.id}"
                    val startIndex = mediaItems.indexOfFirst { it.mediaId == targetMediaId }.coerceAtLeast(0)

                    withContext(Dispatchers.Main) {
                        playerConnection.playQueue(
                            JioSaavnQueue(
                                title = "JioSaavn: ${viewModel.query}",
                                items = mediaItems,
                                startIndex = startIndex,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to play JioSaavn track")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error playing JioSaavn track", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    if (resolvingTrackId == candidate.id) {
                        resolvingTrackId = null
                    }
                }
            }
        }
    }

    val onJioSaavnTrackMenuClick: (JioSaavnCandidate) -> Unit = { candidate ->
        val itemMediaId = "jiosaavn_${candidate.id}"
        val songItem =
            SongItem(
                id = itemMediaId,
                title = candidate.title,
                artists = candidate.artists.map { Artist(name = it, id = null) },
                album = candidate.album?.let { Album(name = it, id = "jiosaavn_album_${candidate.id}") },
                duration = candidate.durationSec.toInt().takeIf { it > 0 },
                thumbnail = candidate.imageUrl?.replace("150x150", "500x500").orEmpty(),
                explicit = false,
            )
        menuState.show {
            YouTubeSongMenu(
                song = songItem,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val onSpotifyTrackMenuClick: (SpotifyTrack) -> Unit = { track ->
        coroutineScope.launch {
            resolvingTrackId = track.id
            try {
                val metadata = SpotifyPlaybackResolver.resolveToMetadata(track, contextQuery = viewModel.query)
                if (metadata != null) {
                    val songItem =
                        SongItem(
                            id = metadata.id,
                            title = metadata.title,
                            artists = metadata.artists.map { Artist(name = it.name, id = it.id) },
                            album = metadata.album?.let { Album(name = it.title, id = it.id) },
                            duration = metadata.duration.takeIf { it > 0 },
                            thumbnail = metadata.thumbnailUrl.orEmpty(),
                            explicit = metadata.explicit,
                        )
                    menuState.show {
                        YouTubeSongMenu(
                            song = songItem,
                            navController = navController,
                            spotifyTrackId = track.id,
                            onDismiss = menuState::dismiss,
                        )
                    }
                } else {
                    Toast.makeText(context, "Could not resolve track", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (resolvingTrackId == track.id) {
                    resolvingTrackId = null
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        SearchFilterHeader(
            searchSource = searchSource,
            onSourceChange = {
                viewModel.setSearchSource(it)
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            },
            searchFilter = searchFilter,
            onFilterChange = {
                if (viewModel.filter.value != it) {
                    viewModel.filter.value = it
                }
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            },
            spotifyFilter = spotifyFilter,
            onSpotifyFilterChange = {
                if (viewModel.spotifyFilter.value != it) {
                    viewModel.setSpotifyFilter(it)
                }
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            },
        )

        LazyColumn(
            state = lazyListState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .add(WindowInsets(top = 8.dp))
                    .asPaddingValues(),
            modifier = Modifier.weight(1f),
        ) {
            if (searchSource == OnlineSearchSource.YOUTUBE) {
                if (searchFilter == null) {
                    allModeSections.forEachIndexed { index, summary ->
                        if (index > 0) {
                            item(key = "divider_$index", contentType = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }

                        item(
                            key = "section_header_${summary.title}_$index",
                            contentType = "section_header",
                        ) {
                            OnlineSearchSectionHeader(summary = summary)
                        }

                        val sectionSongKey = "${viewModel.searchRequestId}_yt_all_${summary.title}"
                        val shouldAnimateSummarySongs = isSlideAnimationActive && !viewModel.isAnimationPlayed(sectionSongKey)

                        itemsIndexed(
                            items = viewModel.sortedItems(summary.items, searchSort),
                            key = { itemIndex, item -> "${summary.title}/${item.id}/$itemIndex" },
                            contentType = { _, _ -> "search_result" },
                        ) { itemIndex, item ->
                            if (item is SongItem) {
                                SearchSlideRow(
                                    index = itemIndex,
                                    animationKey = sectionSongKey,
                                    shouldAnimate = shouldAnimateSummarySongs,
                                    onAnimationTriggered = {
                                        viewModel.markAnimationPlayed(sectionSongKey)
                                    },
                                ) {
                                    ytItemContent(item)
                                }
                            } else {
                                ytItemContent(item)
                            }
                        }

                        item(
                            key = "section_spacer_${summary.title}_$index",
                            contentType = "section_spacer",
                        ) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (allModeSections.isEmpty() && isAllModeLoaded) {
                        item(key = "empty_all", contentType = "empty") {
                            OnlineSearchEmptyBlock()
                        }
                    }
                } else {
                    val filteredSongKey = "${viewModel.searchRequestId}_yt_filtered_${searchFilter?.value ?: "all"}"
                    val shouldAnimateFilteredSongs = isSlideAnimationActive && !viewModel.isAnimationPlayed(filteredSongKey)

                    itemsIndexed(
                        items = viewModel.sortedItems(itemsPage?.items.orEmpty().distinctBy { it.id }, searchSort),
                        key = { _, item -> "filtered_${item.id}" },
                        contentType = { _, _ -> "search_result" },
                    ) { itemIndex, item ->
                        if (item is SongItem) {
                            SearchSlideRow(
                                index = itemIndex,
                                animationKey = filteredSongKey,
                                shouldAnimate = shouldAnimateFilteredSongs,
                                onAnimationTriggered = {
                                    viewModel.markAnimationPlayed(filteredSongKey)
                                },
                            ) {
                                ytItemContent(item)
                            }
                        } else {
                            ytItemContent(item)
                        }
                    }

                    if (itemsPage?.continuation != null) {
                        item(key = "loading", contentType = "loading") {
                            OnlineSearchLoadingBlock()
                        }
                    }

                    if (itemsPage?.items?.isEmpty() == true) {
                        item(key = "empty_filtered", contentType = "empty") {
                            OnlineSearchEmptyBlock()
                        }
                    }
                }

                if (searchFilter == null && allModeSections.isEmpty() && !isAllModeLoaded || searchFilter != null && itemsPage == null) {
                    item(key = "initial_loading", contentType = "loading") {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            } else if (searchSource == OnlineSearchSource.SPOTIFY) {
                // SPOTIFY SEARCH RESULTS
                if (isSpotifyLoading && spotifyResult == null) {
                    item(key = "spotify_initial_loading", contentType = "loading") {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                } else if (spotifyError != null && spotifyResult == null) {
                    if (spotifyError == R.string.spotify_not_connected) {
                        item(key = "spotify_not_connected", contentType = "error") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.spotify_icon),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(64.dp),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.spotify_not_connected),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Sign in to your Spotify account in Settings to browse and play tracks from Spotify.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { navController.navigate("settings/account") },
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    Text(stringResource(R.string.spotify_connect))
                                }
                            }
                        }
                    } else {
                        item(key = "spotify_general_error", contentType = "error") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_about),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.error_unknown),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = viewModel::retrySpotify,
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    Text(stringResource(R.string.retry_button))
                                }
                            }
                        }
                    }
                } else if (spotifyResult != null) {
                    val tracks = spotifyResult.tracks?.items.orEmpty()
                    val albums = spotifyResult.albums?.items.orEmpty()
                    val artists = spotifyResult.artists?.items.orEmpty()
                    val playlists = spotifyResult.playlists?.items.orEmpty()

                    when (spotifyFilter) {
                        null -> {
                            var hasAnySection = false

                            if (tracks.isNotEmpty()) {
                                hasAnySection = true
                                item(key = "spotify_header_songs", contentType = "section_header") {
                                    OnlineSearchSectionHeader(title = stringResource(R.string.filter_songs))
                                }
                                val topTracks = tracks.take(5)
                                val spotifySongsKey = "${viewModel.searchRequestId}_spotify_songs"
                                val shouldAnimateSpotifySongs = isSlideAnimationActive && !viewModel.isAnimationPlayed(spotifySongsKey)

                                itemsIndexed(
                                    items = topTracks,
                                    key = { idx, track -> "spotify_top_track_${track.id}_$idx" },
                                    contentType = { _, _ -> "spotify_track" },
                                ) { idx, track ->
                                    SearchSlideRow(
                                        index = idx,
                                        animationKey = spotifySongsKey,
                                        shouldAnimate = shouldAnimateSpotifySongs,
                                        onAnimationTriggered = {
                                            viewModel.markAnimationPlayed(spotifySongsKey)
                                        },
                                    ) {
                                        val isTrackActive = remember(track, mediaMetadata) { track.isResolvedAs(mediaMetadata) }
                                        val isTrackResolving = resolvingTrackId == track.id
                                        SpotifyTrackListItem(
                                            track = track,
                                            isActive = isTrackActive || isTrackResolving,
                                            isPlaying = isPlaying && !isTrackResolving,
                                            trailingContent = {
                                                if (isTrackResolving) {
                                                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                                } else {
                                                    IconButton(
                                                        onClick = { onSpotifyTrackMenuClick(track) },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = { onSpotifyTrackClick(track, tracks) },
                                                        onLongClick = {
                                                            haptics.longPress()
                                                            onSpotifyTrackMenuClick(track)
                                                        },
                                                    ),
                                        )
                                    }
                                }
                            }

                            if (albums.isNotEmpty()) {
                                if (hasAnySection) {
                                    item(key = "spotify_div_albums", contentType = "divider") {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                                hasAnySection = true
                                item(key = "spotify_header_albums", contentType = "section_header") {
                                    OnlineSearchSectionHeader(title = stringResource(R.string.filter_albums))
                                }
                                item(key = "spotify_row_albums", contentType = "spotify_albums_row") {
                                    SpotifyAlbumSectionRow(
                                        albums = albums,
                                        onAlbumClick = { album -> navController.navigate("spotify_album/${album.id}") },
                                    )
                                }
                            }

                            if (artists.isNotEmpty()) {
                                if (hasAnySection) {
                                    item(key = "spotify_div_artists", contentType = "divider") {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                                hasAnySection = true
                                item(key = "spotify_header_artists", contentType = "section_header") {
                                    OnlineSearchSectionHeader(title = stringResource(R.string.filter_artists))
                                }
                                item(key = "spotify_row_artists", contentType = "spotify_artists_row") {
                                    SpotifyArtistSectionRow(
                                        artists = artists,
                                        onArtistClick = { artist ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val artistItem =
                                                    YouTube
                                                        .search(artist.name, YouTube.SearchFilter.FILTER_ARTIST)
                                                        .getOrNull()
                                                        ?.items
                                                        ?.firstOrNull() as? ArtistItem
                                                withContext(Dispatchers.Main) {
                                                    if (artistItem != null) {
                                                        navController.navigate("artist/${artistItem.id}")
                                                    } else {
                                                        navController.navigate(onlineSearchResultRoute(artist.name))
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }

                            if (playlists.isNotEmpty()) {
                                if (hasAnySection) {
                                    item(key = "spotify_div_playlists", contentType = "divider") {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                                hasAnySection = true
                                item(key = "spotify_header_playlists", contentType = "section_header") {
                                    OnlineSearchSectionHeader(title = stringResource(R.string.filter_playlists))
                                }
                                item(key = "spotify_row_playlists", contentType = "spotify_playlists_row") {
                                    SpotifyPlaylistSectionRow(
                                        playlists = playlists,
                                        onPlaylistClick = { playlist -> navController.navigate("spotify_playlist/${playlist.id}") },
                                    )
                                }
                            }

                            if (!hasAnySection) {
                                item(key = "spotify_empty_all", contentType = "empty") {
                                    OnlineSearchEmptyBlock()
                                }
                            }
                        }

                        SpotifySearchFilter.SONGS -> {
                            if (tracks.isNotEmpty()) {
                                val spotifyFilterSongsKey = "${viewModel.searchRequestId}_spotify_songs"
                                val shouldAnimateSpotifyFilter = isSlideAnimationActive && !viewModel.isAnimationPlayed(spotifyFilterSongsKey)

                                itemsIndexed(
                                    items = tracks,
                                    key = { idx, track -> "spotify_full_track_${track.id}_$idx" },
                                    contentType = { _, _ -> "spotify_track" },
                                ) { idx, track ->
                                    SearchSlideRow(
                                        index = idx,
                                        animationKey = spotifyFilterSongsKey,
                                        shouldAnimate = shouldAnimateSpotifyFilter,
                                        onAnimationTriggered = {
                                            viewModel.markAnimationPlayed(spotifyFilterSongsKey)
                                        },
                                    ) {
                                        val isTrackActive = remember(track, mediaMetadata) { track.isResolvedAs(mediaMetadata) }
                                        val isTrackResolving = resolvingTrackId == track.id
                                        SpotifyTrackListItem(
                                            track = track,
                                            isActive = isTrackActive || isTrackResolving,
                                            isPlaying = isPlaying && !isTrackResolving,
                                            trailingContent = {
                                                if (isTrackResolving) {
                                                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                                } else {
                                                    IconButton(
                                                        onClick = { onSpotifyTrackMenuClick(track) },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_vert),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .combinedClickable(
                                                        onClick = { onSpotifyTrackClick(track, tracks) },
                                                        onLongClick = {
                                                            haptics.longPress()
                                                            onSpotifyTrackMenuClick(track)
                                                        },
                                                    ),
                                        )
                                    }
                                }
                            } else {
                                item(key = "spotify_empty_songs", contentType = "empty") {
                                    OnlineSearchEmptyBlock()
                                }
                            }
                        }

                        SpotifySearchFilter.ALBUMS -> {
                            if (albums.isNotEmpty()) {
                                items(
                                    items = albums,
                                    key = { "spotify_list_album_${it.id}" },
                                    contentType = { "spotify_album" },
                                ) { album ->
                                    val thumbnail =
                                        remember(album.id) {
                                            album.images.maxByOrNull { it.width ?: 0 }?.url
                                                ?: album.images.firstOrNull()?.url.orEmpty()
                                        }
                                    ListItem(
                                        title = album.name,
                                        subtitle = album.artists.joinToString { it.name },
                                        thumbnailContent = {
                                            ItemThumbnail(
                                                thumbnailUrl = thumbnail.resize(200, 200),
                                                isActive = false,
                                                isPlaying = false,
                                                shape = RoundedCornerShape(ThumbnailCornerRadius),
                                                modifier = Modifier.size(ListThumbnailSize),
                                            )
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { navController.navigate("spotify_album/${album.id}") },
                                    )
                                }
                            } else {
                                item(key = "spotify_empty_albums", contentType = "empty") {
                                    OnlineSearchEmptyBlock()
                                }
                            }
                        }

                        SpotifySearchFilter.ARTISTS -> {
                            if (artists.isNotEmpty()) {
                                items(
                                    items = artists,
                                    key = { "spotify_list_artist_${it.id}" },
                                    contentType = { "spotify_artist" },
                                ) { artist ->
                                    val thumbnail =
                                        remember(artist.id) {
                                            artist.images.maxByOrNull { it.width ?: 0 }?.url
                                                ?: artist.images.firstOrNull()?.url.orEmpty()
                                        }
                                    ListItem(
                                        title = artist.name,
                                        subtitle = stringResource(R.string.filter_artists),
                                        thumbnailContent = {
                                            ItemThumbnail(
                                                thumbnailUrl = thumbnail.resize(200, 200),
                                                isActive = false,
                                                isPlaying = false,
                                                shape = CircleShape,
                                                modifier = Modifier.size(ListThumbnailSize),
                                            )
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val artistItem =
                                                            YouTube
                                                                .search(artist.name, YouTube.SearchFilter.FILTER_ARTIST)
                                                                .getOrNull()
                                                                ?.items
                                                                ?.firstOrNull() as? ArtistItem
                                                        withContext(Dispatchers.Main) {
                                                            if (artistItem != null) {
                                                                navController.navigate("artist/${artistItem.id}")
                                                            } else {
                                                                navController.navigate(onlineSearchResultRoute(artist.name))
                                                            }
                                                        }
                                                    }
                                                },
                                    )
                                }
                            } else {
                                item(key = "spotify_empty_artists", contentType = "empty") {
                                    OnlineSearchEmptyBlock()
                                }
                            }
                        }

                        SpotifySearchFilter.PLAYLISTS -> {
                            if (playlists.isNotEmpty()) {
                                items(
                                    items = playlists,
                                    key = { "spotify_list_playlist_${it.id}" },
                                    contentType = { "spotify_playlist" },
                                ) { playlist ->
                                    val thumbnail =
                                        remember(playlist.id) {
                                            playlist.images.maxByOrNull { it.width ?: 0 }?.url
                                                ?: playlist.images.firstOrNull()?.url.orEmpty()
                                        }
                                    val subtitle =
                                        joinByBullet(
                                            playlist.owner?.displayName,
                                            playlist.tracks?.total?.let { "$it ${stringResource(R.string.tracks_label)}" },
                                        )
                                    ListItem(
                                        title = playlist.name,
                                        subtitle = subtitle,
                                        thumbnailContent = {
                                            ItemThumbnail(
                                                thumbnailUrl = thumbnail.resize(200, 200),
                                                isActive = false,
                                                isPlaying = false,
                                                shape = RoundedCornerShape(ThumbnailCornerRadius),
                                                modifier = Modifier.size(ListThumbnailSize),
                                            )
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { navController.navigate("spotify_playlist/${playlist.id}") },
                                    )
                                }
                            } else {
                                item(key = "spotify_empty_playlists", contentType = "empty") {
                                    OnlineSearchEmptyBlock()
                                }
                            }
                        }
                    }
                }
            } else if (searchSource == OnlineSearchSource.JIOSAAVN) {
                // JIOSAAVN SEARCH RESULTS
                if (isJioSaavnLoading && jioSaavnResult == null) {
                    item(key = "jiosaavn_initial_loading", contentType = "loading") {
                        ShimmerHost {
                            repeat(8) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                } else if (jioSaavnError != null && jioSaavnResult == null) {
                    item(key = "jiosaavn_general_error", contentType = "error") {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_about),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.error_unknown),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = viewModel::retryJioSaavn,
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text(stringResource(R.string.retry_button))
                            }
                        }
                    }
                } else if (jioSaavnResult != null) {
                    val tracks = jioSaavnResult.orEmpty()
                    if (tracks.isNotEmpty()) {
                        val jioSaavnSongsKey = "${viewModel.searchRequestId}_jiosaavn_songs"
                        val shouldAnimateJioSaavnSongs = isSlideAnimationActive && !viewModel.isAnimationPlayed(jioSaavnSongsKey)

                        itemsIndexed(
                            items = tracks,
                            key = { idx, candidate -> "jiosaavn_track_${candidate.id}_$idx" },
                            contentType = { _, _ -> "jiosaavn_track" },
                        ) { idx, candidate ->
                            SearchSlideRow(
                                index = idx,
                                animationKey = jioSaavnSongsKey,
                                shouldAnimate = shouldAnimateJioSaavnSongs,
                                onAnimationTriggered = {
                                    viewModel.markAnimationPlayed(jioSaavnSongsKey)
                                },
                            ) {
                                val isTrackActive = mediaMetadata?.id == "jiosaavn_${candidate.id}"
                                val isTrackResolving = resolvingTrackId == candidate.id
                                JioSaavnTrackListItem(
                                    candidate = candidate,
                                    isActive = isTrackActive || isTrackResolving,
                                    isPlaying = isPlaying && !isTrackResolving,
                                    trailingContent = {
                                        if (isTrackResolving) {
                                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                        } else {
                                            IconButton(
                                                onClick = { onJioSaavnTrackMenuClick(candidate) },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.more_vert),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { onJioSaavnTrackClick(candidate, tracks) },
                                                onLongClick = {
                                                    haptics.longPress()
                                                    onJioSaavnTrackMenuClick(candidate)
                                                },
                                            ),
                                )
                            }
                        }
                    } else {
                        item(key = "jiosaavn_empty_songs", contentType = "empty") {
                            OnlineSearchEmptyBlock()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JioSaavnTrackListItem(
    candidate: JioSaavnCandidate,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val duration = candidate.durationSec.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) }
    val artists = candidate.artists.joinToString(", ")
    val subtitle = joinByBullet(artists, duration)

    ListItem(
        title = candidate.title,
        subtitle = subtitle,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = candidate.imageUrl?.replace("150x150", "500x500")?.resize(200, 200),
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                placeholderIconRes = R.drawable.music_note,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive,
    )
}
