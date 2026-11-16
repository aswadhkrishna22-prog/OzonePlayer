/*
 * Ozone Player (2026)
 * Deezer High-Resolution Album Cover Client
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.covers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object DeezerClient {
    private const val TAG = "DeezerClient"
    private const val SEARCH_URL = "https://api.deezer.com/search"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Searches Deezer for a matching track and extracts the cover_xl URL (1000-1200px).
     */
    suspend fun searchCover(
        cleanTitle: String,
        cleanArtist: String,
        albumName: String? = null,
        durationMs: Long = 0L,
    ): String? = withContext(Dispatchers.IO) {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", "$cleanTitle $cleanArtist")
            .addQueryParameter("limit", "12")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "OzonePlayer/1.0 (HD Album Artwork)")
            .build()

        val responseBody = runCatching {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Deezer search request failed")
        }.getOrNull() ?: return@withContext null

        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }.getOrNull() ?: return@withContext null
        if (root["error"] != null) return@withContext null

        val data = root["data"]?.jsonArray ?: return@withContext null

        val wantedTitle = TrackCleaner.normalize(cleanTitle)
        val wantedArtist = TrackCleaner.normalize(cleanArtist)
        val wantedAlbum = albumName?.let(TrackCleaner::normalize)

        for (item in data) {
            val itemObj = item as? JsonObject ?: continue
            val title = itemObj["title_short"]?.jsonPrimitive?.contentOrNull
                ?: itemObj["title"]?.jsonPrimitive?.contentOrNull ?: continue
            val artistObj = itemObj["artist"]?.jsonObject ?: continue
            val artist = artistObj["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val durationSec = itemObj["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val albumObj = itemObj["album"]?.jsonObject
            val albumTitle = albumObj?.get("title")?.jsonPrimitive?.contentOrNull

            val normTitle = TrackCleaner.normalize(title)
            val normArtist = TrackCleaner.normalize(artist)
            val normAlbum = albumTitle?.let(TrackCleaner::normalize)

            val durationMatches = durationMs <= 0 || durationSec <= 0 || abs(durationSec * 1000L - durationMs) <= 3500
            val titleMatches = normTitle == wantedTitle || normTitle.contains(wantedTitle) || wantedTitle.contains(normTitle)
            val artistMatches = normArtist == wantedArtist || normArtist.contains(wantedArtist) || wantedArtist.contains(normArtist)

            if (titleMatches && artistMatches && durationMatches) {
                if (wantedAlbum != null && normAlbum != null && normAlbum != wantedAlbum) {
                    continue
                }

                val coverXl = albumObj?.get("cover_xl")?.jsonPrimitive?.contentOrNull
                    ?: albumObj?.get("cover_big")?.jsonPrimitive?.contentOrNull

                if (coverXl != null && coverXl.startsWith("https://")) {
                    return@withContext coverXl
                }
            }
        }

        null
    }
}
