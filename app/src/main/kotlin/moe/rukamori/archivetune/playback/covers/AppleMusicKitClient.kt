/*
 * Ozone Player (2026)
 * Apple MusicKit Catalog & Motion Art Client
 * Serverless direct integration via public Apple MusicKit Web Gateway
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.covers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object AppleMusicKitClient {
    private const val TAG = "AppleMusicKitClient"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val API_BASE = "https://api.music.apple.com/v1/catalog"
    private const val AMP_BASE = "https://amp-api.music.apple.com/v1/catalog"

    private val INDEX_SCRIPT_REGEX = Regex("""/assets/index~[^"]+\.js""")
    private val TOKEN_REGEX = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenMutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    data class AppleMatch(
        val artwork3000Url: String,
        val albumId: String?,
        val motionVideoUrl: String? = null,
    )

    suspend fun getBearerToken(): String? = cachedToken ?: tokenMutex.withLock {
        cachedToken ?: run {
            fetchWebToken()
        }?.also { cachedToken = it }
    }

    private suspend fun fetchWebToken(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val pageRequest = Request.Builder()
                .url("https://music.apple.com/us/new")
                .header("User-Agent", USER_AGENT)
                .build()
            val pageHtml = client.newCall(pageRequest).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return@runCatching null

            val scriptPath = INDEX_SCRIPT_REGEX.find(pageHtml)?.value ?: return@runCatching null
            val scriptRequest = Request.Builder()
                .url("https://music.apple.com$scriptPath")
                .header("User-Agent", USER_AGENT)
                .build()
            val scriptContent = client.newCall(scriptRequest).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return@runCatching null

            TOKEN_REGEX.find(scriptContent)?.value
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to extract Apple MusicKit web token")
        }.getOrNull()
    }

    fun invalidateToken() {
        cachedToken = null
    }

    /**
     * Searches Apple Music catalog for matching song and extracts 3000px master artwork.
     */
    suspend fun searchTrack(
        cleanTitle: String,
        cleanArtist: String,
        albumName: String? = null,
        durationMs: Long = 0L,
    ): AppleMatch? = withContext(Dispatchers.IO) {
        val token = getBearerToken() ?: return@withContext null

        val url = "$API_BASE/us/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", "$cleanTitle $cleanArtist")
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "10")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .build()

        val responseBody = runCatching {
            client.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    invalidateToken()
                    throw IOException("Apple token expired (HTTP ${resp.code})")
                }
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }.getOrNull() ?: return@withContext null
        val songItems = root["results"]?.jsonObject?.get("songs")?.jsonObject?.get("data")?.jsonArray ?: return@withContext null

        val normalizedWantedTitle = TrackCleaner.normalize(cleanTitle)
        val normalizedWantedArtist = TrackCleaner.normalize(cleanArtist)
        val normalizedWantedAlbum = albumName?.let(TrackCleaner::normalize)

        for (item in songItems) {
            val itemObj = item as? JsonObject ?: continue
            val attrs = itemObj["attributes"]?.jsonObject ?: continue

            val name = attrs["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val artist = attrs["artistName"]?.jsonPrimitive?.contentOrNull ?: continue
            val itemDuration = attrs["durationInMillis"]?.jsonPrimitive?.longOrNull ?: 0L
            val itemAlbum = attrs["albumName"]?.jsonPrimitive?.contentOrNull

            val normName = TrackCleaner.normalize(name)
            val normArtist = TrackCleaner.normalize(artist)
            val normAlbum = itemAlbum?.let(TrackCleaner::normalize)

            // Duration check within 3.5 seconds if duration is known
            val durationMatches = durationMs <= 0 || itemDuration <= 0 || abs(itemDuration - durationMs) <= 3500

            val titleMatches = normName == normalizedWantedTitle || normName.contains(normalizedWantedTitle) || normalizedWantedTitle.contains(normName)
            val artistMatches = normArtist == normalizedWantedArtist || normArtist.contains(normalizedWantedArtist) || normalizedWantedArtist.contains(normArtist)

            if (titleMatches && artistMatches && durationMatches) {
                // If album is specified, prefer album match
                if (normalizedWantedAlbum != null && normAlbum != null && normAlbum != normalizedWantedAlbum) {
                    continue
                }

                val artworkUrlTemplate = attrs["artwork"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                if (artworkUrlTemplate != null) {
                    val artwork3000 = formatArtworkUrl(artworkUrlTemplate, 3000, 3000)
                    val albumId = extractAlbumId(attrs)

                    val motionUrl = if (albumId != null) {
                        fetchAlbumMotionVideo(albumId, token)
                    } else null

                    return@withContext AppleMatch(
                        artwork3000Url = artwork3000,
                        albumId = albumId,
                        motionVideoUrl = motionUrl,
                    )
                }
            }
        }

        null
    }

    private fun extractAlbumId(attributes: JsonObject): String? {
        val url = attributes["url"]?.jsonPrimitive?.contentOrNull ?: return null
        // URL pattern: https://music.apple.com/us/album/song-name/123456789?i=987654321
        val regex = Regex("""/album/[^/]+/(\d+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Queries Apple Music album editorial video to fetch looping motion artwork.
     */
    suspend fun fetchAlbumMotionVideo(albumId: String, bearerToken: String): String? = withContext(Dispatchers.IO) {
        val url = "$AMP_BASE/us/albums/$albumId".toHttpUrl().newBuilder()
            .addQueryParameter("extend", "editorialVideo")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Authorization", "Bearer $bearerToken")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .build()

        val responseText = runCatching {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null

        val root = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull() ?: return@withContext null
        val albumObj = (root["data"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return@withContext null
        val attributes = albumObj["attributes"]?.jsonObject ?: return@withContext null
        val editorialVideo = attributes["editorialVideo"]?.jsonObject ?: return@withContext null

        // Try square motion video first, then motion detail
        val square = editorialVideo["motionDetailSquare"]?.jsonObject
            ?: editorialVideo["motionSquareVideo1x1"]?.jsonObject
            ?: editorialVideo["motionDetailTall"]?.jsonObject

        listOf("video", "videoUrl", "hlsUrl", "url").firstNotNullOfOrNull { key ->
            square?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://") }
        }
    }

    /**
     * Replaces Apple Music's URL template `{w}x{h}bb.{f}` with exact dimensions.
     */
    fun formatArtworkUrl(template: String, width: Int = 3000, height: Int = 3000): String {
        return template
            .replace("{w}", width.toString())
            .replace("{h}", height.toString())
            .replace("{f}", "jpg")
            .replace("{c}", "")
    }
}
