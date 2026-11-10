/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.streaming

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.flaccore.FlacLogger
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

data class JioSaavnCandidate(
    val id: String,
    val title: String,
    val subtitle: String?,
    val album: String?,
    val durationSec: Long,
    val artists: List<String>,
    val encryptedMediaUrl: String,
    val imageUrl: String?,
)

/**
 * High-fidelity 320 kbps AAC stream-URL resolver backed by the JioSaavn catalog.
 * Resolves regional Indian (Malayalam, Tamil, Telugu, Hindi, Kannada, Punjabi)
 * and international tracks to direct Azure/Akamai CDN 320 kbps audio streams.
 */
class JioSaavnStreamResolver(
    private val client: OkHttpClient,
) {
    private val logger = FlacLogger(TAG)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    suspend fun resolve(query: TrackQuery, requestedQuality: Int): FlacStreamUrl? = withContext(Dispatchers.IO) {
        val candidates = findCandidates(query)
        if (candidates.isEmpty()) {
            logger.d("No candidates found on JioSaavn for '${query.title}'")
            return@withContext null
        }

        val best = pickBestCandidate(candidates, query) ?: run {
            logger.d("No matching candidate met confidence threshold for '${query.title}'")
            return@withContext null
        }

        val rawUrl = JioSaavnDecryptor.decryptMediaUrl(best.encryptedMediaUrl) ?: run {
            logger.w("Failed to decrypt JioSaavn media URL for '${best.title}'")
            return@withContext null
        }

        val streamUrl320 = JioSaavnDecryptor.to320KbpsUrl(rawUrl)
        logger.i("JioSaavn resolved 320kbps stream: $streamUrl320 for '${query.title}'")

        FlacStreamUrl(
            url = streamUrl320,
            expiresAtMs = System.currentTimeMillis() + 86_400_000L, // 24 hours
            codec = "aac",
            bitsPerSample = 16,
            sampleRateHz = 44100,
            bitrateKbps = 320,
            coverArtUrl = best.imageUrl?.replace("150x150", "500x500"),
            origin = ORIGIN,
        )
    }

    private fun findCandidates(query: TrackQuery): List<JioSaavnCandidate> {
        val searchTerms = buildSearchTerms(query)
        for (term in searchTerms) {
            val results = search(term)
            if (results.isNotEmpty()) {
                return results
            }
        }
        return emptyList()
    }

    fun buildSearchTerms(query: TrackQuery): List<String> = buildList {
        val cleanTitle = cleanString(query.title)
        val cleanArtist = cleanString(query.artist)
        val primaryArtist = cleanArtist.split(",", ";", "&", "feat").firstOrNull()?.trim().orEmpty()

        if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
            add("$cleanTitle $cleanArtist")
        }
        if (cleanTitle.isNotBlank() && primaryArtist.isNotBlank() && primaryArtist != cleanArtist) {
            add("$cleanTitle $primaryArtist")
        }
        if (cleanTitle.isNotBlank()) {
            add(cleanTitle)
        }
        // Fallback to raw query title + artist
        add("${query.title} ${query.artist}".trim())
    }.distinct()

    private fun search(searchTerm: String): List<JioSaavnCandidate> {
        return try {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("__call", "search.getResults")
                .addQueryParameter("_format", "json")
                .addQueryParameter("_marker", "0")
                .addQueryParameter("api_version", "4")
                .addQueryParameter("ctx", "web6dot0")
                .addQueryParameter("n", "10")
                .addQueryParameter("p", "1")
                .addQueryParameter("q", searchTerm)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string().orEmpty()
            parseSearchResponse(body)
        } catch (e: Exception) {
            logger.w("JioSaavn search failed for '$searchTerm': ${e.message}")
            emptyList()
        }
    }

    fun parseSearchResponse(jsonString: String): List<JioSaavnCandidate> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val root = json.parseToJsonElement(jsonString) as? JsonObject ?: return emptyList()
            val results = root["results"] as? JsonArray ?: return emptyList()

            results.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val title = unescapeHtml(obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
                val subtitle = unescapeHtml(obj["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty())
                val image = obj["image"]?.jsonPrimitive?.contentOrNull
                val moreInfo = obj["more_info"] as? JsonObject
                val album = unescapeHtml(moreInfo?.get("album")?.jsonPrimitive?.contentOrNull.orEmpty())
                val encUrl = moreInfo?.get("encrypted_media_url")?.jsonPrimitive?.contentOrNull.orEmpty()
                val durationStr = moreInfo?.get("duration")?.jsonPrimitive?.contentOrNull.orEmpty()
                val durationSec = durationStr.toLongOrNull() ?: 0L

                val artistMap = moreInfo?.get("artistMap") as? JsonObject
                val artists = mutableListOf<String>()
                (artistMap?.get("primary_artists") as? JsonArray)?.forEach { a ->
                    (a as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.let { artists.add(unescapeHtml(it)) }
                }
                (artistMap?.get("artists") as? JsonArray)?.forEach { a ->
                    (a as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.let { artists.add(unescapeHtml(it)) }
                }

                if (id.isNotBlank() && title.isNotBlank() && encUrl.isNotBlank()) {
                    JioSaavnCandidate(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        album = album,
                        durationSec = durationSec,
                        artists = artists.distinct(),
                        encryptedMediaUrl = encUrl,
                        imageUrl = image,
                    )
                } else null
            }
        } catch (e: Exception) {
            logger.w("Failed to parse JioSaavn search response: ${e.message}")
            emptyList()
        }
    }

    fun pickBestCandidate(candidates: List<JioSaavnCandidate>, query: TrackQuery): JioSaavnCandidate? {
        val scored = candidates.map { candidate ->
            candidate to scoreCandidate(candidate, query)
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return null
        return if (best.second >= 35.0) best.first else null
    }

    private fun scoreCandidate(candidate: JioSaavnCandidate, query: TrackQuery): Double {
        var score = 0.0

        val cleanQueryTitle = cleanString(query.title).lowercase()
        val cleanCandidateTitle = cleanString(candidate.title).lowercase()

        // 1. Title matching
        if (cleanCandidateTitle == cleanQueryTitle) {
            score += 50.0
        } else if (cleanCandidateTitle.contains(cleanQueryTitle) || cleanQueryTitle.contains(cleanCandidateTitle)) {
            score += 35.0
        } else {
            val titleWordsMatch = cleanQueryTitle.split(" ").filter { it.length > 2 }
                .count { cleanCandidateTitle.contains(it) }
            score += titleWordsMatch * 8.0
        }

        // 2. Artist matching
        val queryArtist = cleanString(query.artist).lowercase()
        val candidateArtists = candidate.artists.map { cleanString(it).lowercase() }
        val subtitle = cleanString(candidate.subtitle.orEmpty()).lowercase()

        var artistMatchFound = false
        for (ca in candidateArtists) {
            if (ca.isNotBlank() && (queryArtist.contains(ca) || ca.contains(queryArtist))) {
                score += 30.0
                artistMatchFound = true
                break
            }
        }
        if (!artistMatchFound && subtitle.isNotBlank()) {
            val queryParts = queryArtist.split(",", ";", "&", "feat").map { it.trim() }
            if (queryParts.any { it.isNotBlank() && subtitle.contains(it) }) {
                score += 20.0
            }
        }

        // 3. Duration comparison (seconds)
        val queryDurSec = (query.durationMs ?: 0L) / 1000L
        if (candidate.durationSec > 0 && queryDurSec > 0) {
            val diff = abs(candidate.durationSec - queryDurSec)
            when {
                diff <= 3 -> score += 20.0
                diff <= 8 -> score += 15.0
                diff <= 15 -> score += 5.0
                diff > 30 -> score -= 25.0
            }
        }

        return score
    }

    private fun cleanString(str: String): String {
        return str
            .replace(Regex("""(?i)\(official\s*(music)?\s*(video|audio|lyric|lyrics)?\)"""), "")
            .replace(Regex("""(?i)\[official\s*(music)?\s*(video|audio|lyric|lyrics)?\]"""), "")
            .replace(Regex("""(?i)\(lyric\s*video\)"""), "")
            .replace(Regex("""(?i)\[lyric\s*video\]"""), "")
            .replace(Regex("""(?i)\(audio\)"""), "")
            .replace(Regex("""(?i)\[audio\]"""), "")
            .replace(Regex("""(?i)\(from\s+[^)]+\)"""), "")
            .replace(Regex("""(?i)\[from\s+[^]]+\]"""), "")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    companion object {
        const val TAG = "JioSaavnStreamResolver"
        const val ORIGIN = "jiosaavn"
        const val BASE_URL = "https://www.jiosaavn.com/api.php"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
