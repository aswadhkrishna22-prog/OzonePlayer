/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.trending

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.spotify.matching.TrackMatcher

sealed interface TrendingResolutionResult {
    data class Success(val songItem: SongItem) : TrendingResolutionResult
    data object NotFound : TrendingResolutionResult
}

class TrendingResolver(
    private val matchCache: TrendingMatchCache,
) {
    private val matcher = TrackMatcher()

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 8000L
        const val MIN_MATCH_SCORE = 0.75
    }

    suspend fun resolveAppleTrack(track: TrendingTrack): TrendingResolutionResult =
        withContext(Dispatchers.IO) {
            val cached = matchCache.get(track.artist, track.title)
            when (cached) {
                is MatchCacheResult.Hit -> {
                    // Try to resolve the song metadata from YouTube if needed, or create song item
                    val songItem = SongItem(
                        id = cached.videoId,
                        title = track.title,
                        artists = listOf(moe.rukamori.archivetune.innertube.models.Artist(name = track.artist, id = null)),
                        thumbnail = track.artworkUrl ?: "",
                        explicit = track.explicit,
                        endpoint = moe.rukamori.archivetune.innertube.models.WatchEndpoint(videoId = cached.videoId),
                    )
                    return@withContext TrendingResolutionResult.Success(songItem)
                }

                is MatchCacheResult.Negative -> {
                    return@withContext TrendingResolutionResult.NotFound
                }

                is MatchCacheResult.Miss -> {
                    // Proceed to network search
                }
            }

            val result =
                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                    runCatching {
                        val query = "${track.title} ${track.artist}"
                        val searchResult =
                            YouTube.search(
                                query = query,
                                filter = YouTube.SearchFilter.FILTER_SONG,
                            ).getOrNull()

                        val candidates =
                            searchResult?.items
                                ?.filterIsInstance<SongItem>()
                                ?.distinctBy { it.id }
                                .orEmpty()

                        if (candidates.isEmpty()) return@runCatching null

                        val normTargetTitle = matcher.canonicalTitle(track.title)
                        val normTargetArtist = matcher.canonicalArtist(track.artist)

                        val scored =
                            candidates.mapNotNull { candidate ->
                                val candTitle = matcher.canonicalTitle(candidate.title)
                                val candArtist = matcher.canonicalArtist(candidate.artists.joinToString(", ") { it.name })

                                val titleSim = matcher.jaroWinklerSimilarity(normTargetTitle, candTitle)
                                val artistSim = matcher.jaroWinklerSimilarity(normTargetArtist, candArtist)

                                val artistMatch =
                                    artistSim >= 0.70 ||
                                        candArtist.contains(normTargetArtist) ||
                                        normTargetArtist.contains(candArtist) ||
                                        candidate.artists.any { a ->
                                            val normA = matcher.canonicalArtist(a.name)
                                            normA.isNotEmpty() && (normTargetArtist.contains(normA) || normA.contains(normTargetArtist) || matcher.jaroWinklerSimilarity(normTargetArtist, normA) >= 0.75)
                                        }

                                if (titleSim >= 0.75 && artistMatch) {
                                    val score = titleSim * 0.6 + (if (artistSim >= 0.80) artistSim else 0.80) * 0.4
                                    Pair(candidate, score)
                                } else {
                                    null
                                }
                            }

                        scored.maxByOrNull { it.second }?.takeIf { it.second >= MIN_MATCH_SCORE }?.first
                    }.getOrNull()
                }

            if (result != null) {
                matchCache.putPositive(track.artist, track.title, result.id)
                TrendingResolutionResult.Success(result)
            } else {
                matchCache.putNegative(track.artist, track.title)
                TrendingResolutionResult.NotFound
            }
        }
}
