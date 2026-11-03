/*
 * Ozone Player (2026) | Beast Mode Audio Engine
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.beastmode

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.BeastModeActiveKey
import moe.rukamori.archivetune.constants.BeastModeAutoPromptKey
import moe.rukamori.archivetune.constants.BeastModePrevStateKey
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber

@Serializable
data class BeastModeBackupState(
    val eqEnabled: Boolean = false,
    val selectedProfileId: String = "flat",
    val bandLevelsRaw: String = "",
    val outputGainEnabled: Boolean = false,
    val outputGainMb: Int = 0,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val audioNormalization: Boolean = true,
)

object BeastModeManager {
    private const val TAG = "BeastModeManager"
    private const val DEBOUNCE_PROMPT_MS = 6_000L

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    private val _isBeastModeActive = MutableStateFlow(false)
    val isBeastModeActive: StateFlow<Boolean> = _isBeastModeActive.asStateFlow()

    private val _showPromptDialog = MutableStateFlow(false)
    val showPromptDialog: StateFlow<Boolean> = _showPromptDialog.asStateFlow()

    private val _promptDeviceName = MutableStateFlow("")
    val promptDeviceName: StateFlow<String> = _promptDeviceName.asStateFlow()

    private val _autoPromptEnabled = MutableStateFlow(true)
    val autoPromptEnabled: StateFlow<Boolean> = _autoPromptEnabled.asStateFlow()

    @Volatile
    private var lastPromptTimeMs = 0L

    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            appContext.dataStore.data
                .map { it[BeastModeActiveKey] ?: false }
                .distinctUntilChanged()
                .collect { active ->
                    _isBeastModeActive.value = active
                }
        }

        scope.launch(Dispatchers.IO) {
            appContext.dataStore.data
                .map { it[BeastModeAutoPromptKey] ?: true }
                .distinctUntilChanged()
                .collect { enabled ->
                    _autoPromptEnabled.value = enabled
                }
        }
    }

    /**
     * Calculates the optimal V-shaped club / bus party equalizer curve for [bandCount] bands.
     * Clean sub-bass (+2.5 dB) and crisp treble (+2.5 dB) are boosted without triggering
     * Android's internal negative pre-cut attenuation.
     */
    fun calculateBeastCurve(bandCount: Int): List<Int> {
        if (bandCount <= 0) return emptyList()
        if (bandCount == 1) return listOf(250)
        if (bandCount == 5) {
            // Standard 5-band EQ: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
            return listOf(250, 150, 0, 150, 250)
        }

        // Base 10-band reference V-curve:
        // [31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz]
        val referenceCurve = listOf(250, 200, 150, 80, 0, 0, 80, 150, 200, 250)
        if (bandCount == referenceCurve.size) return referenceCurve

        val lastIndex = (referenceCurve.size - 1).toFloat()
        return List(bandCount) { i ->
            val pos = i.toFloat() * lastIndex / (bandCount - 1).toFloat()
            val lo = kotlin.math.floor(pos).toInt().coerceIn(0, referenceCurve.lastIndex)
            val hi = kotlin.math.ceil(pos).toInt().coerceIn(0, referenceCurve.lastIndex)
            val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
            val a = referenceCurve[lo]
            val b = referenceCurve[hi]
            (a + ((b - a) * t)).toInt()
        }
    }

    /**
     * Called when an external audio sink (Bluetooth speaker, AUX cable, wired headphones, USB) connects.
     */
    fun onDeviceConnected(deviceName: String, deviceType: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPromptTimeMs < DEBOUNCE_PROMPT_MS) return
        if (!_autoPromptEnabled.value || _isBeastModeActive.value) return

        lastPromptTimeMs = now
        _promptDeviceName.value = deviceName.ifBlank { "External Audio" }
        _showPromptDialog.value = true
        Timber.tag(TAG).i("Prompting for Beast Mode: device=%s, type=%d", deviceName, deviceType)
    }

    fun onConfirmPrompt(context: Context, bandCount: Int = 5) {
        _showPromptDialog.value = false
        enableBeastMode(context, bandCount)
    }

    fun onDismissPrompt() {
        _showPromptDialog.value = false
    }

    fun toggleBeastMode(context: Context, bandCount: Int = 5) {
        if (_isBeastModeActive.value) {
            disableBeastMode(context)
        } else {
            enableBeastMode(context, bandCount)
        }
    }

    fun setBeastModeActiveDirect(active: Boolean) {
        _isBeastModeActive.value = active
    }

    fun enableBeastMode(context: Context, bandCount: Int = 5) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            runCatching {
                appContext.dataStore.edit { prefs ->
                    // 1. Snapshot previous settings if not already backed up
                    val existingBackup = prefs[BeastModePrevStateKey]
                    if (existingBackup.isNullOrBlank()) {
                        val backup = BeastModeBackupState(
                            eqEnabled = prefs[EqualizerEnabledKey] ?: false,
                            selectedProfileId = prefs[EqualizerSelectedProfileIdKey] ?: "flat",
                            bandLevelsRaw = prefs[EqualizerBandLevelsMbKey] ?: "",
                            outputGainEnabled = prefs[EqualizerOutputGainEnabledKey] ?: false,
                            outputGainMb = prefs[EqualizerOutputGainMbKey] ?: 0,
                            bassBoostEnabled = prefs[EqualizerBassBoostEnabledKey] ?: false,
                            bassBoostStrength = prefs[EqualizerBassBoostStrengthKey] ?: 0,
                            virtualizerEnabled = prefs[EqualizerVirtualizerEnabledKey] ?: false,
                            virtualizerStrength = prefs[EqualizerVirtualizerStrengthKey] ?: 0,
                            audioNormalization = prefs[AudioNormalizationKey] ?: true,
                        )
                        prefs[BeastModePrevStateKey] = json.encodeToString(backup)
                    }

                    // 2. Apply Beast Mode parameters
                    val curve = calculateBeastCurve(bandCount.coerceAtLeast(1))
                    val encodedCurve = json.encodeToString(curve)

                    prefs[EqualizerEnabledKey] = true
                    prefs[EqualizerSelectedProfileIdKey] = "beast_mode"
                    prefs[EqualizerBandLevelsMbKey] = encodedCurve
                    prefs[EqualizerOutputGainEnabledKey] = true
                    prefs[EqualizerOutputGainMbKey] = 250 // +2.5 dB clean pre-amp boost
                    prefs[EqualizerBassBoostEnabledKey] = false // Disabled to avoid Android OEM ducking/limiter compressor
                    prefs[EqualizerBassBoostStrengthKey] = 0
                    prefs[EqualizerVirtualizerEnabledKey] = false // Disabled to avoid phase cancellation on speakers
                    prefs[EqualizerVirtualizerStrengthKey] = 0
                    prefs[AudioNormalizationKey] = true // Keep TRUE to preserve YouTube 1.414x (+3 dB) dynamic volume boost
                    prefs[BeastModeActiveKey] = true
                }
            }.onSuccess {
                _isBeastModeActive.value = true
                runCatching {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(appContext, appContext.getString(R.string.beast_mode_activated), Toast.LENGTH_SHORT).show()
                    }
                }
                Timber.tag(TAG).i("Beast Mode enabled with bandCount=%d", bandCount)
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to enable Beast Mode")
            }
        }
    }

    fun disableBeastMode(context: Context) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            runCatching {
                appContext.dataStore.edit { prefs ->
                    val backupRaw = prefs[BeastModePrevStateKey]
                    val backup = if (!backupRaw.isNullOrBlank()) {
                        runCatching { json.decodeFromString<BeastModeBackupState>(backupRaw) }.getOrNull()
                    } else null

                    if (backup != null) {
                        prefs[EqualizerEnabledKey] = backup.eqEnabled
                        prefs[EqualizerSelectedProfileIdKey] = backup.selectedProfileId
                        prefs[EqualizerBandLevelsMbKey] = backup.bandLevelsRaw
                        prefs[EqualizerOutputGainEnabledKey] = backup.outputGainEnabled
                        prefs[EqualizerOutputGainMbKey] = backup.outputGainMb
                        prefs[EqualizerBassBoostEnabledKey] = backup.bassBoostEnabled
                        prefs[EqualizerBassBoostStrengthKey] = backup.bassBoostStrength
                        prefs[EqualizerVirtualizerEnabledKey] = backup.virtualizerEnabled
                        prefs[EqualizerVirtualizerStrengthKey] = backup.virtualizerStrength
                        prefs[AudioNormalizationKey] = backup.audioNormalization
                    } else {
                        prefs[EqualizerOutputGainEnabledKey] = false
                        prefs[EqualizerOutputGainMbKey] = 0
                        prefs[EqualizerBassBoostEnabledKey] = false
                        prefs[EqualizerBassBoostStrengthKey] = 0
                        prefs[EqualizerVirtualizerEnabledKey] = false
                        prefs[EqualizerVirtualizerStrengthKey] = 0
                        prefs[EqualizerSelectedProfileIdKey] = "flat"
                    }

                    prefs.remove(BeastModePrevStateKey)
                    prefs[BeastModeActiveKey] = false
                }
            }.onSuccess {
                _isBeastModeActive.value = false
                runCatching {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(appContext, appContext.getString(R.string.beast_mode_deactivated), Toast.LENGTH_SHORT).show()
                    }
                }
                Timber.tag(TAG).i("Beast Mode disabled and state restored")
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to disable Beast Mode")
            }
        }
    }
}
