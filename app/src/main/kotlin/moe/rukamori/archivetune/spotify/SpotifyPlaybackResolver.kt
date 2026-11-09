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
            val baseTitle = extractBaseTitle(track.name).ifBlank { cleanTitle }
            val standardQuery = SpotifyQueryBuilder.buildSearchQuery(track)
            val baseArtistQuery = "$baseTitle $primaryArtistName".trim()
            val cleanQuery = "$cleanTitle $primaryArtistName".trim()
            val rawQuery = "${track.name} $primaryArtistName".trim()
            val albumName = track.album?.name.orEmpty()
            val baseAlbumQuery =
                if (albumName.isNotBlank() && !baseTitle.contains(albumName, ignoreCase = true)) {
                    "$baseTitle $albumName".trim()
                } else {
                    ""
                }

            val resolutionResult =
                searchSemaphore.withPermit {
                    val gatheredCandidates = mutableListOf<SongItem>()

                    fun evaluateCandidates(
                        candidates: List<SongItem>,
                        tierName: String,
                    ): ResolutionResult? {
                        if (candidates.isEmpty()) return null
                        gatheredCandidates.addAll(candidates)
                        val strictRes = scoreStrict(trackQuery, candidates)
                        if (strictRes?.accepted != null) {
                            return ResolutionResult(strictRes.accepted!!, strictRes.score, "strict_$tierName")
                        }
                        val relaxedRes = pickRelaxedCandidate(track, candidates)
                        if (relaxedRes != null) {
                            return ResolutionResult(relaxedRes, 0.85, "relaxed_$tierName")
                        }
                        return null
                    }

                    // Tier 1: standard query (songs)
                    Timber.tag("SpotifyMatching").d("QUERY standard: '$standardQuery'")
                    val stdSongs = searchSongs(standardQuery)
                    evaluateCandidates(stdSongs, "standard_song")?.let { return@withPermit it }

                    // Tier 2: base artist query (songs) if different
                    if (baseArtistQuery.isNotBlank() && baseArtistQuery != standardQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY baseArtist: '$baseArtistQuery'")
                        val baseSongs = searchSongs(baseArtistQuery)
                        evaluateCandidates(baseSongs, "base_song")?.let { return@withPermit it }
                    }

                    // Tier 3: standard and base queries (videos & all)
                    val stdVideos = searchVideos(standardQuery)
                    evaluateCandidates(stdVideos, "standard_video")?.let { return@withPermit it }

                    val stdAll = searchAll(standardQuery)
                    evaluateCandidates(stdAll, "standard_all")?.let { return@withPermit it }

                    if (baseArtistQuery.isNotBlank() && baseArtistQuery != standardQuery) {
                        val baseVideos = searchVideos(baseArtistQuery)
                        evaluateCandidates(baseVideos, "base_video")?.let { return@withPermit it }

                        val baseAll = searchAll(baseArtistQuery)
                        evaluateCandidates(baseAll, "base_all")?.let { return@withPermit it }
                    }

                    // Tier 4: clean query (if different)
                    if (cleanQuery.isNotBlank() && cleanQuery != standardQuery && cleanQuery != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY clean: '$cleanQuery'")
                        val cleanSongs = searchSongs(cleanQuery)
                        evaluateCandidates(cleanSongs, "clean_song")?.let { return@withPermit it }

                        val cleanAll = searchAll(cleanQuery)
                        evaluateCandidates(cleanAll, "clean_all")?.let { return@withPermit it }
                    }

                    // Tier 5: base album query (if applicable)
                    if (baseAlbumQuery.isNotBlank() && baseAlbumQuery != standardQuery && baseAlbumQuery != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY baseAlbum: '$baseAlbumQuery'")
                        val albumSongs = searchSongs(baseAlbumQuery)
                        evaluateCandidates(albumSongs, "album_song")?.let { return@withPermit it }

                        val albumAll = searchAll(baseAlbumQuery)
                        evaluateCandidates(albumAll, "album_all")?.let { return@withPermit it }
                    }

                    // Tier 6: base title alone query (if different)
                    if (baseTitle.isNotBlank() && baseTitle != standardQuery && baseTitle != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY baseTitle: '$baseTitle'")
                        val titleSongs = searchSongs(baseTitle)
                        evaluateCandidates(titleSongs, "title_song")?.let { return@withPermit it }

                        val titleVideos = searchVideos(baseTitle)
                        evaluateCandidates(titleVideos, "title_video")?.let { return@withPermit it }
                    }

                    // Tier 7: raw query (if different)
                    if (rawQuery.isNotBlank() && rawQuery != standardQuery && rawQuery != cleanQuery && rawQuery != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY raw: '$rawQuery'")
                        val rawAll = searchAll(rawQuery)
                        evaluateCandidates(rawAll, "raw_all")?.let { return@withPermit it }
                    }

                    // Tier 8: best effort across all gathered candidates
                    val allGathered = gatheredCandidates.distinctBy { it.id }
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

    internal fun extractBaseTitle(title: String): String {
        val cleaned =
            title
                .replace(Regex("""(?i)\s*[\(\[]\s*(?:from\s+["“']?[^"”'\)\]]+["”']?|original\s+(?:motion\s+picture\s+)?soundtrack|soundtrack|ost)\s*[\)\]]"""), "")
                .replace(Regex("""(?i)\s*[-–—]\s*(?:from\s+["“']?[^"”']+["”']?|original\s+(?:motion\s+picture\s+)?soundtrack|soundtrack|ost).*$"""), "")
                .replace(Regex("""(?i)\s*[-–—]\s*from\s+.*$"""), "")
                .replace(Regex("""(?i)\s*[\(\[]\s*(?:feat\.|ft\.|featuring)\s+[^)]*[\)\]]"""), "")
                .trim()
                .trim('-', '–', '—', '"', '\'', ' ', '\t')

        return cleaned.ifBlank { title }
    }

    private suspend fun searchSongs(query: String): List<SongItem> {
        val res = YouTube.search(query = query, filter = YouTube.SearchFilter.FILTER_SONG).getOrNull()
        return res?.items?.filterIsInstance<SongItem>()?.distinctBy { it.id }.orEmpty()
    }

    private suspend fun searchVideos(query: String): List<SongItem> {
        val res = YouTube.search(query = query, filter = YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
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

        val baseTitle = extractBaseTitle(track.name).ifBlank { track.name }
        val normBaseTitle = matcher.canonicalTitle(baseTitle).ifBlank { matcher.canonicalTitle(track.name) }
        val cleanTitle = SpotifyQueryBuilder.cleanTrackTitle(track.name).ifBlank { track.name }
        val normCleanTitle = matcher.canonicalTitle(cleanTitle).ifBlank { normBaseTitle }

        val primaryArtistName = track.artists.firstOrNull()?.name.orEmpty()
        val normPrimaryArtist = matcher.canonicalArtist(primaryArtistName)
        val allTargetArtists = track.artists.map { matcher.canonicalArtist(it.name) }.filter { it.isNotBlank() }
        val targetDurSec = if (track.durationMs > 0) track.durationMs / 1000 else 0

        val artistTokens = track.artists.flatMap { it.name.lowercase().split(Regex("""\W+""")) }.filter { it.length >= 3 }
        val albumTokens = track.album?.name?.lowercase()?.split(Regex("""\W+"""))?.filter { it.length >= 3 }.orEmpty()

        val baseTitleWords = normBaseTitle.split(Regex("""\W+""")).filter { it.length >= 3 }
        val cleanTitleWords = normCleanTitle.split(Regex("""\W+""")).filter { it.length >= 3 }
        val targetTitleWords = if (baseTitleWords.isNotEmpty()) baseTitleWords else cleanTitleWords

        val scoredCandidates =
            candidates.mapNotNull { cand ->
                val candCleanTitle = SpotifyQueryBuilder.cleanTrackTitle(cand.title).ifBlank { cand.title }
                val normCandTitle = matcher.canonicalTitle(candCleanTitle).ifBlank { matcher.canonicalTitle(cand.title) }

                val hasUnwanted =
                    UNWANTED_TOKENS.any { token ->
                        cand.title.contains(token, ignoreCase = true) && !track.name.contains(token, ignoreCase = true)
                    }
                if (hasUnwanted) return@mapNotNull null

                val candDurSec = cand.duration ?: 0
                val durDelta = if (targetDurSec > 0 && candDurSec > 0) kotlin.math.abs(targetDurSec - candDurSec) else 0

                // Discard candidates with massive duration disparity (> 60s) when both durations are known
                if (targetDurSec > 0 && candDurSec > 0 && durDelta > 60) {
                    return@mapNotNull null
                }

                val jwBaseTitle = matcher.jaroWinklerSimilarity(normBaseTitle, normCandTitle)
                val jwCleanTitle = matcher.jaroWinklerSimilarity(normCleanTitle, normCandTitle)
                val jwTitle = maxOf(jwBaseTitle, jwCleanTitle)

                val baseContained =
                    normBaseTitle.isNotBlank() && (
                        normCandTitle.contains(normBaseTitle) || normBaseTitle.contains(normCandTitle)
                    )
                val cleanContained =
                    normCleanTitle.isNotBlank() && (
                        normCandTitle.contains(normCleanTitle) || normCleanTitle.contains(normCandTitle)
                    )
                val titleContains = baseContained || cleanContained

                val candTitleWords = normCandTitle.split(Regex("""\W+""")).filter { it.length >= 3 }
                val commonWordCount =
                    targetTitleWords.count { targetWord ->
                        candTitleWords.any { it == targetWord || it.contains(targetWord) || targetWord.contains(it) }
                    }
                val wordOverlapRatio = if (targetTitleWords.isNotEmpty()) commonWordCount.toDouble() / targetTitleWords.size else 0.0

                val candArtistNames = cand.artists.map { matcher.canonicalArtist(it.name) }.filter { it.isNotBlank() }
                val candAllText = (cand.artists.map { it.name } + cand.title).joinToString(" ").lowercase()

                val primaryArtistMatched =
                    normPrimaryArtist.isNotBlank() && (
                        candArtistNames.any {
                            it == normPrimaryArtist || it.contains(normPrimaryArtist) || normPrimaryArtist.contains(it) ||
                                matcher.jaroWinklerSimilarity(normPrimaryArtist, it) >= 0.75
                        } ||
                            candAllText.contains(primaryArtistName.lowercase())
                    )

                val anyArtistMatched =
                    primaryArtistMatched ||
                        allTargetArtists.any { artist ->
                            candArtistNames.any {
                                it == artist || it.contains(artist) || artist.contains(it) ||
                                    matcher.jaroWinklerSimilarity(artist, it) >= 0.75
                            } ||
                                candAllText.contains(artist)
                        }

                val tokenArtistMatched = anyArtistMatched || artistTokens.any { candAllText.contains(it) }
                val albumMatched = albumTokens.isNotEmpty() && albumTokens.any { candAllText.contains(it) }
                val contextMatched = tokenArtistMatched || albumMatched

                val isQualified =
                    when {
                        jwTitle >= 0.75 -> true
                        titleContains && (contextMatched || durDelta <= 30) -> true
                        wordOverlapRatio >= 0.50 && (contextMatched || durDelta <= 20) -> true
                        jwTitle >= 0.50 && contextMatched && (candDurSec == 0 || durDelta <= 45) -> true
                        jwTitle >= 0.60 && (candDurSec > 0 && durDelta <= 15) -> true
                        baseContained && candDurSec > 0 && durDelta <= 45 -> true
                        else -> false
                    }

                if (!isQualified) return@mapNotNull null

                var score = jwTitle * 40.0
                if (titleContains) score += 25.0
                if (primaryArtistMatched) {
                    score += 25.0
                } else if (tokenArtistMatched) {
                    score += 20.0
                } else if (albumMatched) {
                    score += 10.0
                }
                score += wordOverlapRatio * 20.0

                if (targetDurSec > 0 && candDurSec > 0) {
                    val durScore = (25.0 - (durDelta * 0.5)).coerceAtLeast(0.0)
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
        val baseTitle = extractBaseTitle(track.name).ifBlank { track.name }
        val normBaseTitle = matcher.canonicalTitle(baseTitle).ifBlank { matcher.canonicalTitle(track.name) }
        val targetDurSec = if (track.durationMs > 0) track.durationMs / 1000 else 0
        val targetWords = normBaseTitle.split(Regex("""\W+""")).filter { it.length >= 3 }
        val artistTokens = track.artists.flatMap { it.name.lowercase().split(Regex("""\W+""")) }.filter { it.length >= 3 }

        val nonUnwanted =
            candidates.filter { cand ->
                !UNWANTED_TOKENS.any { token ->
                    cand.title.contains(token, ignoreCase = true) && !track.name.contains(token, ignoreCase = true)
                }
            }.ifEmpty { candidates }

        // Try candidate sharing words or high title similarity and duration delta <= 60s
        val matchWithDuration =
            nonUnwanted.firstOrNull { cand ->
                val candDurSec = cand.duration ?: 0
                if (targetDurSec > 0 && candDurSec > 0 && kotlin.math.abs(targetDurSec - candDurSec) > 60) {
                    return@firstOrNull false
                }
                val normCandTitle = matcher.canonicalTitle(cand.title)
                val sharesWord = targetWords.any { word -> normCandTitle.contains(word) }
                val titleSim = matcher.jaroWinklerSimilarity(normBaseTitle, normCandTitle)
                sharesWord || titleSim >= 0.40
            }
        if (matchWithDuration != null) return matchWithDuration

        // Try candidate where artist token matches in candidate title/artist
        val matchWithArtistToken =
            nonUnwanted.firstOrNull { cand ->
                val candAll = (cand.artists.map { it.name } + cand.title).joinToString(" ").lowercase()
                artistTokens.any { candAll.contains(it) }
            }
        if (matchWithArtistToken != null) return matchWithArtistToken

        // Fallback: take the first non-unwanted candidate that has reasonable duration (<= 90s delta)
        val reasonableDuration =
            nonUnwanted.firstOrNull { cand ->
                val candDurSec = cand.duration ?: 0
                candDurSec == 0 || targetDurSec == 0 || kotlin.math.abs(targetDurSec - candDurSec) <= 90
            }
        if (reasonableDuration != null) return reasonableDuration

        // Ultimate fallback: first item
        return nonUnwanted.firstOrNull()
    }
}
