/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import javax.inject.Inject

sealed interface AddPlaylistPreviewState {
    data object Idle : AddPlaylistPreviewState
    data object Loading : AddPlaylistPreviewState
    data class Success(val playlist: ImportedPlaylist, val isDuplicate: Boolean) : AddPlaylistPreviewState
    data class Error(val message: String) : AddPlaylistPreviewState
}

sealed interface AddPlaylistImportState {
    data object Idle : AddPlaylistImportState
    data object Importing : AddPlaylistImportState
    data class Success(val playlist: PlaylistEntity) : AddPlaylistImportState
    data class DuplicatePrompt(val playlist: ImportedPlaylist) : AddPlaylistImportState
    data class Error(val message: String) : AddPlaylistImportState
}

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val importManager: PlaylistImportManager,
    private val importRepository: PlaylistImportRepository,
) : ViewModel() {

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _serviceType = MutableStateFlow(PlaylistServiceType.UNSUPPORTED)
    val serviceType: StateFlow<PlaylistServiceType> = _serviceType.asStateFlow()

    private val _previewState = MutableStateFlow<AddPlaylistPreviewState>(AddPlaylistPreviewState.Idle)
    val previewState: StateFlow<AddPlaylistPreviewState> = _previewState.asStateFlow()

    private val _importState = MutableStateFlow<AddPlaylistImportState>(AddPlaylistImportState.Idle)
    val importState: StateFlow<AddPlaylistImportState> = _importState.asStateFlow()

    private var autoFetchJob: Job? = null

    fun onUrlChanged(newUrl: String, triggerAutoFetch: Boolean = true) {
        _url.value = newUrl
        val detected = importManager.detectService(newUrl)
        _serviceType.value = detected

        if (detected == PlaylistServiceType.UNSUPPORTED) {
            _previewState.value = AddPlaylistPreviewState.Idle
            return
        }

        if (triggerAutoFetch) {
            autoFetchJob?.cancel()
            autoFetchJob = viewModelScope.launch {
                delay(600) // Debounce typing
                fetchPreview()
            }
        }
    }

    fun fetchPreview() {
        val currentUrl = _url.value.trim()
        if (currentUrl.isEmpty() || !importManager.isSupportedUrl(currentUrl)) {
            _previewState.value = AddPlaylistPreviewState.Idle
            return
        }

        autoFetchJob?.cancel()
        viewModelScope.launch {
            _previewState.value = AddPlaylistPreviewState.Loading
            importManager.fetchPlaylist(currentUrl).fold(
                onSuccess = { imported ->
                    val existing = importRepository.findExistingPlaylist(imported)
                    _previewState.value = AddPlaylistPreviewState.Success(
                        playlist = imported,
                        isDuplicate = existing != null,
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _previewState.value = AddPlaylistPreviewState.Error(
                        error.message ?: "Failed to fetch playlist details",
                    )
                },
            )
        }
    }

    fun onAddClicked(forceDuplicate: Boolean = false) {
        val currentPreview = _previewState.value
        val playlistToImport = when (currentPreview) {
            is AddPlaylistPreviewState.Success -> currentPreview.playlist
            else -> return
        }

        viewModelScope.launch {
            if (!forceDuplicate) {
                val existing = importRepository.findExistingPlaylist(playlistToImport)
                if (existing != null) {
                    _importState.value = AddPlaylistImportState.DuplicatePrompt(playlistToImport)
                    return@launch
                }
            }

            _importState.value = AddPlaylistImportState.Importing
            importRepository.importPlaylist(playlistToImport).fold(
                onSuccess = { savedEntity ->
                    _importState.value = AddPlaylistImportState.Success(savedEntity)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _importState.value = AddPlaylistImportState.Error(
                        error.message ?: "Failed to import playlist",
                    )
                },
            )
        }
    }

    fun dismissDuplicatePrompt() {
        _importState.value = AddPlaylistImportState.Idle
    }

    fun reset() {
        autoFetchJob?.cancel()
        _url.value = ""
        _serviceType.value = PlaylistServiceType.UNSUPPORTED
        _previewState.value = AddPlaylistPreviewState.Idle
        _importState.value = AddPlaylistImportState.Idle
    }
}
