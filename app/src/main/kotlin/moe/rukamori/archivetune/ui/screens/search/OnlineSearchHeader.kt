/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.ui.component.ChipsRow
import moe.rukamori.archivetune.viewmodels.OnlineSearchSource
import moe.rukamori.archivetune.viewmodels.SpotifySearchFilter

@Composable
fun SearchFilterHeader(
    searchSource: OnlineSearchSource,
    onSourceChange: (OnlineSearchSource) -> Unit,
    searchFilter: SearchFilter?,
    onFilterChange: (SearchFilter?) -> Unit,
    spotifyFilter: SpotifySearchFilter?,
    onSpotifyFilterChange: (SpotifySearchFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top).add(WindowInsets(top = AppBarHeight)))
                .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = searchSource == OnlineSearchSource.YOUTUBE,
                    onClick = { onSourceChange(OnlineSearchSource.YOUTUBE) },
                    label = { Text(stringResource(R.string.home_provider_youtube_music)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.yt_music_icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = null,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                        ),
                )

                FilterChip(
                    selected = searchSource == OnlineSearchSource.SPOTIFY,
                    onClick = { onSourceChange(OnlineSearchSource.SPOTIFY) },
                    label = { Text(stringResource(R.string.home_provider_spotify)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.spotify_icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = null,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                        ),
                )

                FilterChip(
                    selected = searchSource == OnlineSearchSource.JIOSAAVN,
                    onClick = { onSourceChange(OnlineSearchSource.JIOSAAVN) },
                    label = { Text(stringResource(R.string.home_provider_jiosaavn)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.jiosaavn_icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    border = null,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                        ),
                )
            }

            AnimatedContent(
                targetState = searchSource,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "CategoryFilterChipsTransition",
            ) { currentSource ->
                when (currentSource) {
                    OnlineSearchSource.YOUTUBE -> {
                        ChipsRow(
                            containerColor = Color.Transparent,
                            chips =
                                listOf(
                                    null to stringResource(R.string.filter_all),
                                    FILTER_SONG to stringResource(R.string.filter_songs),
                                    FILTER_VIDEO to stringResource(R.string.filter_videos),
                                    FILTER_ALBUM to stringResource(R.string.filter_albums),
                                    FILTER_ARTIST to stringResource(R.string.filter_artists),
                                    FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                                    FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
                                ),
                            currentValue = searchFilter,
                            onValueUpdate = onFilterChange,
                            icons =
                                mapOf(
                                    null to R.drawable.ic_search,
                                    FILTER_SONG to R.drawable.music_note,
                                    FILTER_VIDEO to R.drawable.slow_motion_video,
                                    FILTER_ALBUM to R.drawable.album,
                                    FILTER_ARTIST to R.drawable.person,
                                    FILTER_COMMUNITY_PLAYLIST to R.drawable.queue_music,
                                    FILTER_FEATURED_PLAYLIST to R.drawable.playlist_play,
                                ),
                        )
                    }

                    OnlineSearchSource.SPOTIFY -> {
                        ChipsRow(
                            containerColor = Color.Transparent,
                            chips =
                                listOf(
                                    null to stringResource(R.string.filter_all),
                                    SpotifySearchFilter.SONGS to stringResource(R.string.filter_songs),
                                    SpotifySearchFilter.ALBUMS to stringResource(R.string.filter_albums),
                                    SpotifySearchFilter.ARTISTS to stringResource(R.string.filter_artists),
                                    SpotifySearchFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                                ),
                            currentValue = spotifyFilter,
                            onValueUpdate = onSpotifyFilterChange,
                            icons =
                                mapOf(
                                    null to R.drawable.ic_search,
                                    SpotifySearchFilter.SONGS to R.drawable.music_note,
                                    SpotifySearchFilter.ALBUMS to R.drawable.album,
                                    SpotifySearchFilter.ARTISTS to R.drawable.person,
                                    SpotifySearchFilter.PLAYLISTS to R.drawable.playlist_play,
                                ),
                        )
                    }

                    OnlineSearchSource.JIOSAAVN -> {
                        ChipsRow(
                            containerColor = Color.Transparent,
                            chips =
                                listOf(
                                    null to stringResource(R.string.filter_songs),
                                ),
                            currentValue = null,
                            onValueUpdate = {},
                            icons =
                                mapOf(
                                    null to R.drawable.music_note,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchFilterHeader(
    searchFilter: SearchFilter?,
    onFilterChange: (SearchFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchFilterHeader(
        searchSource = OnlineSearchSource.YOUTUBE,
        onSourceChange = {},
        searchFilter = searchFilter,
        onFilterChange = onFilterChange,
        spotifyFilter = null,
        onSpotifyFilterChange = {},
        modifier = modifier,
    )
}

@Composable
fun OnlineSearchHeader(
    searchFilter: SearchFilter?,
    onFilterChange: (SearchFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchFilterHeader(
        searchFilter = searchFilter,
        onFilterChange = onFilterChange,
        modifier = modifier,
    )
}
