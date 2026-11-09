/*
 * YumaPlayer (2026) | Modified work by MuwMix
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
    val explicit: Boolean = false,
    @Transient
    val youtubeSongItem: SongItem? = null,
)

@Immutable
data class TrendingUiState(
    val selectedSource: TrendingSource = TrendingSource.YOUTUBE,
    val youtubeTracks: List<TrendingTrack> = emptyList(),
    val appleTracks: List<TrendingTrack> = emptyList(),
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
                TrendingSource.APPLE -> appleTracks
            }

    val isVisible: Boolean
        get() = currentTracks.isNotEmpty() || (selectedSource == TrendingSource.APPLE && isAppleAvailable && appleTracks.isNotEmpty())
}
