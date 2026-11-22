/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.queues

import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.RadioSourceJioSaavnKey
import moe.rukamori.archivetune.constants.RadioSourceYouTubeKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnCandidate
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnStreamResolver
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.get
import timber.log.Timber
import java.util.Collections
import kotlin.math.abs

/**
 * Infinite Radio Queue mixing JioSaavn station recommendations and YouTube Music radio.
 *
 * Requirements:
 * 1. Seed JioSaavn / YT Music cross-matching with similarity threshold verification.
 * 2. 1:1 Interleaved batches between JioSaavn and YouTube Music.
 * 3. Graceful single-source continuation if one source fails or is disabled in settings.
 * 4. Normalized title+artist duplicate filtering & duration ±3s verification.
 * 5. Cover / remix / variant filtering (unless seed is a variant).
 * 6. Language lock (Malayalam, Tamil, Telugu seeds never mix other regional languages).
 * 7. Metadata-only queue with lazy stream resolution on playback (preventing URL expiration).
 * 8. Queue title set to "Radio: <seed song>".
 */
class InfiniteRadioQueue(
    val seed: MediaMetadata,
    val seedCandidate: JioSaavnCandidate? = null,
    val seedSongItem: SongItem? = null,
    val database: MusicDatabase? = null,
    val dataStore: DataStore<Preferences>? = null,
    private val jioSaavnResolver: JioSaavnStreamResolver = JioSaavnStreamResolver.shared,
) : Queue {
    override val preloadItem: MediaMetadata? = seed

    private var hasMore: Boolean = true
    private var jioSaavnStationId: String? = null
    private var jioSaavnNextPage: Int = 1
    private var jioSaavnExhausted: Boolean = false

    private var ytRadioEndpoint: WatchEndpoint? = null
    private var ytContinuation: String? = null
    private var ytExhausted: Boolean = false

    private var seedLanguage: String? = null

    private val playedIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val seenSignatures = Collections.synchronizedSet(mutableSetOf<String>())
    private val seenDurations = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())

    override suspend fun getInitialStatus(): Queue.Status = withContext(Dispatchers.IO) {
        val cleanSeedId = seed.id.removePrefix("jiosaavn_").trim()
        playedIds.add(seed.id)
        if (cleanSeedId.isNotBlank()) playedIds.add(cleanSeedId)

        val seedArtistStr = seed.artists.joinToString(" ") { it.name }
        recordTrackSignature(seed.title, seedArtistStr, seed.duration.toLong())

        database?.insert(seed)

        // 1. Detect and lock seed song language
        seedLanguage = detectSeedLanguage()
        Timber.d("InfiniteRadioQueue: Seed='${seed.title}', Detected language='$seedLanguage'")

        // 2. Initialize sources based on settings
        val isJioSaavnEnabled = dataStore?.get(RadioSourceJioSaavnKey, true) ?: true
        val isYouTubeEnabled = dataStore?.get(RadioSourceYouTubeKey, true) ?: true

        if (isJioSaavnEnabled) {
            setupJioSaavnSource()
        } else {
            jioSaavnExhausted = true
        }

        if (isYouTubeEnabled) {
            setupYouTubeSource()
        } else {
            ytExhausted = true
        }

        // 3. Fetch first batch of interleaved radio tracks
        val initialBatch = fetchNextBatch(targetCount = 10)
        if (initialBatch.isEmpty() && jioSaavnExhausted && ytExhausted) {
            hasMore = false
        }

        val seedMediaItem = seed.toMediaItem()
        val initialItems = listOf(seedMediaItem) + initialBatch

        Queue.Status(
            title = "Radio: ${seed.title}",
            items = initialItems,
            mediaItemIndex = 0,
            position = 0L,
        )
    }

    override fun hasNextPage(): Boolean = hasMore && (!jioSaavnExhausted || !ytExhausted)

    override suspend fun nextPage(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!hasNextPage()) return@withContext emptyList()
        val batch = fetchNextBatch(targetCount = 8)
        if (batch.isEmpty()) {
            hasMore = false
        }
        batch
    }

    private suspend fun setupJioSaavnSource() {
        if (seed.id.startsWith("jiosaavn_") || seedCandidate != null) {
            val jioId = seedCandidate?.id ?: seed.id.removePrefix("jiosaavn_")
            jioSaavnStationId = jioSaavnResolver.createEntityStation(jioId)
            if (jioSaavnStationId == null) {
                Timber.w("Failed to create JioSaavn station for seed '$jioId'")
                jioSaavnExhausted = true
            }
        } else {
            // Seed is YouTube or other: search JioSaavn with title + artist and check similarity
            val artistStr = seed.artists.joinToString(" ") { it.name }
            val query = "${seed.title} $artistStr".trim()
            val candidates = jioSaavnResolver.searchSongs(query, limit = 10)
            val trackQuery = TrackQuery(
                title = seed.title,
                artist = artistStr,
                durationMs = if (seed.duration > 0) seed.duration * 1000L else null,
            )
            val best = jioSaavnResolver.pickBestCandidate(candidates, trackQuery)
            if (best != null) {
                val similarity = calculateSimilarity(
                    titleA = seed.title,
                    artistA = artistStr,
                    durA = seed.duration.toLong(),
                    titleB = best.title,
                    artistB = best.artists.joinToString(" "),
                    durB = best.durationSec,
                )
                if (similarity >= 35.0) {
                    jioSaavnStationId = jioSaavnResolver.createEntityStation(best.id)
                    val bestLang = best.language
                    if (seedLanguage == null && !bestLang.isNullOrBlank()) {
                        seedLanguage = bestLang.lowercase().trim()
                    }
                } else {
                    Timber.d("JioSaavn candidate '${best.title}' below similarity threshold ($similarity < 35.0)")
                    jioSaavnExhausted = true
                }
            } else {
                jioSaavnExhausted = true
            }
        }
    }

    private suspend fun setupYouTubeSource() {
        if (!seed.id.startsWith("jiosaavn_")) {
            ytRadioEndpoint = WatchEndpoint(videoId = seed.id, playlistId = "RDAMVM${seed.id}")
        } else {
            // Seed is JioSaavn: search YouTube with title + artist and check similarity threshold
            val artistStr = seed.artists.joinToString(" ") { it.name }
            val query = "${seed.title} $artistStr".trim()
            val ytSearch = runCatching {
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            }.getOrNull()

            val candidates = ytSearch?.items?.filterIsInstance<SongItem>() ?: emptyList()
            var matchedYtId: String? = null
            for (candidate in candidates) {
                val similarity = calculateSimilarity(
                    titleA = seed.title,
                    artistA = artistStr,
                    durA = seed.duration.toLong(),
                    titleB = candidate.title,
                    artistB = candidate.artists.joinToString(" ") { it.name },
                    durB = (candidate.duration ?: 0).toLong(),
                )
                if (similarity >= 35.0) {
                    matchedYtId = candidate.id
                    break
                }
            }

            if (matchedYtId != null) {
                ytRadioEndpoint = WatchEndpoint(videoId = matchedYtId, playlistId = "RDAMVM$matchedYtId")
            } else {
                Timber.d("No matching YouTube song met similarity threshold for '${seed.title}'")
                ytExhausted = true
            }
        }
    }

    private suspend fun detectSeedLanguage(): String? {
        val seedCandLang = seedCandidate?.language
        if (!seedCandLang.isNullOrBlank()) {
            return seedCandLang.lowercase().trim()
        }

        if (seed.id.startsWith("jiosaavn_")) {
            val candidate = jioSaavnResolver.fetchSongDetails(seed.id)
            val candidateLang = candidate?.language
            if (!candidateLang.isNullOrBlank()) {
                return candidateLang.lowercase().trim()
            }
        }

        // Script detection on title and artists
        val combined = "${seed.title} ${seed.artists.joinToString(" ") { it.name }}"
        for (ch in combined) {
            when (ch) {
                in '\u0D00'..'\u0D7F' -> return "malayalam"
                in '\u0B80'..'\u0BFF' -> return "tamil"
                in '\u0C00'..'\u0C7F' -> return "telugu"
                in '\u0900'..'\u097F' -> return "hindi"
            }
        }

        val lower = combined.lowercase()
        val malayalamKeywords = listOf(
            "sushin", "hesham", "deepak dev", "shaan rahman", "yesudas", "chithra",
            "mg sreekumar", "vineeth", "sithara", "job kurian", "harisankar", "rex vijayan",
            "jassie gift", "vidyasagar", "dabzee", "fejo", "thirumaali", "neeraj madhav",
            "jakes bejoy", "christo xavier", "vishnu vijay", "pranavam sasi", "hanan sha"
        )
        if (malayalamKeywords.any { lower.contains(it) }) return "malayalam"

        val tamilKeywords = listOf(
            "anirudh", "a.r. rahman", "ar rahman", "yuvan", "ilaiyaraaja", "ilayaraja",
            "harris jayaraj", "sid sriram", "dhanush", "santhosh narayanan", "d. imman",
            "gv prakash", "hiphop tamizha", "sam c.s.", "sean roldan", "pradeep kumar"
        )
        if (tamilKeywords.any { lower.contains(it) }) return "tamil"

        val teluguKeywords = listOf(
            "devi sri prasad", "thaman", "keeravani", "anurag kulkarni", "ram miriyala",
            "mickey j", "vivek sagar", "chaitan bharadwaj", "bheems"
        )
        if (teluguKeywords.any { lower.contains(it) }) return "telugu"

        return null
    }

    private suspend fun fetchNextBatch(targetCount: Int = 10): List<MediaItem> = withContext(Dispatchers.IO) {
        val isJioSaavnEnabled = dataStore?.get(RadioSourceJioSaavnKey, true) ?: true
        val isYouTubeEnabled = dataStore?.get(RadioSourceYouTubeKey, true) ?: true

        val jioItems = mutableListOf<MediaItem>()
        if (isJioSaavnEnabled && !jioSaavnExhausted && jioSaavnStationId != null) {
            try {
                val songs = jioSaavnResolver.getStationSongs(
                    stationId = jioSaavnStationId!!,
                    count = 10,
                    next = jioSaavnNextPage,
                )
                if (songs.isEmpty()) {
                    jioSaavnExhausted = true
                } else {
                    jioSaavnNextPage++
                    for (candidate in songs) {
                        val mediaId = if (candidate.id.startsWith("jiosaavn_")) candidate.id else "jiosaavn_${candidate.id}"
                        if (candidate.id in playedIds || mediaId in playedIds) continue
                        val artistStr = candidate.artists.joinToString(" ")
                        if (isLanguageMismatch(candidate.language, candidate.title, artistStr, seedLanguage)) continue
                        if (isVariantOrCover(candidate.title, seed.title)) continue
                        if (isDuplicate(candidate.title, artistStr, candidate.durationSec)) continue

                        playedIds.add(candidate.id)
                        playedIds.add(mediaId)
                        recordTrackSignature(candidate.title, artistStr, candidate.durationSec)

                        val meta = MediaMetadata(
                            id = mediaId,
                            title = candidate.title,
                            artists = candidate.artists.map { MediaMetadata.Artist(id = null, name = it) },
                            duration = candidate.durationSec.toInt(),
                            thumbnailUrl = candidate.imageUrl?.replace("150x150", "500x500"),
                            album = candidate.album?.let { MediaMetadata.Album(id = "jiosaavn_album_${candidate.id}", title = it) },
                        )
                        database?.insert(meta)
                        jioItems.add(candidate.toRadioMediaItem())
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "JioSaavn station fetch encountered error, continuing with available sources")
            }
        }

        val ytItems = mutableListOf<MediaItem>()
        if (isYouTubeEnabled && !ytExhausted && ytRadioEndpoint != null) {
            try {
                val nextResult = YouTube.next(
                    endpoint = ytRadioEndpoint!!,
                    continuation = ytContinuation,
                    followAutomixPreview = true,
                ).getOrNull()

                if (nextResult == null || nextResult.items.isEmpty()) {
                    ytExhausted = true
                } else {
                    ytContinuation = nextResult.continuation
                    ytRadioEndpoint = nextResult.endpoint
                    if (ytContinuation == null) {
                        ytExhausted = true
                    }
                    for (songItem in nextResult.items) {
                        if (songItem.id in playedIds) continue
                        val artistStr = songItem.artists.joinToString(" ") { it.name }
                        if (isLanguageMismatch(null, songItem.title, artistStr, seedLanguage)) continue
                        if (isVariantOrCover(songItem.title, seed.title)) continue
                        val durSec = (songItem.duration ?: 0).toLong()
                        if (isDuplicate(songItem.title, artistStr, durSec)) continue

                        playedIds.add(songItem.id)
                        recordTrackSignature(songItem.title, artistStr, durSec)

                        val meta = songItem.toMediaMetadata()
                        database?.insert(meta)
                        ytItems.add(songItem.toMediaItem())
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "YouTube radio fetch encountered error, continuing with available sources")
            }
        }

        // Interleave JioSaavn and YouTube Music approximately 1:1
        val interleaved = mutableListOf<MediaItem>()
        val maxLen = maxOf(jioItems.size, ytItems.size)
        for (i in 0 until maxLen) {
            if (i < jioItems.size) interleaved.add(jioItems[i])
            if (i < ytItems.size) interleaved.add(ytItems[i])
        }

        interleaved
    }

    private fun normalizeString(text: String): String {
        return text.lowercase()
            .replace(Regex("""(?i)\((?:official|lyric|lyrics|video|audio|full|visualizer|remastered|from\s+"[^"]*").*?\)"""), "")
            .replace(Regex("""(?i)\[(?:official|lyric|lyrics|video|audio|full|visualizer|remastered|4k|hd).*?\]"""), "")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeArtist(artist: String): String {
        return artist.lowercase()
            .replace(Regex("""(?i)\b(?:feat|ft|and|various|artists|music|by)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isDuplicate(title: String, artist: String, durationSec: Long): Boolean {
        val normTitle = normalizeString(title)
        val normArtist = normalizeArtist(artist)
        val signature = "$normTitle|$normArtist"
        if (seenSignatures.contains(signature)) return true

        if (durationSec > 0) {
            synchronized(seenDurations) {
                for ((seenT, seenD) in seenDurations) {
                    if (abs(seenD - durationSec) <= 3L && (seenT == normTitle || seenT.contains(normTitle) || normTitle.contains(seenT))) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun recordTrackSignature(title: String, artist: String, durationSec: Long) {
        val normTitle = normalizeString(title)
        val normArtist = normalizeArtist(artist)
        seenSignatures.add("$normTitle|$normArtist")
        if (durationSec > 0) {
            seenDurations.add(normTitle to durationSec)
        }
    }

    private fun isVariantOrCover(candidateTitle: String, seedTitle: String): Boolean {
        val candLower = candidateTitle.lowercase()
        val seedLower = seedTitle.lowercase()
        for (term in VARIANT_TERMS) {
            if (candLower.contains(term) && !seedLower.contains(term)) {
                return true
            }
        }
        return false
    }

    private fun isLanguageMismatch(
        candidateLang: String?,
        candidateTitle: String,
        candidateArtist: String,
        lockedLang: String?,
    ): Boolean {
        if (lockedLang == null) return false
        val locked = lockedLang.lowercase().trim()
        if (locked !in LOCKED_REGIONAL_LANGUAGES) return false

        // 1. Explicit JioSaavn language tag check
        if (!candidateLang.isNullOrBlank()) {
            val cLang = candidateLang.lowercase().trim()
            if (cLang != locked) return true
        }

        // 2. Cross-script conflict rejection
        val combined = "$candidateTitle $candidateArtist"
        for (ch in combined) {
            when {
                locked != "malayalam" && ch in '\u0D00'..'\u0D7F' -> return true
                locked != "tamil" && ch in '\u0B80'..'\u0BFF' -> return true
                locked != "telugu" && ch in '\u0C00'..'\u0C7F' -> return true
                ch in '\u0900'..'\u097F' -> return true // Devanagari / Hindi
                ch in '\u0A00'..'\u0A7F' -> return true // Gurmukhi / Punjabi
                ch in '\u0C80'..'\u0CFF' -> return true // Kannada
                ch in '\u0980'..'\u09FF' -> return true // Bengali
            }
        }

        // 3. Keyword check for other languages
        val lower = combined.lowercase()
        val conflictingWords = when (locked) {
            "malayalam" -> listOf("tamil", "telugu", "hindi", "bhojpuri", "punjabi", "kannada", "bollywood")
            "tamil" -> listOf("malayalam", "telugu", "hindi", "bhojpuri", "punjabi", "kannada", "bollywood")
            "telugu" -> listOf("malayalam", "tamil", "hindi", "bhojpuri", "punjabi", "kannada", "bollywood")
            else -> emptyList()
        }
        if (conflictingWords.any { lower.contains(it) }) return true

        return false
    }

    private fun calculateSimilarity(
        titleA: String,
        artistA: String,
        durA: Long,
        titleB: String,
        artistB: String,
        durB: Long,
    ): Double {
        var score = 0.0
        val normA = normalizeString(titleA)
        val normB = normalizeString(titleB)

        if (normA == normB) {
            score += 50.0
        } else if (normA.contains(normB) || normB.contains(normA)) {
            score += 35.0
        } else {
            val wordsA = normA.split(" ").filter { it.length > 2 }
            val matchCount = wordsA.count { normB.contains(it) }
            score += matchCount * 8.0
        }

        val artA = normalizeArtist(artistA)
        val artB = normalizeArtist(artistB)
        if (artA.isNotBlank() && artB.isNotBlank()) {
            if (artA.contains(artB) || artB.contains(artA)) {
                score += 30.0
            } else {
                val wordsArt = artA.split(" ").filter { it.length > 2 }
                if (wordsArt.any { artB.contains(it) }) {
                    score += 15.0
                }
            }
        }

        if (durA > 0 && durB > 0) {
            val diff = abs(durA - durB)
            when {
                diff <= 3 -> score += 20.0
                diff <= 8 -> score += 15.0
                diff <= 15 -> score += 5.0
            }
        }

        return score
    }

    private fun JioSaavnCandidate.toRadioMediaItem(): MediaItem {
        val itemMediaId = if (id.startsWith("jiosaavn_")) id else "jiosaavn_$id"
        val metadata = MediaMetadata(
            id = itemMediaId,
            title = title,
            artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
            duration = durationSec.toInt(),
            thumbnailUrl = imageUrl?.replace("150x150", "500x500"),
            album = album?.let { MediaMetadata.Album(id = "jiosaavn_album_$id", title = it) },
        )
        return MediaItem.Builder()
            .setMediaId(itemMediaId)
            .setUri(itemMediaId)
            .setCustomCacheKey(itemMediaId)
            .setTag(metadata)
            .setMediaMetadata(
                Media3Metadata.Builder()
                    .setTitle(title)
                    .setSubtitle(artists.joinToString(", "))
                    .setArtist(artists.joinToString(", "))
                    .setArtworkUri(metadata.thumbnailUrl?.toUri())
                    .setAlbumTitle(album)
                    .setIsPlayable(true)
                    .setMediaType(Media3Metadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }

    companion object {
        val LOCKED_REGIONAL_LANGUAGES = setOf("malayalam", "tamil", "telugu")

        val VARIANT_TERMS = listOf(
            "cover", "remix", "slowed", "reverb", "re-recorded", "karaoke",
            "instrumental", "8d", "tribute", "mashup", "acoustic",
            "ringtone", "status", "shorts", "lofi", "lo-fi", "bass boosted",
            "chipmunk", "speed up", "sped up", "dj mix", "dance mix"
        )
    }
}
