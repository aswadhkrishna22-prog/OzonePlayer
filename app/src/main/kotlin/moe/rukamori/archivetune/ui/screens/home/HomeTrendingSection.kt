/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.GridThumbnailCornerRadius
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.trending.TrendingRepository
import moe.rukamori.archivetune.home.trending.TrendingResolutionResult
import moe.rukamori.archivetune.home.trending.TrendingSource
import moe.rukamori.archivetune.home.trending.TrendingTrack
import moe.rukamori.archivetune.home.trending.TrendingUiState
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.GridItem
import moe.rukamori.archivetune.ui.component.ItemExplicitBadge
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.screens.HomeSectionHeader
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

internal fun LazyListScope.homeTrendingSection(
    trendingState: TrendingUiState?,
    mediaContext: HomeFeedMediaContext,
    trendingRepository: TrendingRepository?,
) {
    val state = trendingState?.takeIf { it.isVisible || it.isLoading } ?: return
    val tracks = state.currentTracks

    // If not loading and no tracks to show, hide completely
    if (tracks.isEmpty() && !state.isLoading) return

    val headerTitle =
        if (state.regionDisplayName.isNotBlank()) {
            mediaContext.navController.context.getString(R.string.trending_in, state.regionDisplayName)
        } else {
            mediaContext.navController.context.getString(R.string.trending)
        }

    sectionSpacer("trending")

    item(
        key = "home_trending_header",
        contentType = "section_header",
    ) {
        HomeSectionHeader(
            title = headerTitle,
            onClick = {
                mediaContext.navController.navigate("charts_screen")
            },
            modifier = Modifier.animateItem(),
        )
    }

    item(
        key = "home_trending_chips",
        contentType = "trending_chips",
    ) {
        TrendingSourceChipsRow(
            selectedSource = state.selectedSource,
            isAppleAvailable = state.isAppleAvailable,
            onSourceSelected = { source ->
                mediaContext.onAction(HomeAction.SelectTrendingSource(source))
            },
            modifier = Modifier.animateItem(),
        )
    }

    item(
        key = "home_trending_shelf",
        contentType = "media_shelf",
    ) {
        TrendingShelfContent(
            tracks = tracks,
            isLoading = state.isLoading && tracks.isEmpty(),
            sectionTitle = headerTitle,
            selectedSource = state.selectedSource,
            mediaContext = mediaContext,
            trendingRepository = trendingRepository,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun TrendingSourceChipsRow(
    selectedSource: TrendingSource,
    isAppleAvailable: Boolean,
    onSourceSelected: (TrendingSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            TrendingChip(
                text = stringResource(R.string.youtube_music),
                isSelected = selectedSource == TrendingSource.YOUTUBE,
                onClick = { onSourceSelected(TrendingSource.YOUTUBE) },
            )

            if (isAppleAvailable) {
                TrendingChip(
                    text = stringResource(R.string.apple_music_top_songs),
                    isSelected = selectedSource == TrendingSource.APPLE,
                    onClick = { onSourceSelected(TrendingSource.APPLE) },
                )
            }
        }

        if (selectedSource == TrendingSource.APPLE) {
            Text(
                text = stringResource(R.string.source_apple_music),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun TrendingChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalYumaColors.current
    val bgColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            colors.glassBackground
        }
    val textColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            colors.textPrimary
        }

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier =
            Modifier
                .yumaGlassCard(
                    shape = CircleShape,
                    backgroundColor = bgColor,
                    borderColor = if (isSelected) Color.Transparent else colors.glassBorder,
                )
                .yumaClickable(pressedScale = 0.94f, onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrendingShelfContent(
    tracks: List<TrendingTrack>,
    isLoading: Boolean,
    sectionTitle: String,
    selectedSource: TrendingSource,
    mediaContext: HomeFeedMediaContext,
    trendingRepository: TrendingRepository?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadingTrackId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = HomeLayoutConfig.cardWidth(maxWidth)
        val horizontalInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        val startPadding = horizontalInsets.calculateStartPadding(layoutDirection) + HomeLayoutConfig.shelfHorizontalPadding
        val endPadding = horizontalInsets.calculateEndPadding(layoutDirection) + HomeLayoutConfig.shelfHorizontalPadding

        if (isLoading) {
            LazyRow(
                contentPadding = PaddingValues(start = startPadding, end = endPadding),
                horizontalArrangement = Arrangement.spacedBy(HomeLayoutConfig.shelfSpacing),
            ) {
                items(6) {
                    GridItemPlaceHolder(fillMaxWidth = false)
                }
            }
            return@BoxWithConstraints
        }

        LazyRow(
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(HomeLayoutConfig.shelfSpacing),
        ) {
            itemsIndexed(
                items = tracks,
                key = { index, track -> "${track.id}_$index" },
            ) { index, track ->
                val isCurrentMedia = mediaContext.mediaMetadata?.id == track.id
                val isResolvingThis = loadingTrackId == track.id

                GridItem(
                    title = {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().basicMarquee(),
                        )
                    },
                    subtitle = {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().basicMarquee(),
                        )
                    },
                    badges = {
                        // Rank Badge #1 .. #15
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }

                        if (track.explicit) {
                            ItemExplicitBadge()
                        }
                    },
                    thumbnailContent = {
                        ItemThumbnail(
                            thumbnailUrl = track.artworkUrl,
                            isActive = isCurrentMedia,
                            isPlaying = mediaContext.isPlaying,
                            shape = RoundedCornerShape(GridThumbnailCornerRadius),
                            thumbnailRatio = 1f,
                        )

                        // Loading overlay if resolving Apple song
                        if (isResolvingThis) {
                            Surface(
                                shape = RoundedCornerShape(GridThumbnailCornerRadius),
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier.matchParentSize(),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.matchParentSize(),
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    },
                    thumbnailRatio = 1f,
                    thumbnailHeight = cardWidth,
                    modifier =
                        Modifier
                            .focusable()
                            .combinedClickable(
                                onClick = {
                                    if (track.isApple) {
                                        // Apple track resolution
                                        if (isResolvingThis || trendingRepository == null) return@combinedClickable
                                        loadingTrackId = track.id
                                        scope.launch {
                                            try {
                                                when (val res = trendingRepository.resolver.resolveAppleTrack(track)) {
                                                    is TrendingResolutionResult.Success -> {
                                                        val song = res.songItem
                                                        mediaContext.playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                                preloadItem = song.toMediaMetadata().copy(spotifyTrackId = track.id),
                                                            ),
                                                        )
                                                    }

                                                    is TrendingResolutionResult.NotFound -> {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.could_not_find_song_try_searching),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        val query = "${track.title} ${track.artist}".trim()
                                                        mediaContext.navController.navigate(onlineSearchResultRoute(query))
                                                    }
                                                }
                                            } finally {
                                                loadingTrackId = null
                                            }
                                        }
                                    } else {
                                        // YouTube track: play chart as queue
                                        val mediaItems =
                                            tracks.map { item ->
                                                item.youtubeSongItem?.toMediaItem()
                                                    ?: item.toMediaItem()
                                            }
                                        mediaContext.playerConnection.playQueue(
                                            ListQueue(
                                                title = sectionTitle,
                                                items = mediaItems,
                                                startIndex = index,
                                            ),
                                        )
                                    }
                                },
                            ),
                )
            }
        }
    }
}

private fun TrendingTrack.toMediaItem(): MediaItem =
    youtubeSongItem?.toMediaItem()
        ?: MediaMetadata(
            id = id,
            title = title,
            artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
            duration = durationSeconds ?: -1,
            thumbnailUrl = artworkUrl,
            explicit = explicit,
        ).toMediaItem()
