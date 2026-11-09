/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.trending

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.constants.CountryCodeToName
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.lowdata.LowDataManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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
    val explicit: Boolean = false,
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

    suspend fun getYouTubeTrending(
        regionCode: String,
        forceRefresh: Boolean = false,
    ): List<TrendingTrack> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(cacheDir, "yt_chart_${regionCode}.json")
            val isLowData = LowDataManager.isLowDataModeActive.value
            val now = System.currentTimeMillis()

            val cached = readDiskCache(cacheFile)
            val isCacheValid = cached != null && (now - cached.timestamp <= CHART_CACHE_TTL_MS)

            // In low data mode, never auto-refresh if any cache exists unless pull-to-refresh
            if (!forceRefresh && cached != null && (isLowData || isCacheValid)) {
                return@withContext cached.tracks.map { it.toTrendingTrack() }
            }

            // Fetch from YouTube
            val freshTracks = fetchYouTubeTrendingFromNetwork(regionCode)
            if (freshTracks.isNotEmpty()) {
                writeDiskCache(cacheFile, freshTracks)
                return@withContext freshTracks
            }

            // Fallback to cached if available
            cached?.tracks?.map { it.toTrendingTrack() } ?: emptyList()
        }

    suspend fun getAppleTrending(
        regionCode: String,
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
                        artworkUrl = song.thumbnail,
                        durationSeconds = song.duration,
                        isApple = false,
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
                                explicit = it.explicit,
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
            explicit = explicit,
            youtubeSongItem =
                if (!isApple) {
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
