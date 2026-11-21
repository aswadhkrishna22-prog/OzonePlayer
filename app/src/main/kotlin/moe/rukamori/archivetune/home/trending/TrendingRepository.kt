/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.trending

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.constants.CountryCodeToName
import moe.rukamori.archivetune.innertube.SearchFilter
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.lowdata.LowDataManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
private data class DiskChartCache(
    val timestamp: Long,
    val tracks: List<CachedTrack>,
)

@Serializable
private data class CachedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSeconds: Int? = null,
    val isApple: Boolean = false,
    val isJioSaavn: Boolean = false,
    val explicit: Boolean = false,
    val encryptedMediaUrl: String? = null,
)

@Serializable
private data class AppleRssResponse(
    val feed: AppleRssFeed? = null,
)

@Serializable
private data class AppleRssFeed(
    val results: List<AppleRssSong>? = null,
)

@Serializable
private data class AppleRssSong(
    val id: String,
    val name: String,
    val artistName: String,
    val artworkUrl100: String? = null,
)

class TrendingRepository(
    private val context: Context,
    val matchCache: TrendingMatchCache = TrendingMatchCache(context),
) {
    val resolver = TrendingResolver(matchCache)

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

    private val cacheDir = File(context.cacheDir, "trending").apply { mkdirs() }

    companion object {
        const val CHART_CACHE_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours
        const val TOP_TRACKS_LIMIT = 15
    }

    fun resolveEffectiveRegion(trendingRegionSetting: String, contentCountrySetting: String): Pair<String, String> {
        val rawCode =
            if (trendingRegionSetting.isNotBlank() && trendingRegionSetting != "auto" && trendingRegionSetting != "system") {
                trendingRegionSetting
            } else if (contentCountrySetting.isNotBlank() && contentCountrySetting != "system") {
                contentCountrySetting
            } else {
                Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"
            }

        val regionCode = rawCode.uppercase(Locale.US)
        val displayName =
            CountryCodeToName[regionCode]
                ?: Locale("", regionCode).displayCountry.takeIf { it.isNotBlank() }
                ?: regionCode

        return regionCode to displayName
    }

    fun getCachedHeroTrending(regionCode: String): List<TrendingTrack> {
        val cacheFile = File(cacheDir, "hero_trending_${regionCode}.json")
        val fallbackCacheFile = File(cacheDir, "hero_trending_latest.json")
        val cached = readDiskCache(cacheFile) ?: readDiskCache(fallbackCacheFile)
        return cached?.tracks?.map { it.toTrendingTrack() }.orEmpty()
    }

    suspend fun getHeroTrending(
        regionCode: String,
        recentSongs: List<moe.rukamori.archivetune.db.entities.Song>,
        searchQueries: List<String>,
        forceRefresh: Boolean = false,
    ): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(cacheDir, "hero_trending_${regionCode}.json")
            val fallbackCacheFile = File(cacheDir, "hero_trending_latest.json")
            val isLowData = LowDataManager.isLowDataModeActive.value
            val now = System.currentTimeMillis()

            val cached = readDiskCache(cacheFile) ?: readDiskCache(fallbackCacheFile)
            val isCacheValid = cached != null && (now - cached.timestamp <= CHART_CACHE_TTL_MS)

            if (!forceRefresh && cached != null && (isLowData || isCacheValid)) {
                return@withContext cached.tracks.map { it.toTrendingTrack() }
            }

            // Separate trending source from YouTube Music charts and JioSaavn
            val ytDeferred = async { runCatching { getYouTubeTrending(regionCode, forceRefresh) }.getOrDefault(emptyList()) }
            val jioSaavnDeferred = async { runCatching { getJioSaavnTrending(regionCode, forceRefresh) }.getOrDefault(emptyList()) }

            val ytTracks = ytDeferred.await()
            val jioSaavnTracks = jioSaavnDeferred.await()

            // Merge both trending sources into a combined pool
            val combinedTracks = mutableListOf<TrendingTrack>()
            val seenKeys = mutableSetOf<String>()
            val maxLen = maxOf(ytTracks.size, jioSaavnTracks.size)
            for (i in 0 until maxLen) {
                if (i < ytTracks.size) {
                    val t = ytTracks[i]
                    val key = "${t.title.trim().lowercase(Locale.ROOT)}_${t.artist.trim().lowercase(Locale.ROOT)}"
                    if (seenKeys.add(key)) combinedTracks.add(t)
                }
                if (i < jioSaavnTracks.size) {
                    val t = jioSaavnTracks[i]
                    val key = "${t.title.trim().lowercase(Locale.ROOT)}_${t.artist.trim().lowercase(Locale.ROOT)}"
                    if (seenKeys.add(key)) combinedTracks.add(t)
                }
            }

            if (combinedTracks.isNotEmpty()) {
                val personalized = personalizeTracks(combinedTracks, recentSongs, searchQueries)
                writeDiskCache(cacheFile, personalized)
                writeDiskCache(fallbackCacheFile, personalized)
                return@withContext personalized
            }

            // Fallback to cached if available
            cached?.tracks?.map { it.toTrendingTrack() } ?: emptyList()
        }

    suspend fun getYouTubeTrending(
        regionCode: String,
        category: TrendingCategory = TrendingCategory.FOR_YOU,
        detectedLanguage: String? = null,
        forceRefresh: Boolean = false,
    ): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            val langSlug = if (category == TrendingCategory.FOR_YOU) detectedLanguage?.lowercase(Locale.US)?.takeIf { it.isNotBlank() } ?: "" else ""
            val cacheKey = "yt_${regionCode}_${category.id}${if (langSlug.isNotBlank()) "_$langSlug" else ""}.json"
            val cacheFile = File(cacheDir, cacheKey)
            val isLowData = LowDataManager.isLowDataModeActive.value
            val now = System.currentTimeMillis()

            val cached = readDiskCache(cacheFile)
            val isCacheValid = cached != null && (now - cached.timestamp <= CHART_CACHE_TTL_MS)

            // In low data mode, never auto-refresh if any cache exists unless pull-to-refresh
            if (!forceRefresh && cached != null && (isLowData || isCacheValid)) {
                return@withContext cached.tracks.map { it.toTrendingTrack() }
            }

            // Fetch from YouTube based on category
            val freshTracks = when (category) {
                TrendingCategory.GLOBAL -> fetchYouTubeTrendingFromNetwork(regionCode)
                TrendingCategory.FOR_YOU -> {
                    if (!detectedLanguage.isNullOrBlank()) {
                        val searchTracks = fetchYouTubeTrendingByQuery("$detectedLanguage trending songs")
                        if (searchTracks.isNotEmpty()) searchTracks else fetchYouTubeTrendingFromNetwork(regionCode)
                    } else {
                        fetchYouTubeTrendingFromNetwork(regionCode)
                    }
                }
                else -> {
                    val searchTracks = fetchYouTubeTrendingByQuery("${category.searchKeyword} trending songs")
                    if (searchTracks.isNotEmpty()) searchTracks else fetchYouTubeTrendingFromNetwork(regionCode)
                }
            }

            if (freshTracks.isNotEmpty()) {
                writeDiskCache(cacheFile, freshTracks)
                return@withContext freshTracks
            }

            // Fallback to cached if available
            cached?.tracks?.map { it.toTrendingTrack() } ?: emptyList()
        }

    suspend fun getAppleTrending(
        regionCode: String,
        category: TrendingCategory = TrendingCategory.FOR_YOU,
        forceRefresh: Boolean = false,
    ): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(cacheDir, "apple_chart_${regionCode}.json")
            val isLowData = LowDataManager.isLowDataModeActive.value
            val now = System.currentTimeMillis()

            val cached = readDiskCache(cacheFile)
            val isCacheValid = cached != null && (now - cached.timestamp <= CHART_CACHE_TTL_MS)

            if (!forceRefresh && cached != null && (isLowData || isCacheValid)) {
                return@withContext cached.tracks.map { it.toTrendingTrack() }
            }

            val freshTracks = fetchAppleTrendingFromNetwork(regionCode)
            if (freshTracks.isNotEmpty()) {
                writeDiskCache(cacheFile, freshTracks)
                return@withContext freshTracks
            }

            cached?.tracks?.map { it.toTrendingTrack() } ?: emptyList()
        }

    suspend fun getJioSaavnTrending(
        regionCode: String,
        category: TrendingCategory = TrendingCategory.FOR_YOU,
        detectedLanguage: String? = null,
        forceRefresh: Boolean = false,
    ): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            val langSlug = if (category == TrendingCategory.FOR_YOU) detectedLanguage?.lowercase(Locale.US)?.takeIf { it.isNotBlank() } ?: "" else ""
            val cacheKey = "jiosaavn_${regionCode}_${category.id}${if (langSlug.isNotBlank()) "_$langSlug" else ""}.json"
            val cacheFile = File(cacheDir, cacheKey)
            val isLowData = LowDataManager.isLowDataModeActive.value
            val now = System.currentTimeMillis()

            val cached = readDiskCache(cacheFile)
            val isCacheValid = cached != null && (now - cached.timestamp <= CHART_CACHE_TTL_MS)

            if (!forceRefresh && cached != null && (isLowData || isCacheValid)) {
                return@withContext cached.tracks.map { it.toTrendingTrack() }
            }

            val freshTracks = when (category) {
                TrendingCategory.GLOBAL -> fetchJioSaavnTrendingFromNetwork()
                TrendingCategory.FOR_YOU -> {
                    if (!detectedLanguage.isNullOrBlank()) {
                        fetchJioSaavnTrendingSearch("$detectedLanguage trending songs")
                    } else {
                        fetchJioSaavnTrendingFromNetwork()
                    }
                }
                else -> {
                    fetchJioSaavnTrendingSearch("${category.searchKeyword} trending songs")
                }
            }

            if (freshTracks.isNotEmpty()) {
                writeDiskCache(cacheFile, freshTracks)
                return@withContext freshTracks
            }

            cached?.tracks?.map { it.toTrendingTrack() } ?: emptyList()
        }

    fun normalizeToSquareHighResArtwork(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.contains("i.ytimg.com") || url.contains("img.youtube.com")) {
            return url
        }
        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            return if (url.contains("=")) {
                url.substringBeforeLast("=") + "=w1000-h1000-l90-rj"
            } else {
                "$url=w1000-h1000-l90-rj"
            }
        }
        if (url.contains("saavncdn.com") || url.contains("jiosaavn.com")) {
            return url.replace("150x150", "500x500")
                .replace("50x50", "500x500")
                .replace("250x250", "500x500")
        }
        if (url.contains("mzstatic.com")) {
            return url.replace(Regex("/\\d+x\\d+bb"), "/1000x1000bb")
        }
        return url
    }

    fun detectUserTasteLanguage(
        recentSongs: List<moe.rukamori.archivetune.db.entities.Song>,
        allArtists: List<moe.rukamori.archivetune.db.entities.Artist>,
        searchQueries: List<String>,
    ): String? {
        var malayalamScore = 0
        var tamilScore = 0
        var hindiScore = 0
        var englishScore = 0

        fun checkText(text: String, weight: Int = 3) {
            for (ch in text) {
                when (ch) {
                    in '\u0D00'..'\u0D7F' -> malayalamScore += weight
                    in '\u0B80'..'\u0BFF' -> tamilScore += weight
                    in '\u0900'..'\u097F' -> hindiScore += weight
                }
            }
        }

        recentSongs.forEach { song ->
            checkText(song.song.title, weight = 2)
            song.artists.forEach { checkText(it.name, weight = 3) }
        }
        allArtists.forEach { checkText(it.artist.name, weight = 3) }
        searchQueries.forEach { checkText(it, weight = 2) }

        val allTextTokens = buildList {
            addAll(recentSongs.map { it.song.title.lowercase(Locale.ROOT) })
            addAll(recentSongs.flatMap { it.artists }.map { it.name.lowercase(Locale.ROOT) })
            addAll(allArtists.map { it.artist.name.lowercase(Locale.ROOT) })
            addAll(searchQueries.map { it.lowercase(Locale.ROOT) })
        }

        val malayalamKeywords = listOf(
            "sushin", "hesham", "deepak dev", "shaan rahman", "yesudas", "chithra",
            "mg sreekumar", "vineeth", "sithara", "job kurian", "harisankar", "rex vijayan",
            "jassie gift", "vidyasagar", "mohanlal", "mammootty", "prithviraj", "malayalam",
            "manjari", "sujatha", "alphons", "gopi sundar", "bijibal", "sharreth",
            "stephen devassy", "dabzee", "fejo", "thirumaali", "neeraj madhav", "jakes bejoy",
            "christo xavier", "vishnu vijay", "sanjeev t", "sooraj santhosh", "kallara", "pranavam"
        )
        val tamilKeywords = listOf(
            "anirudh", "a.r. rahman", "ar rahman", "yuvan", "ilaiyaraaja", "ilayaraja",
            "harris jayaraj", "sid sriram", "dhanush", "santhosh narayanan", "d. imman",
            "gv prakash", "tamil", "vijay antony", "hiphop tamizha", "sam c.s.", "sean roldan",
            "pradeep kumar", "dhibu ninan", "shweta mohan", "kollywood"
        )
        val hindiKeywords = listOf(
            "arijit", "pritam", "badshah", "sachin-jigar", "shreya ghoshal", "vishal-shekhar",
            "neha kakkar", "jubin nautiyal", "hindi", "bollywood", "kumar sanu", "alka yagnik",
            "sonu nigam", "udit narayan", "atif aslam", "kk", "armaan malik", "darshan raval",
            "jasleen royal", "amit trivedi", "yo yo honey singh"
        )
        val englishKeywords = listOf(
            "taylor swift", "the weeknd", "ed sheeran", "billie eilish", "drake", "eminem",
            "coldplay", "linkin park", "imagine dragons", "dua lipa", "post malone", "bruno mars",
            "rock", "pop", "hip hop", "metal", "queen", "beatles", "metallica", "travis scott",
            "kanye", "maroon 5", "charlie puth", "shawn mendes", "justin bieber", "kendrick lamar",
            "rihanna", "adele", "lana del rey"
        )

        allTextTokens.forEach { token ->
            if (malayalamKeywords.any { token.contains(it) }) malayalamScore += 5
            if (tamilKeywords.any { token.contains(it) }) tamilScore += 5
            if (hindiKeywords.any { token.contains(it) }) hindiScore += 5
            if (englishKeywords.any { token.contains(it) }) englishScore += 4
        }

        val maxScore = maxOf(malayalamScore, tamilScore, hindiScore, englishScore)
        return if (maxScore >= 4) {
            when (maxScore) {
                malayalamScore -> "Malayalam"
                tamilScore -> "Tamil"
                hindiScore -> "Hindi"
                englishScore -> "English"
                else -> null
            }
        } else {
            null
        }
    }

    fun personalizeTracks(
        tracks: List<TrendingTrack>,
        recentSongs: List<moe.rukamori.archivetune.db.entities.Song>,
        searchQueries: List<String>,
        allArtists: List<moe.rukamori.archivetune.db.entities.Artist> = emptyList(),
    ): List<TrendingTrack> {
        if (tracks.isEmpty()) return emptyList()
        if (recentSongs.isEmpty() && searchQueries.isEmpty() && allArtists.isEmpty()) return tracks

        val recentArtists = recentSongs
            .flatMap { it.artists }
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()

        val topArtistNames = allArtists
            .map { it.artist.name.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()

        val recentTitleTokens = recentSongs
            .map { it.song.title.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()

        val cleanQueries = searchQueries
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 2 }
            .toSet()

        val scoredTracks = tracks.mapIndexed { index, track ->
            val trackArtist = track.artist.trim().lowercase(Locale.ROOT)
            val trackTitle = track.title.trim().lowercase(Locale.ROOT)

            // Base ranking preservation: higher trending songs start with slight edge
            val baseScore = 1.0 - (index.toDouble() / tracks.size.coerceAtLeast(1)) * 0.25

            var affinityBoost = 0.0

            // 1. Top artist match from library playtime
            if (topArtistNames.any { top -> trackArtist.contains(top) || top.contains(trackArtist) }) {
                affinityBoost += 0.80
            }

            // 2. Artist match from recent listening history
            if (recentArtists.any { recent -> trackArtist.contains(recent) || recent.contains(trackArtist) }) {
                affinityBoost += 0.50
            }

            // 3. Artist match from recent search history
            if (cleanQueries.any { query -> trackArtist.contains(query) || query.contains(trackArtist) }) {
                affinityBoost += 0.40
            }

            // 4. Title match from search history or recent songs
            if (cleanQueries.any { query -> trackTitle.contains(query) } || recentTitleTokens.contains(trackTitle)) {
                affinityBoost += 0.30
            }

            // 5. Regional language / genre keyword matches
            val languageTokens = listOf("malayalam", "hindi", "tamil", "punjabi", "telugu", "kannada", "english", "rock", "pop", "hip hop", "rap", "edm", "lofi")
            val userInterestedLanguages = languageTokens.filter { lang ->
                cleanQueries.any { it.contains(lang) }
            }
            if (userInterestedLanguages.any { lang -> trackTitle.contains(lang) || trackArtist.contains(lang) }) {
                affinityBoost += 0.25
            }

            track to (baseScore + affinityBoost)
        }

        return scoredTracks.sortedByDescending { it.second }.map { it.first }
    }

    private suspend fun fetchYouTubeTrendingFromNetwork(regionCode: String): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            runCatching {
                val chartsPageResult = YouTube.getChartsPage(countryCode = regionCode)
                val chartsPage = chartsPageResult.getOrNull() ?: return@runCatching emptyList()

                // Strategy 1: Look for direct SongItems in sections
                val songItems = mutableListOf<SongItem>()

                val trendingOrTopSection =
                    chartsPage.sections.firstOrNull {
                        it.chartType == ChartsPage.ChartType.TRENDING || it.chartType == ChartsPage.ChartType.TOP
                    } ?: chartsPage.sections.firstOrNull()

                if (trendingOrTopSection != null) {
                    val directSongs = trendingOrTopSection.items.filterIsInstance<SongItem>()
                    if (directSongs.isNotEmpty()) {
                        songItems.addAll(directSongs)
                    } else {
                        // Strategy 2: If items are playlists (e.g. Trending 20 India), fetch the playlist
                        val playlistItem =
                            trendingOrTopSection.items.filterIsInstance<PlaylistItem>().firstOrNull {
                                it.title.contains("Trending", ignoreCase = true) ||
                                    it.title.contains("Top", ignoreCase = true)
                            } ?: trendingOrTopSection.items.filterIsInstance<PlaylistItem>().firstOrNull()

                        if (playlistItem != null) {
                            val playlistResult = YouTube.playlist(playlistItem.id)
                            val playlistPage = playlistResult.getOrNull()
                            if (playlistPage != null && playlistPage.songs.isNotEmpty()) {
                                songItems.addAll(playlistPage.songs)
                            }
                        }
                    }
                }

                // If still empty, check all sections for any SongItems
                if (songItems.isEmpty()) {
                    for (section in chartsPage.sections) {
                        val songs = section.items.filterIsInstance<SongItem>()
                        if (songs.isNotEmpty()) {
                            songItems.addAll(songs)
                            break
                        }
                    }
                }

                songItems.take(TOP_TRACKS_LIMIT).map { song ->
                    TrendingTrack(
                        id = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        artworkUrl = normalizeToSquareHighResArtwork(song.thumbnail),
                        durationSeconds = song.duration,
                        isApple = false,
                        isJioSaavn = false,
                        explicit = song.explicit,
                        youtubeSongItem = song,
                    )
                }
            }.getOrDefault(emptyList())
        }

    private suspend fun fetchAppleTrendingFromNetwork(regionCode: String): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(5000L) {
                runCatching {
                    val storefront = regionCode.lowercase(Locale.US)
                    val url = "https://rss.marketingtools.apple.com/api/v2/$storefront/music/most-played/15/songs.json"
                    val request =
                        Request.Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .header("User-Agent", "OzonePlayer/1.0")
                            .build()

                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) return@runCatching emptyList()

                    val body = response.body?.string() ?: return@runCatching emptyList()
                    val parsed = json.decodeFromString<AppleRssResponse>(body)
                    val results = parsed.feed?.results.orEmpty()
                    val isLowData = LowDataManager.isLowDataModeActive.value

                    results.take(TOP_TRACKS_LIMIT).map { song ->
                        val artwork =
                            if (isLowData) {
                                song.artworkUrl100
                            } else {
                                song.artworkUrl100?.replace("/100x100bb.jpg", "/600x600bb.jpg")
                            }

                        TrendingTrack(
                            id = "apple_${song.id}",
                            title = song.name,
                            artist = song.artistName,
                            artworkUrl = artwork,
                            durationSeconds = null,
                            isApple = true,
                            explicit = false,
                        )
                    }
                }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    private suspend fun fetchJioSaavnTrendingFromNetwork(): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(6000L) {
                runCatching {
                    val chartSongs = fetchJioSaavnChartPlaylist()
                    if (chartSongs.isNotEmpty()) {
                        return@runCatching chartSongs
                    }
                    fetchJioSaavnTrendingSearch()
                }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    private fun fetchJioSaavnChartPlaylist(): List<TrendingTrack> {
        return try {
            val chartUrl = "https://www.jiosaavn.com/api.php?__call=content.getCharts&_format=json&_marker=0&api_version=4&ctx=web6dot0"
            val req = Request.Builder().url(chartUrl).header("User-Agent", "Mozilla/5.0").header("Accept", "application/json").build()
            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) return emptyList()
            val body = res.body?.string().orEmpty()
            val root = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
            val firstChartId = (root.firstOrNull() as? JsonObject)?.get("id")?.toString()?.replace("\"", "").orEmpty()
            if (firstChartId.isBlank()) return emptyList()

            val detailsUrl = "https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0&listid=$firstChartId"
            val detailsReq = Request.Builder().url(detailsUrl).header("User-Agent", "Mozilla/5.0").header("Accept", "application/json").build()
            val detailsRes = httpClient.newCall(detailsReq).execute()
            if (!detailsRes.isSuccessful) return emptyList()
            val detailsBody = detailsRes.body?.string().orEmpty()
            val detailsRoot = json.parseToJsonElement(detailsBody) as? JsonObject ?: return emptyList()
            val songsArray = detailsRoot["songs"] as? JsonArray ?: return emptyList()

            parseJioSaavnSongsJsonArray(songsArray)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchYouTubeTrendingByQuery(query: String): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            runCatching {
                val searchResult = YouTube.search(query = query, filter = SearchFilter.FILTER_SONG).getOrNull()
                val songItems = searchResult?.items?.filterIsInstance<SongItem>().orEmpty()
                songItems.take(TOP_TRACKS_LIMIT).map { song ->
                    TrendingTrack(
                        id = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        artworkUrl = normalizeToSquareHighResArtwork(song.thumbnail),
                        durationSeconds = song.duration,
                        isApple = false,
                        isJioSaavn = false,
                        explicit = song.explicit,
                        youtubeSongItem = song,
                    )
                }
            }.getOrDefault(emptyList())
        }

    private fun fetchJioSaavnTrendingSearch(query: String = "trending songs"): List<TrendingTrack> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=20&p=1&q=$encodedQuery"
            val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").header("Accept", "application/json").build()
            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) return emptyList()
            val body = res.body?.string().orEmpty()
            val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
            val resultsArray = root["results"] as? JsonArray ?: return emptyList()

            parseJioSaavnSongsJsonArray(resultsArray)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJioSaavnSongsJsonArray(array: JsonArray): List<TrendingTrack> {
        return array.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.toString()?.replace("\"", "").orEmpty()
            val rawTitle = obj["title"]?.toString()?.replace("\"", "") ?: obj["song"]?.toString()?.replace("\"", "").orEmpty()
            val title = unescapeHtml(rawTitle)
            val moreInfo = obj["more_info"] as? JsonObject
            val rawArtist = obj["subtitle"]?.toString()?.replace("\"", "") ?: moreInfo?.get("music")?.toString()?.replace("\"", "").orEmpty()
            val artist = unescapeHtml(rawArtist).ifBlank { "Unknown Artist" }
            val rawImage = obj["image"]?.toString()?.replace("\"", "")
            val image = normalizeToSquareHighResArtwork(rawImage)
            val duration = moreInfo?.get("duration")?.toString()?.replace("\"", "")?.toIntOrNull()
            val encUrl = moreInfo?.get("encrypted_media_url")?.toString()?.replace("\"", "")

            if (id.isNotBlank() && title.isNotBlank()) {
                TrendingTrack(
                    id = "jiosaavn_$id",
                    title = title,
                    artist = artist,
                    artworkUrl = image,
                    durationSeconds = duration,
                    isApple = false,
                    isJioSaavn = true,
                    explicit = false,
                    encryptedMediaUrl = encUrl,
                )
            } else null
        }.take(TOP_TRACKS_LIMIT)
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun readDiskCache(cacheFile: File): DiskChartCache? =
        runCatching {
            if (!cacheFile.exists()) return null
            val text = cacheFile.readText()
            json.decodeFromString<DiskChartCache>(text)
        }.getOrNull()

    private fun writeDiskCache(cacheFile: File, tracks: List<TrendingTrack>) {
        runCatching {
            val payload =
                DiskChartCache(
                    timestamp = System.currentTimeMillis(),
                    tracks =
                        tracks.map {
                            CachedTrack(
                                id = it.id,
                                title = it.title,
                                artist = it.artist,
                                artworkUrl = it.artworkUrl,
                                durationSeconds = it.durationSeconds,
                                isApple = it.isApple,
                                isJioSaavn = it.isJioSaavn,
                                explicit = it.explicit,
                                encryptedMediaUrl = it.encryptedMediaUrl,
                            )
                        },
                )
            cacheFile.writeText(json.encodeToString(payload))
        }
    }

    private fun CachedTrack.toTrendingTrack(): TrendingTrack =
        TrendingTrack(
            id = id,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            durationSeconds = durationSeconds,
            isApple = isApple,
            isJioSaavn = isJioSaavn,
            explicit = explicit,
            encryptedMediaUrl = encryptedMediaUrl,
            youtubeSongItem =
                if (!isApple && !isJioSaavn) {
                    SongItem(
                        id = id,
                        title = title,
                        artists = listOf(moe.rukamori.archivetune.innertube.models.Artist(name = artist, id = null)),
                        thumbnail = artworkUrl ?: "",
                        explicit = explicit,
                        duration = durationSeconds,
                        endpoint = moe.rukamori.archivetune.innertube.models.WatchEndpoint(videoId = id),
                    )
                } else {
                    null
                },
        )
}
