/*
 * Ozone Player (2026)
 * Radio Repository (Persistence of Favorites, Recents, and Custom Streams)
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.radio

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID

object RadioRepository {
    private const val TAG = "RadioRepository"
    private const val FILE_NAME = "radio_data_v1.json"
    private const val MAX_RECENT = 50

    @Serializable
    private data class SavedRadioState(
        val favorites: List<RadioStation> = emptyList(),
        val recents: List<RadioStation> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var storageFile: File? = null

    private val _favoritesFlow = MutableStateFlow<List<RadioStation>>(emptyList())
    val favoritesFlow: StateFlow<List<RadioStation>> = _favoritesFlow.asStateFlow()

    private val _recentsFlow = MutableStateFlow<List<RadioStation>>(emptyList())
    val recentsFlow: StateFlow<List<RadioStation>> = _recentsFlow.asStateFlow()

    fun init(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        storageFile = file

        scope.launch {
            loadFromDisk(file)
        }
    }

    private suspend fun loadFromDisk(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext
        runCatching {
            val content = file.readText()
            val saved = json.decodeFromString<SavedRadioState>(content)
            _favoritesFlow.value = saved.favorites
            _recentsFlow.value = saved.recents
            Timber.tag(TAG).d("Loaded %d radio favorites and %d recents", saved.favorites.size, saved.recents.size)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to load radio data from disk")
        }
    }

    fun isFavorite(uuid: String): Boolean {
        return _favoritesFlow.value.any { it.uuid == uuid }
    }

    fun toggleFavorite(station: RadioStation) {
        val current = _favoritesFlow.value
        val isFav = current.any { it.uuid == station.uuid }
        _favoritesFlow.value = if (isFav) {
            current.filterNot { it.uuid == station.uuid }
        } else {
            listOf(station) + current.filterNot { it.uuid == station.uuid }
        }
        persist()
    }

    fun recordPlayed(station: RadioStation) {
        val current = _recentsFlow.value
        _recentsFlow.value = (listOf(station) + current.filterNot { it.uuid == station.uuid }).take(MAX_RECENT)
        persist()

        if (!station.isCustom) {
            scope.launch {
                RadioBrowserClient.reportStationClick(station.uuid)
            }
        }
    }

    fun addCustomStation(name: String, streamUrl: String): RadioStation {
        val trimmedUrl = streamUrl.trim()
        val station = RadioStation(
            uuid = "custom-${UUID.randomUUID()}",
            name = name.trim().ifEmpty { "Custom Radio" },
            streamUrl = trimmedUrl,
            hls = trimmedUrl.contains(".m3u8", ignoreCase = true),
            codec = if (trimmedUrl.contains(".m3u8", ignoreCase = true)) "HLS" else "MP3/AAC",
        )
        toggleFavorite(station)
        return station
    }

    private fun persist() {
        val file = storageFile ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val saved = SavedRadioState(
                        favorites = _favoritesFlow.value,
                        recents = _recentsFlow.value,
                    )
                    val text = json.encodeToString(saved)
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(text)
                    tmp.renameTo(file)
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to persist radio data")
                }
            }
        }
    }
}
