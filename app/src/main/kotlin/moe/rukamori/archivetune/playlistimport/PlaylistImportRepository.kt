/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.models.MediaMetadata
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistImportRepository @Inject constructor(
    private val database: MusicDatabase,
) {
    suspend fun findExistingPlaylist(imported: ImportedPlaylist): Playlist? =
        withContext(Dispatchers.IO) {
            when (imported.serviceType) {
                PlaylistServiceType.SPOTIFY -> {
                    database.playlistBySpotifyId(imported.id).firstOrNull()
                }

                PlaylistServiceType.YOUTUBE, PlaylistServiceType.YOUTUBE_MUSIC -> {
                    val browseId = imported.rawBrowseId ?: imported.id
                    val cleanId = imported.id.removePrefix("VL")
                    database.playlistByBrowseId(browseId).firstOrNull()
                        ?: database.playlistByBrowseId("VL$cleanId").firstOrNull()
                        ?: database.playlistByBrowseId(cleanId).firstOrNull()
                }

                PlaylistServiceType.UNSUPPORTED -> null
            }
        }

    suspend fun importPlaylist(imported: ImportedPlaylist): Result<PlaylistEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = LocalDateTime.now()
                val isSpotify = imported.serviceType == PlaylistServiceType.SPOTIFY

                val playlistEntity = PlaylistEntity(
                    name = imported.name,
                    browseId = if (isSpotify) null else (imported.rawBrowseId ?: "VL${imported.id.removePrefix("VL")}"),
                    spotifyId = if (isSpotify) imported.id else null,
                    thumbnailUrl = imported.coverUrl,
                    remoteSongCount = imported.tracks.size,
                    isEditable = false,
                    bookmarkedAt = now,
                    lastUpdateTime = now,
                )

                database.query {
                    insert(playlistEntity)
                }

                val savedPlaylist = database.playlist(playlistEntity.id).firstOrNull()
                    ?: Playlist(playlist = playlistEntity, songCount = 0, songThumbnails = emptyList())

                if (imported.tracks.isNotEmpty()) {
                    database.transaction {
                        imported.tracks.forEachIndexed { index, track ->
                            val songId = track.originalId?.takeIf { it.isNotBlank() } ?: "IMPORT_${UUID.randomUUID()}"
                            val mediaMetadata = MediaMetadata(
                                id = songId,
                                title = track.title,
                                artists = listOf(MediaMetadata.Artist(id = null, name = track.artist)),
                                duration = track.durationSeconds ?: 0,
                                thumbnailUrl = track.thumbnailUrl,
                                album = track.album?.takeIf { it.isNotBlank() }?.let { albumName ->
                                    MediaMetadata.Album(id = albumName, title = albumName)
                                },
                                spotifyTrackId = if (isSpotify) track.originalId else null,
                            )

                            try {
                                insert(mediaMetadata)
                            } catch (e: Exception) {
                                Timber.w(e, "Could not insert track metadata for ${track.title}")
                            }

                            insert(
                                PlaylistSongMap(
                                    playlistId = playlistEntity.id,
                                    songId = songId,
                                    position = index,
                                ),
                            )
                        }
                    }
                }

                playlistEntity
            }
        }
}
