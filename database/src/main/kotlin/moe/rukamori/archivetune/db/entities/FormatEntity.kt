/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

@Entity(tableName = "format")
data class FormatEntity(
    @PrimaryKey val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double? = null,
    val playbackUrl: String?,
    val bitsPerSample: Int? = null,
)

fun FormatEntity.containerLabel(): String = mimeType.substringAfter("/").substringBefore(";").uppercase()

fun FormatEntity.codecLabel(): String {
    val rawCodec = codecs.ifBlank { mimeType.substringAfter("/") }.uppercase()
    val rawMime = mimeType.substringAfter("/").substringBefore(";").uppercase()

    return when {
        rawCodec.contains("FLAC") || rawCodec.contains("ALAC") -> "FLAC"
        rawCodec.contains("OPUS") -> "OPUS"
        rawCodec.contains("AAC") || rawCodec.contains("MP4A") -> "AAC"
        rawCodec.contains("VORBIS") -> "VORBIS"
        rawMime.contains("OPUS") -> "OPUS"
        rawMime.contains("AAC") || rawMime.contains("MP4A") -> "AAC"
        rawMime.contains("VORBIS") -> "VORBIS"
        rawMime.isNotBlank() -> rawMime
        else -> rawCodec
    }
}

fun FormatEntity.sourceBitrateKbps(): Int? =
    takeIf { bitrate > 0 }?.let {
        if (bitrate > 2000) (bitrate / 1000.0).roundToInt() else bitrate
    }

fun FormatEntity.measuredBitrateKbps(durationSec: Long?): Int? {
    if (contentLength > 0L && durationSec != null && durationSec > 0L) {
        val bps = (contentLength * 8.0) / durationSec
        return (bps / 1000.0).roundToInt().takeIf { it > 0 }
    }
    return null
}

fun FormatEntity.measuredBitrateKbps(durationSec: Int?): Int? =
    measuredBitrateKbps(durationSec?.toLong())

fun FormatEntity.formattedBitrate(measuredKbps: Int? = null): String {
    val kbps = measuredKbps ?: sourceBitrateKbps()
    return if (kbps != null && kbps > 0) "~$kbps kbps" else "Unknown"
}

fun FormatEntity.formattedSampleRate(): String? =
    sampleRate?.takeIf { it > 0 }?.let {
        "${(it / 100.0).roundToInt() / 10.0} kHz"
    }

fun FormatEntity.formattedFileSize(): String = if (contentLength > 0) "${(contentLength / 1024.0 / 1024.0).roundToInt()} MB" else ""

fun FormatEntity.formattedQuality(): String {
    val sr = formattedSampleRate()
    return when {
        bitsPerSample != null && bitsPerSample > 0 && sr != null -> "$bitsPerSample-bit / $sr"
        sr != null -> sr
        bitrate > 0 -> formattedBitrate()
        else -> "Unknown"
    }
}
