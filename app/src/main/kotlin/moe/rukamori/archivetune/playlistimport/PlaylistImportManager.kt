/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistimport

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistImportManager @Inject constructor() {
    private val spotifyProvider = SpotifyPlaylistProvider()
    private val youTubeProvider = YouTubePlaylistProvider()

    // Readily extensible provider list (e.g. Apple Music, Deezer later)
    private val providers: List<PlaylistProvider> = listOf(
        spotifyProvider,
        youTubeProvider,
    )

    fun detectService(url: String): PlaylistServiceType {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return PlaylistServiceType.UNSUPPORTED

        if (spotifyProvider.canHandle(trimmed)) {
            return PlaylistServiceType.SPOTIFY
        }

        if (youTubeProvider.canHandle(trimmed)) {
            return youTubeProvider.detectSpecificService(trimmed)
        }

        return PlaylistServiceType.UNSUPPORTED
    }

    fun isSupportedUrl(url: String): Boolean =
        detectService(url) != PlaylistServiceType.UNSUPPORTED

    suspend fun fetchPlaylist(url: String): Result<ImportedPlaylist> {
        val trimmed = url.trim()
        val provider = providers.firstOrNull { it.canHandle(trimmed) }
            ?: return Result.failure(IllegalArgumentException("Unsupported playlist link"))

        return provider.fetchPlaylist(trimmed)
    }
}
