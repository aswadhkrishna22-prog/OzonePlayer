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

enum class TrendingCategory(val id: String, val title: String, val searchKeyword: String) {
    FOR_YOU("for_you", "For You", ""),
    MALAYALAM("malayalam", "Malayalam", "malayalam"),
    TAMIL("tamil", "Tamil", "tamil"),
    HINDI("hindi", "Hindi", "hindi"),
    ENGLISH("english", "English", "english"),
    GLOBAL("global", "Top Charts", ""),
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
    val selectedCategory: TrendingCategory = TrendingCategory.FOR_YOU,
    val detectedLanguage: String? = null,
    val youtubeTracks: List<TrendingTrack> = emptyList(),
    val jioSaavnTracks: List<TrendingTrack> = emptyList(),
    val appleTracks: List<TrendingTrack> = emptyList(),
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

    val isVisible: Boolean
        get() = currentTracks.isNotEmpty() ||
            (selectedSource == TrendingSource.JIOSAAVN && isJioSaavnAvailable && jioSaavnTracks.isNotEmpty()) ||
            (selectedSource == TrendingSource.APPLE && isAppleAvailable && appleTracks.isNotEmpty())
}
