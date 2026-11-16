/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.UseSpotifySearchKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import moe.rukamori.archivetune.models.ItemsPage
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.models.SpotifySearchResult
import moe.rukamori.archivetune.spotify.sync.SpotifyTokenManager
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.SearchSlideTrigger
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnCandidate
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnStreamResolver
import javax.inject.Inject

enum class OnlineSearchSource {
    YOUTUBE,
    SPOTIFY,
    JIOSAAVN,
}

enum class SpotifySearchFilter {
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
}

enum class OnlineSearchSort {
    DEFAULT,
    VIEWS,
}

@HiltViewModel
class OnlineSearchViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
        val jioSaavnResolver: JioSaavnStreamResolver,
    ) : ViewModel() {
        val query =
            decodeOnlineSearchQuery(
                savedStateHandle.get<String>(OnlineSearchResultArgument).orEmpty(),
            )
        val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
        val sort = MutableStateFlow(OnlineSearchSort.DEFAULT)
        val searchSource = MutableStateFlow(OnlineSearchSource.YOUTUBE)
        val spotifyFilter = MutableStateFlow<SpotifySearchFilter?>(null)
        var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
        var spotifyResult by mutableStateOf<SpotifySearchResult?>(null)
        var isSpotifyLoading by mutableStateOf(false)
        var spotifyError by mutableStateOf<Int?>(null)
        var jioSaavnResult by mutableStateOf<List<JioSaavnCandidate>?>(null)
        var isJioSaavnLoading by mutableStateOf(false)
        var jioSaavnError by mutableStateOf<Int?>(null)
        val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

        private var searchRequestCounter: Int
            get() = savedStateHandle.get<Int>(KEY_SEARCH_REQUEST_COUNTER) ?: 1
            set(value) {
                savedStateHandle[KEY_SEARCH_REQUEST_COUNTER] = value
            }

        val searchRequestId: String
            get() = "req_${query}_${searchSource.value.name}_$searchRequestCounter"

        private val playedAnimationKeys: MutableSet<String> =
            (savedStateHandle.get<ArrayList<String>>(KEY_PLAYED_ANIMATION_KEYS)
                ?: savedStateHandle.get<List<String>>(KEY_PLAYED_ANIMATION_KEYS)
                ?: emptyList()).toMutableSet()

        fun isAnimationPlayed(key: String): Boolean = playedAnimationKeys.contains(key)

        fun markAnimationPlayed(key: String) {
            if (playedAnimationKeys.add(key)) {
                savedStateHandle[KEY_PLAYED_ANIMATION_KEYS] = ArrayList(playedAnimationKeys)
            }
        }

        private val allModeFilters =
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            )
        private var isSummaryLoading = false
        private val loadingFilters = mutableSetOf<String>()
        private var searchJob: kotlinx.coroutines.Job? = null

        companion object {
            private const val KEY_SEARCH_REQUEST_COUNTER = "search_request_counter"
            private const val KEY_PLAYED_ANIMATION_KEYS = "played_search_slide_keys"
            private val summaryCache = LruCache<String, SearchSummaryPage>(50)
            private val filterCache = LruCache<String, ItemsPage>(100)
            private val spotifyCache = LruCache<String, SpotifySearchResult>(50)
            private val jioSaavnCache = LruCache<String, List<JioSaavnCandidate>>(50)
        }

        init {
            viewModelScope.launch {
                val useSpotify = context.dataStore.get(UseSpotifySearchKey, false)
                if (useSpotify) {
                    searchSource.value = OnlineSearchSource.SPOTIFY
                    loadSpotifySearchIfNeeded()
                }
            }

            viewModelScope.launch {
                searchSource.collect { source ->
                    if (source == OnlineSearchSource.SPOTIFY) {
                        loadSpotifySearchIfNeeded()
                    } else if (source == OnlineSearchSource.JIOSAAVN) {
                        loadJioSaavnSearchIfNeeded()
                    }
                }
            }

            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                filter.collect { selectedFilter ->
                    if (selectedFilter == null) {
                        viewModelScope.launch {
                            loadSummaryIfNeeded()
                        }
                        allModeFilters.forEach { allModeFilter ->
                            viewModelScope.launch {
                                loadFilterIfNeeded(allModeFilter)
                            }
                        }
                    } else {
                        loadFilterIfNeeded(selectedFilter)
                    }
                }
            }
        }

        private suspend fun loadSummaryIfNeeded() {
            if (summaryPage != null || isSummaryLoading) return

            summaryCache.get(query)?.let { cached ->
                summaryPage = cached
                return
            }

            isSummaryLoading = true
            try {
                YouTube
                    .searchSummary(query)
                    .onSuccess {
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val contentFilteredPage =
                            it
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(context.dataStore.get(HideVideoKey, false))
                        val processed =
                            contentFilteredPage.copy(
                                summaries =
                                    contentFilteredPage.summaries.mapNotNull { summary ->
                                        summary
                                            .copy(items = filterAiContent(summary.items, aiContentFilterPolicy))
                                            .takeIf { filteredSummary -> filteredSummary.items.isNotEmpty() }
                                    },
                            )
                        summaryPage = processed
                        summaryCache.put(query, processed)
                    }.onFailure {
                        reportException(it)
                    }
            } finally {
                isSummaryLoading = false
            }
        }

        private suspend fun loadFilterIfNeeded(filter: YouTube.SearchFilter) {
            val filterKey = filter.value
            if (viewStateMap.containsKey(filterKey) || !loadingFilters.add(filterKey)) return

            val cacheKey = "$query:$filterKey"
            filterCache.get(cacheKey)?.let { cached ->
                viewStateMap[filterKey] = cached
                loadingFilters.remove(filterKey)
                return
            }

            try {
                YouTube
                    .search(query, filter)
                    .onSuccess { result ->
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val page =
                            ItemsPage(
                                filterAiContent(
                                    result.items
                                        .distinctBy { it.id }
                                        .filterExplicit(
                                            context.dataStore.get(
                                                HideExplicitKey,
                                                false,
                                            ),
                                        ).filterVideo(context.dataStore.get(HideVideoKey, false)),
                                    aiContentFilterPolicy,
                                ),
                                result.continuation,
                            )
                        viewStateMap[filterKey] = page
                        filterCache.put(cacheKey, page)
                    }.onFailure {
                        reportException(it)
                    }
            } finally {
                loadingFilters.remove(filterKey)
            }
        }

        fun loadMore() {
            if (moe.rukamori.archivetune.lowdata.LowDataManager.isLowDataModeActive.value) {
                timber.log.Timber.tag("OnlineSearch").d("Low data mode: skipping pagination (first page only)")
                return
            }
            val filter = filter.value?.value
            viewModelScope.launch {
                if (filter == null) return@launch
                val viewState = viewStateMap[filter] ?: return@launch
                val continuation = viewState.continuation
                if (continuation != null) {
                    val searchResult =
                        YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                    val aiContentFilterPolicy = loadAiContentFilterPolicy()
                    val continuedItems =
                        filterAiContent(
                            searchResult.items
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(context.dataStore.get(HideVideoKey, false)),
                            aiContentFilterPolicy,
                        )
                    viewStateMap[filter] =
                        ItemsPage(
                            (viewState.items + continuedItems).distinctBy { it.id },
                            searchResult.continuation,
                        )
                }
            }
        }

        fun updateSort(sort: OnlineSearchSort) {
            this.sort.value = sort
        }

        fun sortedItems(
            items: List<YTItem>,
            sort: OnlineSearchSort = this.sort.value,
        ): List<YTItem> =
            when (sort) {
                OnlineSearchSort.DEFAULT -> {
                    items
                }

                OnlineSearchSort.VIEWS -> {
                    items
                        .withIndex()
                        .sortedWith(
                            compareByDescending<IndexedValue<YTItem>> {
                                (it.value as? SongItem)?.viewCount ?: Long.MIN_VALUE
                            }.thenBy { it.index },
                        ).map { it.value }
                }
            }

        fun setSearchSource(source: OnlineSearchSource) {
            if (searchSource.value != source) {
                val oldSource = searchSource.value
                searchSource.value = source
                if (SearchSlideTrigger.shouldTriggerOnSourceChange(oldSource, source)) {
                    searchRequestCounter++
                }
                viewModelScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[UseSpotifySearchKey] = (source == OnlineSearchSource.SPOTIFY)
                    }
                }
                if (source == OnlineSearchSource.SPOTIFY) {
                    viewModelScope.launch {
                        loadSpotifySearchIfNeeded()
                    }
                } else if (source == OnlineSearchSource.JIOSAAVN) {
                    viewModelScope.launch {
                        loadJioSaavnSearchIfNeeded()
                    }
                }
            }
        }

        fun setSpotifyFilter(filter: SpotifySearchFilter?) {
            if (SearchSlideTrigger.shouldTriggerOnCategoryFilterChange()) {
                searchRequestCounter++
            }
            spotifyFilter.value = filter
        }

        fun retrySpotify() {
            viewModelScope.launch {
                loadSpotifySearchIfNeeded(force = true)
            }
        }

        suspend fun loadSpotifySearchIfNeeded(force: Boolean = false) {
            if (query.isBlank()) return
            if (!force && (spotifyResult != null || isSpotifyLoading)) return

            if (!force) {
                spotifyCache.get(query)?.let { cached ->
                    spotifyResult = cached
                    return
                }
            }

            isSpotifyLoading = true
            spotifyError = null
            try {
                val hasToken = SpotifyTokenManager.ensureToken(context)
                if (!hasToken) {
                    spotifyError = R.string.spotify_not_connected
                    return
                }

                val limit = if (moe.rukamori.archivetune.lowdata.LowDataManager.isLowDataModeActive.value) 10 else 25
                Spotify.search(
                    query = query,
                    types = listOf("track", "album", "artist", "playlist"),
                    limit = limit,
                ).onSuccess { res ->
                    spotifyResult = res
                    spotifyCache.put(query, res)
                }.onFailure { err ->
                    if (err is Spotify.SpotifyException && err.statusCode == 401) {
                        spotifyError = R.string.spotify_not_connected
                    } else {
                        reportException(err)
                        spotifyError = R.string.error_unknown
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                spotifyError = R.string.error_unknown
            } finally {
                isSpotifyLoading = false
            }
        }

        fun retryJioSaavn() {
            viewModelScope.launch {
                loadJioSaavnSearchIfNeeded(force = true)
            }
        }

        suspend fun loadJioSaavnSearchIfNeeded(force: Boolean = false) {
            if (query.isBlank()) return
            if (!force && (jioSaavnResult != null || isJioSaavnLoading)) return

            if (!force) {
                jioSaavnCache.get(query)?.let { cached ->
                    jioSaavnResult = cached
                    return
                }
            }

            isJioSaavnLoading = true
            jioSaavnError = null
            try {
                val results = jioSaavnResolver.searchSongs(query, limit = 30)
                jioSaavnResult = results
                jioSaavnCache.put(query, results)
            } catch (e: Exception) {
                reportException(e)
                jioSaavnError = R.string.error_unknown
            } finally {
                isJioSaavnLoading = false
            }
        }
    }
