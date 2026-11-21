/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.trending

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import moe.rukamori.archivetune.innertube.models.SongItem

enum class TrendingSource {
    YOUTUBE,
    JIOSAAVN,
    APPLE,
}

@Immutable
@Serializable
data class TrendingTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSeconds: Int? = null,
    val isApple: Boolean = false,
    val isJioSaavn: Boolean = false,
    val explicit: Boolean = false,
    val encryptedMediaUrl: String? = null,
    @Transient
    val youtubeSongItem: SongItem? = null,
)

@Immutable
data class TrendingUiState(
    val selectedSource: TrendingSource = TrendingSource.YOUTUBE,
    val youtubeTracks: List<TrendingTrack> = emptyList(),
    val jioSaavnTracks: List<TrendingTrack> = emptyList(),
    val appleTracks: List<TrendingTrack> = emptyList(),
    val heroTracks: List<TrendingTrack> = emptyList(),
    val isJioSaavnAvailable: Boolean = false,
    val isAppleAvailable: Boolean = false,
    val regionCode: String = "",
    val regionDisplayName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val currentTracks: List<TrendingTrack>
        get() =
            when (selectedSource) {
                TrendingSource.YOUTUBE -> youtubeTracks
                TrendingSource.JIOSAAVN -> jioSaavnTracks
                TrendingSource.APPLE -> appleTracks
            }

    val topHeroTrack: TrendingTrack?
        get() = heroTracks.firstOrNull() ?: currentTracks.firstOrNull()

    val isVisible: Boolean
        get() = currentTracks.isNotEmpty() ||
            heroTracks.isNotEmpty() ||
            (selectedSource == TrendingSource.JIOSAAVN && isJioSaavnAvailable && jioSaavnTracks.isNotEmpty()) ||
            (selectedSource == TrendingSource.APPLE && isAppleAvailable && appleTracks.isNotEmpty())
}
