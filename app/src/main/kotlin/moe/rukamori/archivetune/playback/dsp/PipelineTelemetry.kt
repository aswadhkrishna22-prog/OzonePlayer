/*
 * Ozone Player (2026)
 * Live Audio Signal Path Telemetry
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.dsp

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.playback.autoeq.AutoEqRepository
import moe.rukamori.archivetune.playback.processor.AutoEqAudioProcessor
import moe.rukamori.archivetune.playback.processor.SpatialProcessor

data class AudioSourceTelemetry(
    val title: String = "",
    val artist: String = "",
    val codec: String = "PCM",
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitDepth: String = "16-bit",
    val bitrateKbps: Int = 0,
)

data class AudioDspTelemetry(
    val autoEqEnabled: Boolean = false,
    val autoEqModel: String? = null,
    val autoEqFilterCount: Int = 0,
    val spatialEnabled: Boolean = false,
    val dualPlayEnabled: Boolean = false,
    val dualPlayEar: String? = null,
)

data class AudioSinkTelemetry(
    val sampleRate: Int = 44100,
    val channelCount: Int = 2,
    val bitDepth: String = "16-bit Dithered (TPDF)",
    val deviceName: String = "Speaker / Default",
    val deviceType: String = "Built-in",
    val isUsbDac: Boolean = false,
    val isBluetooth: Boolean = false,
    val isBitPerfect: Boolean = false,
)

data class SignalPathSnapshot(
    val source: AudioSourceTelemetry = AudioSourceTelemetry(),
    val dsp: AudioDspTelemetry = AudioDspTelemetry(),
    val sink: AudioSinkTelemetry = AudioSinkTelemetry(),
)

object PipelineTelemetry {
    private val _snapshot = MutableStateFlow(SignalPathSnapshot())
    val snapshot: StateFlow<SignalPathSnapshot> = _snapshot.asStateFlow()

    fun updateSource(
        format: Format?,
        title: String = "",
        artist: String = "",
    ) {
        if (format == null) return

        val mime = format.sampleMimeType ?: ""
        val codec = when {
            mime.contains("flac", ignoreCase = true) -> "FLAC Lossless"
            mime.contains("opus", ignoreCase = true) -> "Opus"
            mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
            mime.contains("mpeg", ignoreCase = true) -> "MP3"
            mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
            else -> mime.substringAfterLast("/").uppercase()
        }

        val bitDepth = when (format.pcmEncoding) {
            C.ENCODING_PCM_24BIT -> "24-bit Hi-Res"
            C.ENCODING_PCM_32BIT -> "32-bit Hi-Res"
            C.ENCODING_PCM_FLOAT -> "32-bit Float"
            else -> if (format.averageBitrate > 500_000) "Lossless (24/16-bit)" else "16-bit"
        }

        val sr = if (format.sampleRate > 0) format.sampleRate else 44100
        val ch = if (format.channelCount > 0) format.channelCount else 2
        val kbps = if (format.averageBitrate > 0) format.averageBitrate / 1000 else 0

        val current = _snapshot.value
        _snapshot.value = current.copy(
            source = AudioSourceTelemetry(
                title = title.ifEmpty { current.source.title },
                artist = artist.ifEmpty { current.source.artist },
                codec = codec,
                sampleRate = sr,
                channelCount = ch,
                bitDepth = bitDepth,
                bitrateKbps = kbps,
            )
        )
    }

    fun updateDsp(
        dualPlayActive: Boolean = false,
        dualPlayEar: String? = null,
    ) {
        val autoEq = AutoEqAudioProcessor.instance
        val autoEqProfile = autoEq.profile

        val current = _snapshot.value
        _snapshot.value = current.copy(
            dsp = AudioDspTelemetry(
                autoEqEnabled = autoEq.enabled && autoEqProfile != null,
                autoEqModel = autoEqProfile?.name,
                autoEqFilterCount = autoEqProfile?.filters?.size ?: 0,
                spatialEnabled = SpatialProcessor.globalEnabled,
                dualPlayEnabled = dualPlayActive,
                dualPlayEar = dualPlayEar,
            )
        )
    }

    fun updateSink(
        context: Context,
        sinkSampleRate: Int = 48000,
        sinkChannels: Int = 2,
    ) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        var devName = "Phone Speaker"
        var devType = "Built-in"
        var isUsb = false
        var isBt = false

        if (audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val activeDevice = devices.firstOrNull { d ->
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                d.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_LINE_ANALOG
            } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            if (activeDevice != null) {
                devName = activeDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: when (activeDevice.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio Device"
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB DAC / Headset"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headphones"
                    else -> "Phone Speaker"
                }

                devType = when (activeDevice.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio (Direct)"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5mm Headphone Jack"
                    else -> "Built-in Speaker"
                }

                isUsb = activeDevice.type == AudioDeviceInfo.TYPE_USB_DEVICE || activeDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET
                isBt = activeDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        }

        val current = _snapshot.value
        _snapshot.value = current.copy(
            sink = AudioSinkTelemetry(
                sampleRate = sinkSampleRate,
                channelCount = sinkChannels,
                bitDepth = if (isUsb) "24-bit / 32-bit Direct" else "16-bit Dithered (TPDF)",
                deviceName = devName,
                deviceType = devType,
                isUsbDac = isUsb,
                isBluetooth = isBt,
                isBitPerfect = isUsb && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            )
        )
    }
}
