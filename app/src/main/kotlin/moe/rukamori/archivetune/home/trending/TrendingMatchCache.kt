/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.trending

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.spotify.matching.TrackMatcher
import java.io.File

sealed interface MatchCacheResult {
    data class Hit(val videoId: String) : MatchCacheResult
    data object Negative : MatchCacheResult
    data object Miss : MatchCacheResult
}

@Serializable
private data class MatchEntry(
    val videoId: String?,
    val timestamp: Long,
)

@Serializable
private data class MatchCachePayload(
    val entries: Map<String, MatchEntry> = emptyMap(),
)

class TrendingMatchCache(context: Context) {
    private val cacheFile = File(context.filesDir, "trending_matches.json")
    private val mutex = Mutex()
    private val trackMatcher = TrackMatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private val memoryCache = mutableMapOf<String, MatchEntry>()
    private var isLoaded = false

    private companion object {
        const val POSITIVE_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        const val NEGATIVE_TTL_MS = 1L * 60 * 60 * 1000 // 1 hour
    }

    private fun cacheKey(artist: String, title: String): String {
        val normArtist = trackMatcher.canonicalArtist(artist)
        val normTitle = trackMatcher.canonicalTitle(title)
        return "${normArtist}__$normTitle"
    }

    private suspend fun ensureLoaded() = mutex.withLock {
        if (isLoaded) return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                if (cacheFile.exists()) {
                    val text = cacheFile.readText()
                    val payload = json.decodeFromString<MatchCachePayload>(text)
                    memoryCache.putAll(payload.entries)
                }
            }
        }
        isLoaded = true
    }

    suspend fun get(artist: String, title: String): MatchCacheResult {
        ensureLoaded()
        val key = cacheKey(artist, title)
        val now = System.currentTimeMillis()

        mutex.withLock {
            val entry = memoryCache[key] ?: return MatchCacheResult.Miss
            if (entry.videoId != null) {
                // Positive match
                if (now - entry.timestamp <= POSITIVE_TTL_MS) {
                    return MatchCacheResult.Hit(entry.videoId)
                } else {
                    memoryCache.remove(key)
                }
            } else {
                // Negative match
                if (now - entry.timestamp <= NEGATIVE_TTL_MS) {
                    return MatchCacheResult.Negative
                } else {
                    memoryCache.remove(key)
                }
            }
        }
        return MatchCacheResult.Miss
    }

    suspend fun putPositive(artist: String, title: String, videoId: String) {
        ensureLoaded()
        val key = cacheKey(artist, title)
        val entry = MatchEntry(videoId = videoId, timestamp = System.currentTimeMillis())
        mutex.withLock {
            memoryCache[key] = entry
        }
        persist()
    }

    suspend fun putNegative(artist: String, title: String) {
        ensureLoaded()
        val key = cacheKey(artist, title)
        val entry = MatchEntry(videoId = null, timestamp = System.currentTimeMillis())
        mutex.withLock {
            memoryCache[key] = entry
        }
        persist()
    }

    private suspend fun persist() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val payload = MatchCachePayload(entries = memoryCache.toMap())
                    val text = json.encodeToString(payload)
                    cacheFile.writeText(text)
                }
            }
        }
    }
}
