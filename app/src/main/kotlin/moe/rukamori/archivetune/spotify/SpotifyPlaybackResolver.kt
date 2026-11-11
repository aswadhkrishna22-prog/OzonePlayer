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
import moe.rukamori.archivetune.spotify.matching.SpotifyTrackScorer
import moe.rukamori.archivetune.spotify.matching.SpotifyTrackScorer.AmbiguityResult
import moe.rukamori.archivetune.spotify.matching.SpotifyTrackScorer.ScoredCandidate
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber

sealed interface PlaybackResolutionDecision {
    data class Resolved(val metadata: MediaMetadata) : PlaybackResolutionDecision
    data class Ambiguous(
        val topCandidates: List<SongItem>,
        val spotifyTrack: SpotifyTrack,
        val bestMetadata: MediaMetadata,
    ) : PlaybackResolutionDecision
    data object NotFound : PlaybackResolutionDecision
}

object SpotifyPlaybackResolver {
    private const val CACHE_MAX_SIZE = 512

    private val searchSemaphore = kotlinx.coroutines.sync.Semaphore(3)

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
        contextQuery: String? = null,
    ): MediaItem? = resolveToMetadata(track, database, contextQuery)?.toMediaItem()

    suspend fun resolveToMetadata(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
        contextQuery: String? = null,
    ): MediaMetadata? {
        return when (val decision = resolveCandidateDecision(track, database, contextQuery)) {
            is PlaybackResolutionDecision.Resolved -> decision.metadata
            is PlaybackResolutionDecision.Ambiguous -> decision.bestMetadata
            is PlaybackResolutionDecision.NotFound -> null
        }
    }

    suspend fun resolveCandidateDecision(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
        contextQuery: String? = null,
    ): PlaybackResolutionDecision =
        withContext(Dispatchers.IO) {
            val db = database ?: databaseRef
            if (database != null && databaseRef == null) {
                databaseRef = database
            }

            val cacheKey = SpotifyTrackScorer.cacheKey(track.id)
            val rawSpotifyId = track.id.removePrefix("spotify:track:").removePrefix("spotify:").trim()
            val targetLang = detectLanguage("${track.name} ${track.album?.name.orEmpty()} ${contextQuery.orEmpty()}")

            mutex.withLock {
                cache[cacheKey]?.let { cached ->
                    Timber.tag("SpotifyMatching").d("CACHE HIT: '${track.name}' (key=$cacheKey) -> YT ${cached.id}")
                    return@withContext PlaybackResolutionDecision.Resolved(cached)
                }
            }

            if (db != null) {
                val match =
                    db.getSpotifyMatch(cacheKey)
                        ?: db.getSpotifyMatch(rawSpotifyId)?.takeIf { it.isManualOverride }
                        ?: db.getSpotifyMatch(track.id)?.takeIf { it.isManualOverride }
                if (match != null) {
                    val dbSong = db.getSongByIdBlocking(match.youtubeId)
                    val cachedTitle = dbSong?.song?.title ?: match.title
                    val cachedArtist = dbSong?.artists?.joinToString(" ") { it.name } ?: match.artist
                    val cachedLang = detectLanguage("$cachedTitle $cachedArtist")
                    val isStaleLanguageMatch =
                        !match.isManualOverride &&
                            targetLang != null &&
                            cachedLang != null &&
                            targetLang != cachedLang

                    if (!isStaleLanguageMatch) {
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
                            cache[cacheKey] = metadata
                        }
                        Timber.tag("SpotifyMatching").d("DB MATCH HIT: '${track.name}' (key=$cacheKey) -> YT ${metadata.id}")
                        return@withContext PlaybackResolutionDecision.Resolved(metadata)
                    } else {
                        Timber.tag("SpotifyMatching").w(
                            "INVALIDATING STALE DB MATCH: '${track.name}' (target=$targetLang) was cached to YT ${match.youtubeId} (cached=$cachedLang)",
                        )
                    }
                }
            }

            val artistsList = track.artists.map { it.name }.filter { it.isNotBlank() }
            val queryArtist = artistsList.joinToString(", ")
            Timber.tag("SpotifyMatching").d(
                "INPUT: title='${track.name}', artists=[$queryArtist], dur=${track.durationMs}ms, " +
                "isrc=${track.externalIds?.isrc}, explicit=${track.explicit}",
            )

            val primaryArtistName = track.artists.firstOrNull()?.name.orEmpty()
            val cleanTitle = SpotifyQueryBuilder.cleanTrackTitle(track.name).ifBlank { track.name }
            val baseTitle = extractBaseTitle(track.name).ifBlank { cleanTitle }
            val langQuery = if (targetLang != null) " $targetLang" else ""

            val standardQuery = buildStandardQuery(track, targetLang)
            val baseArtistQuery = "$baseTitle$langQuery $primaryArtistName".trim()
            val cleanQuery = "$cleanTitle$langQuery $primaryArtistName".trim()
            val baseTitleQuery = "$baseTitle$langQuery".trim()
            val rawQuery = "${track.name}$langQuery $primaryArtistName".trim()
            val albumName = track.album?.name.orEmpty()
            val baseAlbumQuery =
                if (albumName.isNotBlank() && !baseTitle.contains(albumName, ignoreCase = true)) {
                    "$baseTitle$langQuery $albumName".trim()
                } else {
                    ""
                }

            val decisionResult =
                searchSemaphore.withPermit {
                    val gatheredCandidates = mutableListOf<SongItem>()
                    val officialSongIds = mutableSetOf<String>()

                    suspend fun collectCandidates(
                        searchFn: suspend () -> List<SongItem>,
                        isOfficial: Boolean,
                    ) {
                        val items = searchFn()
                        if (isOfficial) {
                            officialSongIds.addAll(items.map { it.id })
                        }
                        gatheredCandidates.addAll(items)
                    }

                    // Tier 1: standard query (songs)
                    Timber.tag("SpotifyMatching").d("QUERY standard: '$standardQuery'")
                    collectCandidates({ searchSongs(standardQuery) }, isOfficial = true)

                    // Tier 2: base artist query (songs) if different
                    if (baseArtistQuery.isNotBlank() && baseArtistQuery != standardQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY baseArtist: '$baseArtistQuery'")
                        collectCandidates({ searchSongs(baseArtistQuery) }, isOfficial = true)
                    }

                    // Quick evaluation if we found solid official song candidates
                    val distinctInitial = gatheredCandidates.distinctBy { it.id }
                    val scoredInitial = distinctInitial.map { cand ->
                        SpotifyTrackScorer.scoreCandidate(
                            targetTitle = track.name,
                            targetArtists = artistsList,
                            targetAlbum = track.album?.name,
                            targetDurationMs = track.durationMs.toLong(),
                            contextQuery = contextQuery,
                            candidate = cand,
                            isOfficialSong = officialSongIds.contains(cand.id),
                        )
                    }

                    val ambiguityInitial = SpotifyTrackScorer.evaluateAmbiguity(scoredInitial, artistsList)
                    if (ambiguityInitial is AmbiguityResult.ClearWinner && ambiguityInitial.score >= 50.0) {
                        return@withPermit ambiguityInitial
                    }

                    // Tier 3: standard and base queries (videos & all)
                    collectCandidates({ searchVideos(standardQuery) }, isOfficial = false)
                    collectCandidates({ searchAll(standardQuery) }, isOfficial = false)

                    if (baseArtistQuery.isNotBlank() && baseArtistQuery != standardQuery) {
                        collectCandidates({ searchVideos(baseArtistQuery) }, isOfficial = false)
                        collectCandidates({ searchAll(baseArtistQuery) }, isOfficial = false)
                    }

                    // Tier 4: clean query (if different)
                    if (cleanQuery.isNotBlank() && cleanQuery != standardQuery && cleanQuery != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY clean: '$cleanQuery'")
                        collectCandidates({ searchSongs(cleanQuery) }, isOfficial = true)
                        collectCandidates({ searchAll(cleanQuery) }, isOfficial = false)
                    }

                    // Tier 5: base album query (if applicable)
                    if (baseAlbumQuery.isNotBlank() && baseAlbumQuery != standardQuery && baseAlbumQuery != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY baseAlbum: '$baseAlbumQuery'")
                        collectCandidates({ searchSongs(baseAlbumQuery) }, isOfficial = true)
                        collectCandidates({ searchAll(baseAlbumQuery) }, isOfficial = false)
                    }

                    // Tier 6: base title alone query (if different)
                    if (baseTitleQuery.isNotBlank() && baseTitleQuery != standardQuery && baseTitleQuery != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY baseTitle: '$baseTitleQuery'")
                        collectCandidates({ searchSongs(baseTitleQuery) }, isOfficial = true)
                        collectCandidates({ searchVideos(baseTitleQuery) }, isOfficial = false)
                    }

                    // Tier 7: raw query (if different)
                    if (rawQuery.isNotBlank() && rawQuery != standardQuery && rawQuery != cleanQuery && rawQuery != baseArtistQuery) {
                        Timber.tag("SpotifyMatching").d("QUERY raw: '$rawQuery'")
                        collectCandidates({ searchAll(rawQuery) }, isOfficial = false)
                    }

                    val allDistinct = gatheredCandidates.distinctBy { it.id }
                    val scoredAll = allDistinct.map { cand ->
                        SpotifyTrackScorer.scoreCandidate(
                            targetTitle = track.name,
                            targetArtists = artistsList,
                            targetAlbum = track.album?.name,
                            targetDurationMs = track.durationMs.toLong(),
                            contextQuery = contextQuery,
                            candidate = cand,
                            isOfficialSong = officialSongIds.contains(cand.id),
                        )
                    }

                    SpotifyTrackScorer.evaluateAmbiguity(scoredAll, artistsList)
                }

            when (decisionResult) {
                is AmbiguityResult.ClearWinner -> {
                    val accepted = decisionResult.accepted
                    val bestMetadata = accepted.toMediaMetadata().copy(
                        thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: accepted.thumbnail,
                        duration = if (track.durationMs > 0) track.durationMs / 1000 else accepted.duration ?: -1,
                        explicit = track.explicit || accepted.explicit,
                        album = track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                            ?: accepted.toMediaMetadata().album,
                        spotifyTrackId = track.id.takeIf(String::isNotBlank),
                        isrc = track.externalIds?.isrc?.takeIf(String::isNotBlank) ?: accepted.toMediaMetadata().isrc,
                    )

                    mutex.withLock {
                        cache[cacheKey] = bestMetadata
                    }

                    db?.insert(
                        SpotifyMatchEntity(
                            spotifyId = cacheKey,
                            youtubeId = bestMetadata.id,
                            title = accepted.title,
                            artist = accepted.artists.joinToString(" ") { it.name }.ifBlank { track.artists.joinToString(" ") { it.name } },
                            matchScore = decisionResult.score,
                            isrc = bestMetadata.isrc,
                            isManualOverride = false,
                        ),
                    )

                    Timber.tag("SpotifyMatching").i(
                        "RESOLVED '${track.name}' -> YT id=${accepted.id}, title='${accepted.title}', score=${decisionResult.score}",
                    )
                    PlaybackResolutionDecision.Resolved(bestMetadata)
                }

                is AmbiguityResult.Ambiguous -> {
                    val best = decisionResult.best
                    val bestMetadata = best.toMediaMetadata().copy(
                        thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: best.thumbnail,
                        duration = if (track.durationMs > 0) track.durationMs / 1000 else best.duration ?: -1,
                        explicit = track.explicit || best.explicit,
                        album = track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                            ?: best.toMediaMetadata().album,
                        spotifyTrackId = track.id.takeIf(String::isNotBlank),
                        isrc = track.externalIds?.isrc?.takeIf(String::isNotBlank) ?: best.toMediaMetadata().isrc,
                    )

                    Timber.tag("SpotifyMatching").w(
                        "AMBIGUITY GUARD TRIGGERED for '${track.name}'. Top candidates: ${decisionResult.topCandidates.size}",
                    )
                    PlaybackResolutionDecision.Ambiguous(
                        topCandidates = decisionResult.topCandidates,
                        spotifyTrack = track,
                        bestMetadata = bestMetadata,
                    )
                }

                is AmbiguityResult.NoCandidates -> {
                    Timber.tag("SpotifyMatching").w("REJECTED '${track.name}': all resolution tiers failed")
                    PlaybackResolutionDecision.NotFound
                }
            }
        }

    suspend fun fetchTopCandidates(
        trackId: String,
        title: String,
        artists: List<String>,
        durationMs: Long = 0L,
        album: String? = null,
        contextQuery: String? = null,
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val artistStr = artists.joinToString(" ")
        val targetLang = SpotifyTrackScorer.detectLanguage("$title ${album.orEmpty()} ${contextQuery.orEmpty()}")
        val langQuery = if (targetLang != null) " $targetLang" else ""
        val primaryQuery = "$title$langQuery $artistStr".trim()
        val baseTitle = SpotifyTrackScorer.extractBaseTitle(title)
        val secondaryQuery = "$baseTitle$langQuery $artistStr".trim()

        val songs = searchSongs(primaryQuery)
        val moreSongs = if (secondaryQuery != primaryQuery) searchSongs(secondaryQuery) else emptyList()
        val videos = searchVideos(primaryQuery)
        val all = searchAll(primaryQuery)

        val officialSongIds = (songs + moreSongs).map { it.id }.toSet()
        val allCandidates = (songs + moreSongs + videos + all).distinctBy { it.id }

        val scored = allCandidates.map { cand ->
            SpotifyTrackScorer.scoreCandidate(
                targetTitle = title,
                targetArtists = artists,
                targetAlbum = album,
                targetDurationMs = durationMs,
                contextQuery = contextQuery,
                candidate = cand,
                isOfficialSong = officialSongIds.contains(cand.id),
            )
        }

        val valid = scored.filter { !it.isDisqualified && it.totalScore > -100.0 }.sortedByDescending { it.totalScore }
        val top = valid.take(8).map { it.candidate }
        if (top.isNotEmpty()) {
            top
        } else {
            allCandidates.take(8)
        }
    }

    suspend fun storeOverride(
        database: MusicDatabase,
        spotifyTrackId: String,
        chosen: SongItem,
        targetTrackTitle: String? = null,
        targetArtist: String? = null,
    ) {
        val key = SpotifyTrackScorer.cacheKey(spotifyTrackId)
        val entity = SpotifyMatchEntity(
            spotifyId = key,
            youtubeId = chosen.id,
            title = chosen.title,
            artist = chosen.artists.joinToString(", ") { it.name }.ifBlank { targetArtist.orEmpty() },
            matchScore = 1.0,
            isManualOverride = true,
        )
        withContext(Dispatchers.IO) {
            database.insert(entity)
            val meta = chosen.toMediaMetadata().copy(spotifyTrackId = spotifyTrackId)
            mutex.withLock {
                cache[key] = meta
            }
        }
    }

    suspend fun resetOverride(
        database: MusicDatabase,
        spotifyTrackId: String,
    ) {
        val key = SpotifyTrackScorer.cacheKey(spotifyTrackId)
        val rawId = spotifyTrackId.removePrefix("spotify:track:").removePrefix("spotify:").trim()
        withContext(Dispatchers.IO) {
            database.deleteSpotifyMatch(key)
            database.deleteSpotifyMatch(rawId)
            database.deleteSpotifyMatch(spotifyTrackId)
            mutex.withLock {
                cache.remove(key)
                cache.remove(rawId)
                cache.remove(spotifyTrackId)
            }
        }
    }

    suspend fun hasOverride(
        database: MusicDatabase,
        spotifyTrackId: String,
    ): Boolean {
        val key = SpotifyTrackScorer.cacheKey(spotifyTrackId)
        val rawId = spotifyTrackId.removePrefix("spotify:track:").removePrefix("spotify:").trim()
        return withContext(Dispatchers.IO) {
            val match = database.getSpotifyMatch(key)
                ?: database.getSpotifyMatch(rawId)
                ?: database.getSpotifyMatch(spotifyTrackId)
            match?.isManualOverride == true
        }
    }

    internal fun buildStandardQuery(track: SpotifyTrack, targetLang: String? = null): String {
        val rawStandardQuery = SpotifyQueryBuilder.buildSearchQuery(track)
        return if (targetLang != null && !rawStandardQuery.contains(targetLang, ignoreCase = true)) {
            "$rawStandardQuery $targetLang".trim()
        } else {
            rawStandardQuery
        }
    }

    internal fun detectLanguage(text: String): String? = SpotifyTrackScorer.detectLanguage(text)

    internal fun extractBaseTitle(title: String): String = SpotifyTrackScorer.extractBaseTitle(title)

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
}
