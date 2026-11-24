/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.AnimatedEqualizer
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.haptics.rememberOzoneHaptics
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.theme.ozoneClickable

private sealed interface ContinueTileModel {
    val id: String
    val title: String

    data object Shuffle : ContinueTileModel {
        override val id: String = "continue_shuffle"
        override val title: String = "Shuffle"
    }

    data class Item(
        val item: LocalItem,
        val artworkUrl: String?,
        override val title: String,
        override val id: String,
    ) : ContinueTileModel
}

/**
 * Compact 2-column "Continue" block right under the Hero.
 * Displays up to 6 tiles (56dp artwork + 1-line title, 12dp radius, one-tap play).
 * Replaces the separate Speed Dial and Keep Listening shelves.
 */
@Composable
internal fun HomeContinueBlock(
    speedDialItems: List<LocalItem>,
    keepListening: List<LocalItem>,
    quickPicks: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberOzoneHaptics()

    val tiles = remember(speedDialItems, keepListening) {
        val list = mutableListOf<ContinueTileModel>()
        // 1. Shuffle tile with shuffle icon
        list.add(ContinueTileModel.Shuffle)

        // 2. Up to 5 items from speedDialItems and keepListening
        val seenIds = mutableSetOf<String>()
        val combined = (speedDialItems + keepListening)
        for (item in combined) {
            if (list.size >= 6) break
            val (id, title, artUrl) = when (item) {
                is Song -> Triple("song_${item.id}", item.song.title, item.song.thumbnailUrl)
                is Album -> Triple("album_${item.id}", item.album.title, item.album.thumbnailUrl)
                is Artist -> Triple("artist_${item.id}", item.artist.name, item.artist.thumbnailUrl)
                is Playlist -> Triple("playlist_${item.id}", item.playlist.name, item.playlist.thumbnailUrl)
                else -> Triple("item_${item.id}", item.title, item.thumbnailUrl)
            }
            if (seenIds.add(id)) {
                list.add(ContinueTileModel.Item(item = item, artworkUrl = artUrl, title = title, id = id))
            }
        }
        list.toList()
    }

    if (tiles.size <= 1) return

    val chunkedRows = remember(tiles) { tiles.chunked(2) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HomeLayoutConfig.sidePadding),
    ) {
        chunkedRows.forEach { rowTiles ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowTiles.forEach { tile ->
                    ContinueTile(
                        tile = tile,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        onShuffle = {
                            haptics.click()
                            val allSongs = (speedDialItems + keepListening).filterIsInstance<Song>()
                            if (allSongs.isNotEmpty()) {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.shuffle),
                                        items = allSongs.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                            } else if (quickPicks.isNotEmpty()) {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.shuffle),
                                        items = quickPicks.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                            }
                        },
                        onPlayItem = { localItem ->
                            haptics.click()
                            when (localItem) {
                                is Song -> {
                                    if (localItem.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            if (localItem.song.isLocal) {
                                                ListQueue(items = listOf(localItem.toMediaItem()))
                                            } else {
                                                YouTubeQueue.radio(localItem.toMediaMetadata())
                                            },
                                        )
                                    }
                                }
                                is Album -> navController.navigate("album/${localItem.id}")
                                is Artist -> navController.navigate("artist/${localItem.id}")
                                is Playlist -> navController.navigate("local_playlist/${localItem.id}")
                                else -> {}
                            }
                        },
                        onItemLongClick = { localItem ->
                            haptics.longPress()
                            menuState.show {
                                when (localItem) {
                                    is Song -> SongMenu(originalSong = localItem, navController = navController, onDismiss = menuState::dismiss)
                                    is Album -> AlbumMenu(originalAlbum = localItem, navController = navController, onDismiss = menuState::dismiss)
                                    is Artist -> ArtistMenu(originalArtist = localItem, coroutineScope = scope, onDismiss = menuState::dismiss)
                                    is Playlist -> PlaylistMenu(playlist = localItem, coroutineScope = scope, onDismiss = menuState::dismiss)
                                    else -> {}
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueTile(
    tile: ContinueTileModel,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    onShuffle: () -> Unit,
    onPlayItem: (LocalItem) -> Unit,
    onItemLongClick: (LocalItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tileRadius = 12.dp

    when (tile) {
        is ContinueTileModel.Shuffle -> {
            Surface(
                shape = RoundedCornerShape(tileRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                modifier = modifier
                    .height(56.dp)
                    .ozoneClickable(pressedScale = 0.96f, onClick = onShuffle),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(topStart = tileRadius, bottomStart = tileRadius))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    ),
                                ),
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = stringResource(R.string.shuffle),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = stringResource(R.string.shuffle),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                }
            }
        }

        is ContinueTileModel.Item -> {
            val isActive = when (val item = tile.item) {
                is Song -> item.id == mediaMetadata?.id
                is Album -> item.id == mediaMetadata?.album?.id
                else -> false
            }

            Surface(
                shape = RoundedCornerShape(tileRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                modifier = modifier
                    .height(56.dp)
                    .then(
                        if (isActive) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(tileRadius))
                        } else {
                            Modifier
                        },
                    )
                    .combinedClickable(
                        onClick = { onPlayItem(tile.item) },
                        onLongClick = { onItemLongClick(tile.item) },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(topStart = tileRadius, bottomStart = tileRadius))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(tile.artworkUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                    )

                    if (isActive && isPlaying) {
                        AnimatedEqualizer(
                            isPlaying = isPlaying,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
