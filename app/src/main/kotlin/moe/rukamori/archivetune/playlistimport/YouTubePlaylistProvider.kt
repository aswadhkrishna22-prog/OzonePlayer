/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistimport

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.PlaylistPage

class YouTubePlaylistProvider : PlaylistProvider {
    override val serviceType: PlaylistServiceType
        get() = PlaylistServiceType.YOUTUBE

    override fun canHandle(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        return extractPlaylistId(trimmed) != null
    }

    fun detectSpecificService(url: String): PlaylistServiceType {
        val trimmed = url.trim()
        return when {
            trimmed.contains("music.youtube.com", ignoreCase = true) -> PlaylistServiceType.YOUTUBE_MUSIC
            canHandle(trimmed) -> PlaylistServiceType.YOUTUBE
            else -> PlaylistServiceType.UNSUPPORTED
        }
    }

    override suspend fun fetchPlaylist(url: String): Result<ImportedPlaylist> =
        withContext(Dispatchers.IO) {
            val playlistId = extractPlaylistId(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube playlist link"))

            val specificType = detectSpecificService(url)
            val cleanBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"

            runCatching {
                val page: PlaylistPage = YouTube.playlist(cleanBrowseId).getOrThrow()
                val playlistItem = page.playlist

                val importedTracks = page.songs.map { song: SongItem ->
                    ImportedTrack(
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        album = song.album?.name,
                        durationSeconds = song.duration,
                        thumbnailUrl = song.thumbnail,
                        originalId = song.id,
                    )
                }

                ImportedPlaylist(
                    serviceType = specificType,
                    id = playlistId,
                    name = playlistItem.title,
                    coverUrl = playlistItem.thumbnail,
                    tracks = importedTracks,
                    rawBrowseId = cleanBrowseId,
                )
            }.recoverCatching { throwable ->
                val message = throwable.message.orEmpty()
                if (message.contains("PLAYLIST_PRIVATE", ignoreCase = true) ||
                    message.contains("private", ignoreCase = true)
                ) {
                    throw IllegalStateException("This playlist is private or cannot be accessed.")
                }
                throw throwable
            }
        }

    fun extractPlaylistId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Try parsing as URI / URL
        val uri = runCatching {
            if (!trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                Uri.parse("https://$trimmed")
            } else {
                Uri.parse(trimmed)
            }
        }.getOrNull()

        if (uri != null) {
            val host = uri.host.orEmpty().lowercase()
            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                val listParam = uri.getQueryParameter("list")
                if (!listParam.isNullOrBlank()) {
                    return listParam.trim()
                }
            }
        }

        // Direct regex pattern for list parameter: list=([a-zA-Z0-9_-]+)
        val listRegex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
        val listMatch = listRegex.find(trimmed)
        if (listMatch != null) {
            return listMatch.groupValues[1]
        }

        // Raw IDs starting with PL, VL, OLAK5uy_, RD
        val rawIdRegex = Regex("""^(?:VL)?(PL[a-zA-Z0-9_-]+|OLAK5uy_[a-zA-Z0-9_-]+|RD[a-zA-Z0-9_-]+)$""")
        val rawMatch = rawIdRegex.find(trimmed)
        if (rawMatch != null) {
            return rawMatch.groupValues[1]
        }

        return null
    }
}
