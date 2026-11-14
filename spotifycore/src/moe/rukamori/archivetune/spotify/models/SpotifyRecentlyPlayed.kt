/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyRecentlyPlayed(
    val items: List<SpotifyRecentlyPlayedItem> = emptyList(),
    val next: String? = null,
)

@Serializable
data class SpotifyRecentlyPlayedItem(
    val track: SpotifyTrack,
    @SerialName("played_at") val playedAt: String? = null,
)
