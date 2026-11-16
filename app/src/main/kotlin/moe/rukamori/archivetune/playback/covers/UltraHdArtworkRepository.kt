/*
 * Ozone Player (2026)
 * Ultra HD (3000px) Artwork & Motion Art Repository
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.covers

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object UltraHdArtworkRepository {
    private const val TAG = "UltraHdArtwork"
    private const val CACHE_FILE_NAME = "ultra_hd_covers_v1.json"
    private const val MOTION_CACHE_FILE_NAME = "apple_motion_v1.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val coverCache = ConcurrentHashMap<String, String>()
    private val motionCache = ConcurrentHashMap<String, String>()

    private val inFlight = ConcurrentHashMap<String, Boolean>()

    private var cacheFile: File? = null
    private var motionCacheFile: File? = null

    // Reactive flow of all resolved covers (songKey -> hdUrl)
    private val _coversFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val coversFlow: StateFlow<Map<String, String>> = _coversFlow.asStateFlow()

    // Reactive flow of all resolved motion video loops (songKey -> motionUrl)
    private val _motionFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val motionFlow: StateFlow<Map<String, String>> = _motionFlow.asStateFlow()

    fun init(context: Context) {
        val dir = File(context.filesDir, "hd_covers").apply { mkdirs() }
        cacheFile = File(dir, CACHE_FILE_NAME)
        motionCacheFile = File(dir, MOTION_CACHE_FILE_NAME)

        scope.launch {
            loadCaches()
        }
    }

    private suspend fun loadCaches() = withContext(Dispatchers.IO) {
        cacheFile?.takeIf { it.exists() }?.let { file ->
            runCatching {
                val map = json.decodeFromString<Map<String, String>>(file.readText())
                coverCache.putAll(map)
                _coversFlow.value = HashMap(coverCache)
                Timber.tag(TAG).d("Loaded %d Ultra HD cover mappings", map.size)
            }
        }

        motionCacheFile?.takeIf { it.exists() }?.let { file ->
            runCatching {
                val map = json.decodeFromString<Map<String, String>>(file.readText())
                motionCache.putAll(map)
                _motionFlow.value = HashMap(motionCache)
                Timber.tag(TAG).d("Loaded %d Apple Motion Art mappings", map.size)
            }
        }
    }

    fun makeSongKey(rawTitle: String, rawArtist: String, rawAlbum: String?): String {
        val clean = TrackCleaner.clean(rawTitle, rawArtist)
        val primary = TrackCleaner.primaryArtist(clean.artist)
        return listOf(
            primary.lowercase(Locale.ROOT),
            clean.title.lowercase(Locale.ROOT),
            rawAlbum?.trim()?.lowercase(Locale.ROOT) ?: ""
        ).joinToString("|")
    }

    /**
     * Synchronously returns the 3000px Ultra HD cover if already resolved and cached;
     * otherwise null.
     */
    fun getCachedHdCover(rawTitle: String, rawArtist: String, rawAlbum: String?): String? {
        val key = makeSongKey(rawTitle, rawArtist, rawAlbum)
        return coverCache[key]?.takeIf { it.isNotBlank() }
    }

    /**
     * Synchronously returns the Apple Motion video URL if already resolved and cached;
     * otherwise null.
     */
    fun getCachedMotionUrl(rawTitle: String, rawArtist: String, rawAlbum: String?): String? {
        val key = makeSongKey(rawTitle, rawArtist, rawAlbum)
        return motionCache[key]?.takeIf { it.isNotBlank() }
    }

    /**
     * Asynchronously requests high-resolution cover resolution for the current song.
     * Tries Apple Music (3000px) first, then Deezer (1200px).
     * If found, updates the reactive flows and disk caches.
     */
    fun resolveUltraHdArtwork(
        rawTitle: String,
        rawArtist: String,
        rawAlbum: String?,
        durationMs: Long,
    ) {
        if (rawTitle.isBlank() || rawArtist.isBlank()) return
        val key = makeSongKey(rawTitle, rawArtist, rawAlbum)

        if (coverCache.containsKey(key)) return
        if (inFlight.putIfAbsent(key, true) != null) return

        scope.launch {
            try {
                val clean = TrackCleaner.clean(rawTitle, rawArtist)

                // 1. Try Apple Music (3000px studio master cover + Apple Motion Art)
                val appleMatch = runCatching {
                    AppleMusicKitClient.searchTrack(
                        cleanTitle = clean.title,
                        cleanArtist = clean.artist,
                        albumName = rawAlbum,
                        durationMs = durationMs,
                    )
                }.getOrNull()

                var resolvedCover = appleMatch?.artwork3000Url
                val resolvedMotion = appleMatch?.motionVideoUrl

                // 2. Try Deezer fallback (1200px) if Apple had no match
                if (resolvedCover == null) {
                    resolvedCover = runCatching {
                        DeezerClient.searchCover(
                            cleanTitle = clean.title,
                            cleanArtist = clean.artist,
                            albumName = rawAlbum,
                            durationMs = durationMs,
                        )
                    }.getOrNull()
                }

                if (resolvedCover != null) {
                    coverCache[key] = resolvedCover
                    _coversFlow.value = HashMap(coverCache)
                    persistCoverCache()
                    Timber.tag(TAG).d("Resolved Ultra HD cover (3000px) for %s: %s", clean.title, resolvedCover)
                }

                if (resolvedMotion != null) {
                    motionCache[key] = resolvedMotion
                    _motionFlow.value = HashMap(motionCache)
                    persistMotionCache()
                    Timber.tag(TAG).d("Resolved Apple Motion Art for %s: %s", clean.title, resolvedMotion)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error resolving Ultra HD artwork for %s", rawTitle)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private suspend fun persistCoverCache() = withContext(Dispatchers.IO) {
        val file = cacheFile ?: return@withContext
        runCatching {
            file.writeText(json.encodeToString<Map<String, String>>(coverCache.toMap()))
        }
    }

    private suspend fun persistMotionCache() = withContext(Dispatchers.IO) {
        val file = motionCacheFile ?: return@withContext
        runCatching {
            file.writeText(json.encodeToString<Map<String, String>>(motionCache.toMap()))
        }
    }
}
