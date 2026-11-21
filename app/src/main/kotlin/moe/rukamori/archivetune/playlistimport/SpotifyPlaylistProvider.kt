/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyAuth
import moe.rukamori.archivetune.spotify.SpotifyPlaylistApi
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

class SpotifyPlaylistProvider : PlaylistProvider {
    override val serviceType: PlaylistServiceType
        get() = PlaylistServiceType.SPOTIFY

    override fun canHandle(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        return extractPlaylistId(trimmed) != null
    }

    override suspend fun fetchPlaylist(url: String): Result<ImportedPlaylist> =
        withContext(Dispatchers.IO) {
            val playlistId = extractPlaylistId(url)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist link"))

            runCatching {
                // Ensure a valid token (existing session or guest token)
                SpotifyAuth.ensureToken().getOrThrow()

                val playlist: SpotifyPlaylist = SpotifyPlaylistApi.playlist(playlistId).getOrThrow()
                val tracksResult = SpotifyPlaylistApi.playlistTracks(playlistId, limit = 100, offset = 0)
                val tracks: List<SpotifyPlaylistTrack> = tracksResult.getOrNull()?.items.orEmpty()

                val importedTracks = tracks.mapNotNull { item: SpotifyPlaylistTrack ->
                    val track: SpotifyTrack = item.track ?: return@mapNotNull null
                    ImportedTrack(
                        title = track.name,
                        artist = track.artists.joinToString(", ") { it.name },
                        album = track.album?.name,
                        durationSeconds = (track.durationMs / 1000).takeIf { it > 0 },
                        thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                        originalId = track.id,
                    )
                }

                ImportedPlaylist(
                    serviceType = PlaylistServiceType.SPOTIFY,
                    id = playlistId,
                    name = playlist.name.ifBlank { "Spotify Playlist" },
                    coverUrl = playlist.images.firstOrNull()?.url,
                    tracks = importedTracks,
                    rawSpotifyId = playlistId,
                )
            }.recoverCatching { throwable ->
                val message = throwable.message.orEmpty()
                if (message.contains("404") || message.contains("not found", ignoreCase = true)) {
                    throw IllegalStateException("Playlist not found or is private.")
                }
                throw throwable
            }
        }

    fun extractPlaylistId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // 1. Match spotify:playlist:ID format
        val uriRegex = Regex("""^spotify:playlist:([a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE)
        val uriMatch = uriRegex.find(trimmed)
        if (uriMatch != null) {
            return uriMatch.groupValues[1]
        }

        // 2. Match open.spotify.com/playlist/ID URL
        val urlRegex = Regex("""open\.spotify\.com/playlist/([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val urlMatch = urlRegex.find(trimmed)
        if (urlMatch != null) {
            return urlMatch.groupValues[1]
        }

        // 3. Raw 22-character alphanumeric Spotify ID
        val rawIdRegex = Regex("""^[a-zA-Z0-9]{22}$""")
        if (rawIdRegex.matches(trimmed) && trimmed.contains(Regex("[0-9]")) && trimmed.contains(Regex("[a-zA-Z]"))) {
            return trimmed
        }

        return null
    }
}
