/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.musicrecognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface OverlayState {
    data object Idle : OverlayState
    data object RequestingPermission : OverlayState
    data object Listening : OverlayState
    data object Processing : OverlayState
    data class Success(val track: RecognizedTrack) : OverlayState
    data class Error(val message: String, val canRetry: Boolean = true) : OverlayState
}

@HiltViewModel
class OzoneSearchOverlayViewModel @Inject constructor(
    private val repository: MusicRecognitionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<OverlayState>(OverlayState.Idle)
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    private var recognitionJob: Job? = null

    fun onPermissionGranted() {
        startRecognition()
    }

    fun onPermissionDenied() {
        _state.value = OverlayState.Error(
            message = "Microphone permission is required to identify songs.",
            canRetry = true,
        )
    }

    fun startRecognition() {
        if (recognitionJob?.isActive == true) return
        recognitionJob = viewModelScope.launch {
            _state.value = OverlayState.Listening
            try {
                val samples = repository.captureAudio()
                if (samples.isEmpty()) {
                    _state.value = OverlayState.Error(
                        message = "Could not capture audio",
                        canRetry = true,
                    )
                    return@launch
                }

                _state.value = OverlayState.Processing
                val result = repository.recognize(samples)
                result.fold(
                    onSuccess = { track ->
                        _state.value = OverlayState.Success(track)
                        try {
                            repository.saveToHistory(track)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to save recognition history")
                        }
                    },
                    onFailure = { throwable ->
                        val msg = if (
                            throwable.message?.contains("No match", ignoreCase = true) == true ||
                            throwable.message?.contains("404") == true
                        ) {
                            "No match found"
                        } else {
                            "Identification failed, please try again"
                        }
                        _state.value = OverlayState.Error(
                            message = msg,
                            canRetry = true,
                        )
                    },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Overlay audio recognition failed")
                _state.value = OverlayState.Error(
                    message = "Failed to record audio",
                    canRetry = true,
                )
            }
        }
    }

    fun cancel() {
        recognitionJob?.cancel()
        recognitionJob = null
        _state.value = OverlayState.Idle
    }
}
