/*
 * Ozone Player (2026)
 * RadioBrowser API Client (50,000+ Internet Radio Directory)
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.radio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

object RadioBrowserClient {
    private const val TAG = "RadioBrowserClient"
    private const val USER_AGENT = "OzonePlayer/1.0 (Android Internet Radio)"

    private val SERVERS = listOf(
        "de1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "nl1.api.radio-browser.info",
        "fi1.api.radio-browser.info",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var activeServer: String? = null

    val CURATED_GENRES = listOf(
        RadioCategory("Popular", "popular"),
        RadioCategory("Bollywood", "bollywood"),
        RadioCategory("Malayalam", "malayalam"),
        RadioCategory("Lo-Fi", "lofi"),
        RadioCategory("Jazz", "jazz"),
        RadioCategory("Rock", "rock"),
        RadioCategory("Pop", "pop"),
        RadioCategory("Classical", "classical"),
        RadioCategory("Electronic", "electronic"),
        RadioCategory("Hip Hop", "hiphop"),
        RadioCategory("News & Talk", "news"),
        RadioCategory("Ambient", "ambient"),
        RadioCategory("Chillout", "chillout"),
        RadioCategory("Dance", "dance"),
        RadioCategory("80s Hits", "80s"),
        RadioCategory("90s Hits", "90s"),
    )

    val CURATED_COUNTRIES = listOf(
        "IN" to "India",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CA" to "Canada",
        "DE" to "Germany",
        "AU" to "Australia",
        "FR" to "France",
        "JP" to "Japan",
        "BR" to "Brazil",
    )

    private suspend fun pickServer(): String = withContext(Dispatchers.IO) {
        activeServer?.let { return@withContext it }
        for (host in SERVERS.shuffled()) {
            val ok = runCatching {
                val req = Request.Builder()
                    .url("https://$host/json/stats")
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) {
                activeServer = host
                return@withContext host
            }
        }
        // Fallback default
        SERVERS.first().also { activeServer = it }
    }

    private suspend fun buildUrl(path: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl {
        val host = activeServer ?: pickServer()
        return HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .apply {
                path.split('/').filter { it.isNotBlank() }.forEach { addPathSegment(it) }
            }
            .apply(query)
            .build()
    }

    private suspend fun executeGet(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                activeServer = null // Invalidate server so next attempt queries a different mirror
                throw IOException("RadioBrowser HTTP ${resp.code} for $url")
            }
            resp.body?.string().orEmpty()
        }
    }

    /**
     * Fetches top clicked / most popular radio stations worldwide.
     */
    suspend fun getPopular(limit: Int = 60): List<RadioStation> = runCatching {
        val url = buildUrl("json/stations/topclick/$limit") {
            addQueryParameter("hidebroken", "true")
        }
        parseStations(executeGet(url))
    }.onFailure { e ->
        Timber.tag(TAG).w(e, "Failed to fetch popular radio stations")
    }.getOrDefault(emptyList())

    /**
     * Searches radio stations by tag/genre (e.g. "jazz", "lofi", "bollywood").
     */
    suspend fun getByTag(tag: String, limit: Int = 60): List<RadioStation> = runCatching {
        val url = buildUrl("json/stations/bytagexact/$tag") {
            addQueryParameter("limit", limit.toString())
            addQueryParameter("hidebroken", "true")
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
        }
        parseStations(executeGet(url))
    }.onFailure { e ->
        Timber.tag(TAG).w(e, "Failed to fetch stations for tag %s", tag)
    }.getOrDefault(emptyList())

    /**
     * Searches radio stations by keyword / name query.
     */
    suspend fun searchStations(query: String, limit: Int = 60): List<RadioStation> = runCatching {
        if (query.isBlank()) return@runCatching getPopular(limit)
        val url = buildUrl("json/stations/search") {
            addQueryParameter("name", query.trim())
            addQueryParameter("limit", limit.toString())
            addQueryParameter("hidebroken", "true")
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
        }
        parseStations(executeGet(url))
    }.onFailure { e ->
        Timber.tag(TAG).w(e, "Failed to search radio stations for '%s'", query)
    }.getOrDefault(emptyList())

    /**
     * Fetches radio stations for a specific 2-letter ISO country code (e.g. "IN", "US").
     */
    suspend fun getByCountry(countryCode: String, limit: Int = 60): List<RadioStation> = runCatching {
        val url = buildUrl("json/stations/bycountrycodeexact/${countryCode.uppercase()}") {
            addQueryParameter("limit", limit.toString())
            addQueryParameter("hidebroken", "true")
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
        }
        parseStations(executeGet(url))
    }.onFailure { e ->
        Timber.tag(TAG).w(e, "Failed to fetch stations for country %s", countryCode)
    }.getOrDefault(emptyList())

    /**
     * Increments the click counter for the station in the open directory.
     */
    suspend fun reportStationClick(stationUuid: String) = withContext(Dispatchers.IO) {
        if (stationUuid.startsWith("custom-")) return@withContext
        runCatching {
            val url = buildUrl("json/url/$stationUuid")
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().close()
        }.onFailure { e ->
            Timber.tag(TAG).d("Click count report ignored: %s", e.message)
        }
    }

    internal fun parseStations(jsonBody: String): List<RadioStation> {
        val array = runCatching { json.parseToJsonElement(jsonBody) as? JsonArray }.getOrNull()
            ?: return emptyList()

        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val uuid = obj.str("stationuuid") ?: return@mapNotNull null
            val rawName = obj.str("name")?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null

            val stream = (obj.str("url_resolved")?.takeIf { it.isNotBlank() } ?: obj.str("url"))
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return@mapNotNull null

            RadioStation(
                uuid = uuid,
                name = rawName,
                streamUrl = stream,
                favicon = obj.str("favicon")?.takeIf { it.startsWith("http") },
                homepage = obj.str("homepage")?.takeIf { it.startsWith("http") },
                tags = obj.str("tags").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                country = obj.str("country"),
                countryCode = obj.str("countrycode"),
                codec = obj.str("codec"),
                bitrate = (obj["bitrate"] as? JsonPrimitive)?.intOrNull ?: 0,
                hls = (obj["hls"] as? JsonPrimitive)?.intOrNull == 1,
                state = obj.str("state")?.trim()?.takeIf { it.isNotEmpty() },
                latitude = obj.dbl("geo_lat"),
                longitude = obj.dbl("geo_long"),
            )
        }.distinctBy { it.uuid }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
}
