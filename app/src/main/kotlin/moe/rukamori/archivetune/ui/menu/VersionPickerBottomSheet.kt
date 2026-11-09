/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.matching.SpotifyTrackScorer
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
fun VersionPickerBottomSheet(
    spotifyTrackId: String,
    targetTitle: String,
    targetArtists: List<String>,
    targetDurationMs: Long = 0L,
    targetAlbum: String? = null,
    targetThumbnail: String = "",
    initialCandidates: List<SongItem> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf(initialCandidates) }
    var isLoading by remember { mutableStateOf(initialCandidates.isEmpty()) }
    var hasOverride by remember { mutableStateOf(false) }
    var currentMatchedYtId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(spotifyTrackId) {
        val key = SpotifyTrackScorer.cacheKey(spotifyTrackId)
        val rawId = spotifyTrackId.removePrefix("spotify:track:").removePrefix("spotify:").trim()
        val match = withContext(Dispatchers.IO) {
            database.getSpotifyMatch(key)
                ?: database.getSpotifyMatch(rawId)
                ?: database.getSpotifyMatch(spotifyTrackId)
        }
        hasOverride = match?.isManualOverride == true
        currentMatchedYtId = match?.youtubeId

        if (candidates.isEmpty()) {
            isLoading = true
            val fetched = withContext(Dispatchers.IO) {
                SpotifyPlaybackResolver.fetchTopCandidates(
                    trackId = spotifyTrackId,
                    title = targetTitle,
                    artists = targetArtists,
                    durationMs = targetDurationMs,
                    album = targetAlbum,
                )
            }
            candidates = fetched
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = targetThumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.pick_another_version),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$targetTitle • ${targetArtists.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hasOverride) {
            Spacer(modifier = Modifier.height(8.dp))
            MenuSurfaceSection {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.reset_to_automatic),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.reset_to_automatic_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.sync),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        coroutineScope.launch {
                            SpotifyPlaybackResolver.resetOverride(database, spotifyTrackId)
                            val meta = SpotifyPlaybackResolver.resolveToMetadata(
                                track = moe.rukamori.archivetune.spotify.models.SpotifyTrack(
                                    id = spotifyTrackId,
                                    name = targetTitle,
                                    artists = targetArtists.map { moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist(name = it) },
                                    durationMs = targetDurationMs.toInt(),
                                    album = targetAlbum?.let { moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum(name = it) },
                                ),
                                database = database,
                            )
                            if (meta != null) {
                                playerConnection.playQueue(
                                    YouTubeQueue(
                                        endpoint = WatchEndpoint(videoId = meta.id),
                                        preloadItem = meta,
                                    ),
                                )
                            }
                            Toast.makeText(context, R.string.reset_to_automatic, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp),
                )
            }
        } else if (candidates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = true,
            ) {
                itemsIndexed(
                    items = candidates.take(8),
                    key = { idx, item -> "${item.id}_$idx" },
                ) { index, cand ->
                    val isCurrentSelection = cand.id == currentMatchedYtId
                    val durStr = cand.duration?.let { makeTimeString(it * 1000L) }
                    val artistNames = cand.artists.joinToString(", ") { it.name }
                    val metaSubtitle = if (durStr != null) "$artistNames • $durStr" else artistNames

                    ListItem(
                        headlineContent = {
                            Text(
                                text = cand.title,
                                fontWeight = if (isCurrentSelection) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrentSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = metaSubtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            AsyncImage(
                                model = cand.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                        },
                        trailingContent = {
                            if (isCurrentSelection) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(start = 4.dp),
                                ) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    SpotifyPlaybackResolver.storeOverride(
                                        database = database,
                                        spotifyTrackId = spotifyTrackId,
                                        chosen = cand,
                                        targetTrackTitle = targetTitle,
                                        targetArtist = targetArtists.joinToString(", "),
                                    )
                                    val metadata = cand.toMediaMetadata().copy(spotifyTrackId = spotifyTrackId)
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = cand.endpoint ?: WatchEndpoint(videoId = cand.id),
                                            preloadItem = metadata,
                                        ),
                                    )
                                    onDismiss()
                                }
                            },
                    )

                    if (index < candidates.take(8).lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}
