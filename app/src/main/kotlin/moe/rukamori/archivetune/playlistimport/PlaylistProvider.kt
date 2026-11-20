/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistimport

enum class PlaylistServiceType {
    SPOTIFY,
    YOUTUBE_MUSIC,
    YOUTUBE,
    UNSUPPORTED;

    val displayName: String
        get() = when (this) {
            SPOTIFY -> "Spotify"
            YOUTUBE_MUSIC -> "YouTube Music"
            YOUTUBE -> "YouTube"
            UNSUPPORTED -> "Unsupported"
        }
}

data class ImportedTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val originalId: String? = null,
)

data class ImportedPlaylist(
    val serviceType: PlaylistServiceType,
    val id: String,
    val name: String,
    val coverUrl: String?,
    val tracks: List<ImportedTrack>,
    val rawBrowseId: String? = null,
    val rawSpotifyId: String? = null,
)

interface PlaylistProvider {
    val serviceType: PlaylistServiceType
    fun canHandle(url: String): Boolean
    suspend fun fetchPlaylist(url: String): Result<ImportedPlaylist>
}
