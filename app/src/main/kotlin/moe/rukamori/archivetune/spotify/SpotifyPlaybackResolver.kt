/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.mapping.SpotifyEntityMapper
import moe.rukamori.archivetune.spotify.mapping.SpotifyQueryBuilder
import moe.rukamori.archivetune.spotify.matching.SpotifySearchScorer
import moe.rukamori.archivetune.spotify.matching.TrackMatcher
import moe.rukamori.archivetune.spotify.matching.TrackQuery
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber

object SpotifyPlaybackResolver {
    private const val CACHE_MAX_SIZE = 512

    private val matcher = TrackMatcher()
    private val scorer = SpotifySearchScorer(matcher)
    private val searchSemaphore = kotlinx.coroutines.sync.Semaphore(3)

    private val UNWANTED_TOKENS = listOf(
        "karaoke", "tribute", "instrumental", "backing track", "cover", "parody",
        "reaction", "slowed", "sped up", "nightcore", "8d audio", "ringtone", "bass boosted"
    )

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<String, MediaMetadata>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean = size > CACHE_MAX_SIZE
        }

    @Volatile
    private var databaseRef: MusicDatabase? = null

    suspend fun resolveToMediaItem(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
    ): MediaItem? = resolveToMetadata(track, database)?.toMediaItem()

    suspend fun resolveToMetadata(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
    ): MediaMetadata? =
        withContext(Dispatchers.IO) {
            val db = database ?: databaseRef
            if (database != null && databaseRef == null) {
                databaseRef = database
            }

            val rawSpotifyId = track.id.removePrefix("spotify:track:").removePrefix("spotify:")

            mutex.withLock {
                (cache[rawSpotifyId] ?: cache[track.id])?.let { cached ->
                    Timber.tag("SpotifyMatching").d("CACHE HIT: '${track.name}' -> YT ${cached.id}")
                    return@withContext cached
                }
            }

            if (db != null) {
                val match =
                    if (rawSpotifyId.isNotBlank()) {
                        db.getSpotifyMatch(rawSpotifyId) ?: db.getSpotifyMatch(track.id)
                    } else {
                        db.getSpotifyMatch(track.id)
                    }
                if (match != null) {
                    val dbSong = db.getSongByIdBlocking(match.youtubeId)
                    val metadata =
                        if (dbSong != null) {
                            dbSong.toMediaMetadata().copy(
                                thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: dbSong.song.thumbnailUrl,
                                duration = if (track.durationMs > 0) track.durationMs / 1000 else dbSong.song.duration,
                                album =
                                    track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                                        ?: dbSong.album?.let { MediaMetadata.Album(id = it.id, title = it.title) },
                                explicit = track.explicit || dbSong.song.explicit,
                                spotifyTrackId = track.id.takeIf(String::isNotBlank),
                                isrc = track.externalIds?.isrc?.takeIf { it.isNotBlank() } ?: match.isrc ?: dbSong.song.isrc,
                            )
                        } else {
                            MediaMetadata(
                                id = match.youtubeId,
                                title = track.name,
                                artists = track.artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
                                duration = if (track.durationMs > 0) track.durationMs / 1000 else -1,
                                thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track),
                                album = track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) },
                                explicit = track.explicit,
                                spotifyTrackId = track.id.takeIf(String::isNotBlank),
                                isrc = track.externalIds?.isrc?.takeIf { it.isNotBlank() } ?: match.isrc,
                            )
                        }

                    mutex.withLock {
                        cache[track.id] = metadata
                        if (rawSpotifyId.isNotBlank()) {
                            cache[rawSpotifyId] = metadata
                        }
                    }
                    Timber.tag("SpotifyMatching").d("DB MATCH HIT: '${track.name}' -> YT ${metadata.id}")
                    return@withContext metadata
                }
            }

            val artistsList = track.artists.map { it.name }.filter { it.isNotBlank() }
            val queryArtist = artistsList.joinToString(", ")
            Timber.tag("SpotifyMatching").d(
                "INPUT: title='${track.name}', artists=[$queryArtist], dur=${track.durationMs}ms, " +
                "isrc=${track.externalIds?.isrc}, explicit=${track.explicit}",
            )

            val trackQuery =
                TrackQuery(
                    artist = queryArtist,
                    title = track.name,
                    album = track.album?.name,
                    isrc = track.externalIds?.isrc,
                    durationMs = track.durationMs.toLong(),
                    explicit = track.explicit,
                )

            val primaryArtistName = track.artists.firstOrNull()?.name.orEmpty()
            val cleanTitle = SpotifyQueryBuilder.cleanTrackTitle(track.name).ifBlank { track.name }
            val standardQuery = SpotifyQueryBuilder.buildSearchQuery(track)
            val cleanQuery = "$cleanTitle $primaryArtistName".trim()
            val rawQuery = "${track.name} $primaryArtistName".trim()

            val resolutionResult = searchSemaphore.withPermit {
                // Tier 1: standard query (songs)
                Timber.tag("SpotifyMatching").d("QUERY standard: '$standardQuery'")
                val stdSongs = searchSongs(standardQuery)
                val stdSongStrict = scoreStrict(trackQuery, stdSongs)
                if (stdSongStrict?.accepted != null) {
                    return@withPermit ResolutionResult(stdSongStrict.accepted!!, stdSongStrict.score, "strict_standard_song")
                }
                val stdSongRelaxed = pickRelaxedCandidate(track, stdSongs)
                if (stdSongRelaxed != null) {
                    return@withPermit ResolutionResult(stdSongRelaxed, 0.88, "relaxed_standard_song")
                }

                // Tier 2: standard query (all items / videos)
                val stdAll = searchAll(standardQuery)
                val stdAllStrict = scoreStrict(trackQuery, stdAll)
                if (stdAllStrict?.accepted != null) {
                    return@withPermit ResolutionResult(stdAllStrict.accepted!!, stdAllStrict.score, "strict_standard_all")
                }
                val stdAllRelaxed = pickRelaxedCandidate(track, stdAll)
                if (stdAllRelaxed != null) {
                    return@withPermit ResolutionResult(stdAllRelaxed, 0.82, "relaxed_standard_all")
                }

                // Tier 3: clean query (if different)
                var cleanCandidates = emptyList<SongItem>()
                if (cleanQuery.isNotBlank() && cleanQuery != standardQuery) {
                    Timber.tag("SpotifyMatching").d("QUERY clean: '$cleanQuery'")
                    val cleanSongs = searchSongs(cleanQuery)
                    val cleanSongStrict = scoreStrict(trackQuery, cleanSongs)
                    if (cleanSongStrict?.accepted != null) {
                        return@withPermit ResolutionResult(cleanSongStrict.accepted!!, cleanSongStrict.score, "strict_clean_song")
                    }
                    val cleanSongRelaxed = pickRelaxedCandidate(track, cleanSongs)
                    if (cleanSongRelaxed != null) {
                        return@withPermit ResolutionResult(cleanSongRelaxed, 0.80, "relaxed_clean_song")
                    }

                    val cleanAll = searchAll(cleanQuery)
                    val cleanAllStrict = scoreStrict(trackQuery, cleanAll)
                    if (cleanAllStrict?.accepted != null) {
                        return@withPermit ResolutionResult(cleanAllStrict.accepted!!, cleanAllStrict.score, "strict_clean_all")
                    }
                    val cleanAllRelaxed = pickRelaxedCandidate(track, cleanAll)
                    if (cleanAllRelaxed != null) {
                        return@withPermit ResolutionResult(cleanAllRelaxed, 0.75, "relaxed_clean_all")
                    }
                    cleanCandidates = cleanSongs + cleanAll
                }

                // Tier 4: raw query (if different)
                var rawCandidates = emptyList<SongItem>()
                if (rawQuery.isNotBlank() && rawQuery != standardQuery && rawQuery != cleanQuery) {
                    Timber.tag("SpotifyMatching").d("QUERY raw: '$rawQuery'")
                    val rawAll = searchAll(rawQuery)
                    val rawStrict = scoreStrict(trackQuery, rawAll)
                    if (rawStrict?.accepted != null) {
                        return@withPermit ResolutionResult(rawStrict.accepted!!, rawStrict.score, "strict_raw_all")
                    }
                    val rawRelaxed = pickRelaxedCandidate(track, rawAll)
                    if (rawRelaxed != null) {
                        return@withPermit ResolutionResult(rawRelaxed, 0.70, "relaxed_raw_all")
                    }
                    rawCandidates = rawAll
                }

                // Tier 5: best effort across all gathered candidates
                val allGathered = (stdSongs + stdAll + cleanCandidates + rawCandidates).distinctBy { it.id }
                val bestEffort = pickBestEffortCandidate(track, allGathered)
                if (bestEffort != null) {
                    return@withPermit ResolutionResult(bestEffort, 0.60, "best_effort")
                }

                null
            }

            val best =
                resolutionResult?.accepted ?: run {
                    Timber.tag("SpotifyMatching").w("REJECTED '${track.name}': all resolution tiers failed")
                    return@withContext null
                }

            Timber.tag("SpotifyMatching").i(
                "ACCEPTED '${track.name}' -> YT id=${best.id}, title='${best.title}', method=${resolutionResult.method}, score=${resolutionResult.score}",
            )

            val bestMetadata = best.toMediaMetadata()
            val metadata =
                bestMetadata.copy(
                    thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: best.thumbnail,
                    duration = if (track.durationMs > 0) track.durationMs / 1000 else best.duration ?: -1,
                    explicit = track.explicit || best.explicit,
                    album =
                        track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                            ?: bestMetadata.album,
                    spotifyTrackId = track.id.takeIf(String::isNotBlank),
                    isrc = track.externalIds?.isrc?.takeIf(String::isNotBlank) ?: bestMetadata.isrc,
                )

            Timber.tag("SpotifyMatching").d(
                "PLAYER OUTPUT: id=${metadata.id}, title='${metadata.title}', artists='${metadata.artists.joinToString { it.name }}', " +
                "dur=${metadata.duration}s, isrc=${metadata.isrc}, explicit=${metadata.explicit}",
            )

            mutex.withLock {
                cache[track.id] = metadata
                if (rawSpotifyId.isNotBlank()) {
                    cache[rawSpotifyId] = metadata
                }
            }

            val matchKey = if (rawSpotifyId.isNotBlank()) rawSpotifyId else track.id
            val existingMatch = db?.getSpotifyMatch(matchKey)
            if (existingMatch == null || !existingMatch.isManualOverride) {
                db?.insert(
                    SpotifyMatchEntity(
                        spotifyId = matchKey,
                        youtubeId = metadata.id,
                        title = track.name,
                        artist = track.artists.joinToString(" ") { it.name },
                        matchScore = resolutionResult.score,
                        isrc = metadata.isrc,
                    ),
                )
            }

            metadata
        }

    private data class ResolutionResult(
        val accepted: SongItem,
        val score: Double,
        val method: String,
    )

    private suspend fun searchSongs(query: String): List<SongItem> {
        val res = YouTube.search(query = query, filter = YouTube.SearchFilter.FILTER_SONG).getOrNull()
        return res?.items?.filterIsInstance<SongItem>()?.distinctBy { it.id }.orEmpty()
    }

    private suspend fun searchAll(query: String): List<SongItem> {
        val res = YouTube.search(query = query, filter = null).getOrNull()
        return res?.items?.filterIsInstance<SongItem>()?.distinctBy { it.id }.orEmpty()
    }

    private fun scoreStrict(
        trackQuery: TrackQuery,
        candidates: List<SongItem>,
    ): SpotifySearchScorer.CandidateDecision<SongItem>? {
        val valid = candidates.filter { (it.duration ?: 0) > 0 }
        if (valid.isEmpty()) return null
        return scorer.pickGeneric(
            track = trackQuery,
            candidates = valid,
            titleOf = { it.title },
            artistsOf = { it.artists.map { a -> a.name } },
            durationMsOf = { (it.duration ?: 0) * 1000L },
        )
    }

    private fun pickRelaxedCandidate(
        track: SpotifyTrack,
        candidates: List<SongItem>,
    ): SongItem? {
        if (candidates.isEmpty()) return null

        val targetCleanTitle = SpotifyQueryBuilder.cleanTrackTitle(track.name).ifBlank { track.name }
        val normTargetTitle = matcher.canonicalTitle(targetCleanTitle).ifBlank { matcher.canonicalTitle(track.name) }
        val primaryArtistName = track.artists.firstOrNull()?.name.orEmpty()
        val normPrimaryArtist = matcher.canonicalArtist(primaryArtistName)
        val allTargetArtists = track.artists.map { matcher.canonicalArtist(it.name) }.filter { it.isNotBlank() }
        val targetDurSec = if (track.durationMs > 0) track.durationMs / 1000 else 0

        val targetTitleWords = normTargetTitle.split(Regex("""\W+""")).filter { it.length >= 3 }

        val scoredCandidates = candidates.mapNotNull { cand ->
            val candCleanTitle = SpotifyQueryBuilder.cleanTrackTitle(cand.title).ifBlank { cand.title }
            val normCandTitle = matcher.canonicalTitle(candCleanTitle).ifBlank { matcher.canonicalTitle(cand.title) }

            val hasUnwanted = UNWANTED_TOKENS.any { token ->
                cand.title.contains(token, ignoreCase = true) && !track.name.contains(token, ignoreCase = true)
            }
            if (hasUnwanted) return@mapNotNull null

            val candDurSec = cand.duration ?: 0
            val durDelta = if (targetDurSec > 0 && candDurSec > 0) kotlin.math.abs(targetDurSec - candDurSec) else 0

            // Discard candidates with massive duration disparity (> 45s) when both durations are known
            if (targetDurSec > 0 && candDurSec > 0 && durDelta > 45) {
                return@mapNotNull null
            }

            val jwTitle = matcher.jaroWinklerSimilarity(normTargetTitle, normCandTitle)
            val titleContains = normCandTitle.contains(normTargetTitle) || normTargetTitle.contains(normCandTitle)

            val candTitleWords = normCandTitle.split(Regex("""\W+""")).filter { it.length >= 3 }
            val commonWordCount = targetTitleWords.count { targetWord ->
                candTitleWords.any { it == targetWord || it.contains(targetWord) || targetWord.contains(it) }
            }
            val wordOverlapRatio = if (targetTitleWords.isNotEmpty()) commonWordCount.toDouble() / targetTitleWords.size else 0.0

            val candArtistNames = cand.artists.map { matcher.canonicalArtist(it.name) }.filter { it.isNotBlank() }
            val candAllText = (cand.artists.map { it.name } + cand.title).joinToString(" ").lowercase()

            val primaryArtistMatched = normPrimaryArtist.isNotBlank() && (
                candArtistNames.any { it == normPrimaryArtist || it.contains(normPrimaryArtist) || normPrimaryArtist.contains(it) || matcher.jaroWinklerSimilarity(normPrimaryArtist, it) >= 0.75 } ||
                candAllText.contains(primaryArtistName.lowercase())
            )

            val anyArtistMatched = primaryArtistMatched || allTargetArtists.any { artist ->
                candArtistNames.any { it == artist || it.contains(artist) || artist.contains(it) || matcher.jaroWinklerSimilarity(artist, it) >= 0.75 } ||
                candAllText.contains(artist)
            }

            val isQualified = when {
                jwTitle >= 0.75 -> true
                titleContains && (anyArtistMatched || durDelta <= 20) -> true
                wordOverlapRatio >= 0.60 && anyArtistMatched -> true
                jwTitle >= 0.55 && anyArtistMatched && (candDurSec == 0 || durDelta <= 30) -> true
                jwTitle >= 0.65 && (candDurSec > 0 && durDelta <= 10) -> true
                else -> false
            }

            if (!isQualified) return@mapNotNull null

            var score = jwTitle * 40.0
            if (titleContains) score += 20.0
            if (primaryArtistMatched) score += 25.0 else if (anyArtistMatched) score += 15.0
            score += wordOverlapRatio * 15.0

            if (targetDurSec > 0 && candDurSec > 0) {
                val durScore = (25.0 - (durDelta * 0.75)).coerceAtLeast(0.0)
                score += durScore
            }

            Pair(cand, score)
        }

        return scoredCandidates.maxByOrNull { it.second }?.first
    }

    private fun pickBestEffortCandidate(
        track: SpotifyTrack,
        candidates: List<SongItem>,
    ): SongItem? {
        if (candidates.isEmpty()) return null
        val targetCleanTitle = SpotifyQueryBuilder.cleanTrackTitle(track.name).ifBlank { track.name }
        val normTargetTitle = matcher.canonicalTitle(targetCleanTitle).ifBlank { matcher.canonicalTitle(track.name) }
        val targetDurSec = if (track.durationMs > 0) track.durationMs / 1000 else 0
        val targetWords = normTargetTitle.split(Regex("""\W+""")).filter { it.length >= 3 }

        return candidates.firstOrNull { cand ->
            val hasUnwanted = UNWANTED_TOKENS.any { token ->
                cand.title.contains(token, ignoreCase = true) && !track.name.contains(token, ignoreCase = true)
            }
            if (hasUnwanted) return@firstOrNull false

            val candDurSec = cand.duration ?: 0
            if (targetDurSec > 0 && candDurSec > 0 && kotlin.math.abs(targetDurSec - candDurSec) > 60) {
                return@firstOrNull false
            }

            val normCandTitle = matcher.canonicalTitle(cand.title)
            val sharesWord = targetWords.any { word -> normCandTitle.contains(word) }
            val titleSim = matcher.jaroWinklerSimilarity(normTargetTitle, normCandTitle)

            sharesWord || titleSim >= 0.45
        }
    }
}
