/*
 * Ozone Player (2026) | Low Data Mode & Auto Slow Network Detection
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.lowdata

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.AutoLowDataModeKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.network.NetworkBannerUiState
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue

// ── TUNEABLE CONSTANTS ─────────────────────────────────────────────────────────
const val SLOW_NETWORK_THROUGHPUT_BPS = 150_000L      // 150 kbps
const val SLOW_NETWORK_ACTIVE_LOAD_MS = 15_000L       // 15 seconds of active loading
const val REBUFFERING_STALL_WINDOW_MS = 90_000L       // 90 seconds window
const val REBUFFERING_STALL_THRESHOLD = 2             // 2 or more stalls
const val RECOVERED_THROUGHPUT_BPS = 800_000L         // 800 kbps
const val RECOVERED_DURATION_MS = 60_000L             // 60 seconds of recovery throughput
const val ASK_ME_SNOOZE_MS = 15 * 60 * 1000L          // 15 minutes snooze
const val MIN_THROUGHPUT_SAMPLE_BYTES = 20 * 1024L    // 20 KB minimum response to measure
const val DEBOUNCE_STATE_CHANGE_MS = 5_000L           // 5 seconds debounce between transitions

enum class AutoLowDataMode {
    OFF,
    AUTOMATIC,
    ASK_ME,
}

data class TransferSample(
    val timestamp: Long,
    val bytes: Long,
    val durationMs: Long,
)

object LowDataManager {
    private const val TAG = "LowDataManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _manualLowDataMode = MutableStateFlow(false)
    val manualLowDataMode: StateFlow<Boolean> = _manualLowDataMode.asStateFlow()

    private val _autoLowDataMode = MutableStateFlow(AutoLowDataMode.OFF)
    val autoLowDataMode: StateFlow<AutoLowDataMode> = _autoLowDataMode.asStateFlow()

    private val _isAutoTurnedOn = MutableStateFlow(false)
    val isAutoTurnedOn: StateFlow<Boolean> = _isAutoTurnedOn.asStateFlow()

    private val _isLowDataModeActive = MutableStateFlow(false)
    val isLowDataModeActive: StateFlow<Boolean> = _isLowDataModeActive.asStateFlow()

    private val _isAudioBufferLow = MutableStateFlow(false)
    val isAudioBufferLow: StateFlow<Boolean> = _isAudioBufferLow.asStateFlow()

    private val _showAskMeDialog = MutableStateFlow(false)
    val showAskMeDialog: StateFlow<Boolean> = _showAskMeDialog.asStateFlow()

    private val _bannerEvent = MutableSharedFlow<NetworkBannerUiState>(extraBufferCapacity = 1)
    val bannerEvent: SharedFlow<NetworkBannerUiState> = _bannerEvent.asSharedFlow()

    // ── Internal Detection State ───────────────────────────────────────────────
    private val transferSamples = ConcurrentLinkedQueue<TransferSample>()
    private val bufferingStalls = ConcurrentLinkedQueue<Long>()
    @Volatile private var lastStateTransitionTime = 0L
    @Volatile private var lastAskMeDismissTime = 0L
    @Volatile private var lastHighSpeedStartTime = 0L
    @Volatile private var isInitialized = false
    @Volatile var isNetworkOnline = true

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            appContext.dataStore.data
                .map { prefs -> prefs[LowDataModeKey] ?: false }
                .distinctUntilChanged()
                .collect { manual ->
                    _manualLowDataMode.value = manual
                    updateCombinedState()
                }
        }

        scope.launch(Dispatchers.IO) {
            appContext.dataStore.data
                .map { prefs ->
                    prefs[AutoLowDataModeKey]?.toEnum(AutoLowDataMode.OFF) ?: AutoLowDataMode.OFF
                }
                .distinctUntilChanged()
                .collect { autoMode ->
                    _autoLowDataMode.value = autoMode
                    if (autoMode == AutoLowDataMode.OFF && _isAutoTurnedOn.value) {
                        _isAutoTurnedOn.value = false
                        updateCombinedState()
                    }
                }
        }
    }

    private fun updateCombinedState() {
        val active = _manualLowDataMode.value || _isAutoTurnedOn.value
        if (_isLowDataModeActive.value != active) {
            _isLowDataModeActive.value = active
            Timber.tag(TAG).i("isLowDataModeActive updated to: %s (manual=%s, auto=%s)", active, _manualLowDataMode.value, _isAutoTurnedOn.value)
        }
    }

    fun setManualLowDataMode(context: Context, enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { prefs ->
                prefs[LowDataModeKey] = enabled
            }
        }
        // If manually switched off, also cancel any auto-activation
        if (!enabled && _isAutoTurnedOn.value) {
            _isAutoTurnedOn.value = false
            updateCombinedState()
        }
    }

    fun setAutoLowDataMode(context: Context, mode: AutoLowDataMode) {
        scope.launch(Dispatchers.IO) {
            context.applicationContext.dataStore.edit { prefs ->
                prefs[AutoLowDataModeKey] = mode.name
            }
        }
    }

    fun setAudioBufferLow(isLow: Boolean) {
        _isAudioBufferLow.value = isLow
    }

    // ── Measured Throughput Reporting ─────────────────────────────────────────

    fun reportThroughputSample(bytes: Long, durationMs: Long) {
        if (bytes < MIN_THROUGHPUT_SAMPLE_BYTES || durationMs <= 0L) return
        val now = SystemClock.elapsedRealtime()
        transferSamples.add(TransferSample(now, bytes, durationMs))
        pruneTransferSamples(now)
        checkNetworkConditions(now)
    }

    private fun pruneTransferSamples(now: Long) {
        while (true) {
            val oldest = transferSamples.peek() ?: break
            if (now - oldest.timestamp > RECOVERED_DURATION_MS) {
                transferSamples.poll()
            } else {
                break
            }
        }
    }

    // ── Rebuffering Reporting ─────────────────────────────────────────────────

    fun reportBufferingStall() {
        val now = SystemClock.elapsedRealtime()
        bufferingStalls.add(now)
        pruneBufferingStalls(now)
        Timber.tag(TAG).d("Buffering stall reported (recent stalls=%d)", bufferingStalls.size)
        checkNetworkConditions(now)
    }

    private fun pruneBufferingStalls(now: Long) {
        while (true) {
            val oldest = bufferingStalls.peek() ?: break
            if (now - oldest > REBUFFERING_STALL_WINDOW_MS) {
                bufferingStalls.poll()
            } else {
                break
            }
        }
    }

    // ── Condition Evaluation ──────────────────────────────────────────────────

    private fun checkNetworkConditions(now: Long) {
        // Do not treat "no connection" or airplane mode as slow.
        if (!isNetworkOnline) return
        if (now - lastStateTransitionTime < DEBOUNCE_STATE_CHANGE_MS) return

        val autoMode = _autoLowDataMode.value
        if (autoMode == AutoLowDataMode.OFF) return

        val recentStalls = bufferingStalls.size
        val hasStalls = recentStalls >= REBUFFERING_STALL_THRESHOLD

        var totalBytes = 0L
        var totalDurationMs = 0L
        for (sample in transferSamples) {
            totalBytes += sample.bytes
            totalDurationMs += sample.durationMs
        }

        val throughputBps = if (totalDurationMs > 0L) (totalBytes * 8000L) / totalDurationMs else 0L
        val hasActiveLoad = totalDurationMs >= SLOW_NETWORK_ACTIVE_LOAD_MS
        val isSlowThroughput = hasActiveLoad && throughputBps < SLOW_NETWORK_THROUGHPUT_BPS

        val isSlow = hasStalls || isSlowThroughput

        if (isSlow) {
            lastHighSpeedStartTime = 0L
            onSlowNetworkDetected(now)
        } else if (_isAutoTurnedOn.value && !_manualLowDataMode.value) {
            // Check recovery: above RECOVERED_THROUGHPUT_BPS for RECOVERED_DURATION_MS
            if (throughputBps >= RECOVERED_THROUGHPUT_BPS && totalDurationMs >= 10_000L) {
                if (lastHighSpeedStartTime == 0L) {
                    lastHighSpeedStartTime = now
                } else if (now - lastHighSpeedStartTime >= RECOVERED_DURATION_MS) {
                    onNetworkRecovered(now)
                }
            } else {
                lastHighSpeedStartTime = 0L
            }
        }
    }

    private fun onSlowNetworkDetected(now: Long) {
        val autoMode = _autoLowDataMode.value
        if (_isLowDataModeActive.value) return // already active

        when (autoMode) {
            AutoLowDataMode.AUTOMATIC -> {
                Timber.tag(TAG).i("Slow network detected: auto-activating Low Data Mode")
                lastStateTransitionTime = now
                _isAutoTurnedOn.value = true
                updateCombinedState()
                _bannerEvent.tryEmit(NetworkBannerUiState.LowDataModeOn)
            }
            AutoLowDataMode.ASK_ME -> {
                if (now - lastAskMeDismissTime < ASK_ME_SNOOZE_MS) {
                    Timber.tag(TAG).d("Ask Me dialog snoozed (15 min window active)")
                    return
                }
                val isForeground = runCatching {
                    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                }.getOrDefault(false)

                if (isForeground && !_showAskMeDialog.value) {
                    Timber.tag(TAG).i("Slow network detected: requesting Ask Me dialog")
                    lastStateTransitionTime = now
                    _showAskMeDialog.value = true
                }
            }
            AutoLowDataMode.OFF -> {}
        }
    }

    private fun onNetworkRecovered(now: Long) {
        Timber.tag(TAG).i("Network recovered (>%d bps): auto-deactivating Low Data Mode", RECOVERED_THROUGHPUT_BPS)
        lastStateTransitionTime = now
        lastHighSpeedStartTime = 0L
        _isAutoTurnedOn.value = false
        updateCombinedState()
        _bannerEvent.tryEmit(NetworkBannerUiState.LowDataModeOff)
    }

    fun onConfirmAskMe(context: Context) {
        _showAskMeDialog.value = false
        setManualLowDataMode(context, true)
    }

    fun onDismissAskMe() {
        _showAskMeDialog.value = false
        lastAskMeDismissTime = SystemClock.elapsedRealtime()
    }
}
