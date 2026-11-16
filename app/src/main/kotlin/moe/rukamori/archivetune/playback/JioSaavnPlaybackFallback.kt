/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnDecryptor
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnQualityCache
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnQualityTier
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles automatic fallback recovery for JioSaavn CDN streams when rewritten 320 kbps
 * URLs encounter HTTP 404/403 or early source errors before audio begins playing.
 *
 * Sequence: 320 kbps -> 160 kbps -> original decrypted CDN URL.
 * Retries each quality once per step without showing an error to the user.
 * Caches the working quality so failing URLs are not tried again on subsequent plays.
 * If all fail, surfaces the normal error.
 */
object JioSaavnPlaybackFallback {
    private const val TAG = "JioSaavnFallback"
    private val lastFallbackAttemptMs = AtomicLong(0L)
    @Volatile private var lastFallbackMediaId: String? = null
    @Volatile private var lastFallbackTargetUrl: String? = null

    fun isJioSaavnTrack(mediaItem: MediaItem?): Boolean {
        if (mediaItem == null) return false
        val id = mediaItem.mediaId
        if (id.startsWith("jiosaavn_")) return true
        val uriStr = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        return uriStr.contains("saavncdn.com")
    }

    fun isPrePlaybackSourceError(
        error: PlaybackException,
        currentPositionMs: Long,
    ): Boolean {
        // Must be before the first audio is played
        if (currentPositionMs > 1000L) return false

        val isBadHttpStatus = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        val isIoNetwork = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        val isUnspecifiedIo = error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

        var isHttpError = false
        var t: Throwable? = error
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException) {
                if (t.responseCode == 404 || t.responseCode == 403 || t.responseCode >= 400) {
                    isHttpError = true
                    break
                }
            }
            t = t.cause
        }

        val msg = (error.message ?: "") + " " + (error.cause?.message ?: "")
        val isSourceError = msg.contains("Source error", ignoreCase = true) ||
            msg.contains("Video unavailable", ignoreCase = true) ||
            msg.contains("Response code: 404") ||
            msg.contains("Response code: 403")

        return isBadHttpStatus || isHttpError || isSourceError || isUnspecifiedIo || isIoNetwork
    }

    fun canFallback(mediaItem: MediaItem?): Boolean {
        if (!isJioSaavnTrack(mediaItem)) return false
        val mediaId = mediaItem?.mediaId ?: return false
        val currentUrl = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val rawUrl = JioSaavnQualityCache.getRawUrl(mediaId) ?: currentUrl
        if (rawUrl.isBlank()) return false
        return JioSaavnDecryptor.getNextFallback(currentUrl, rawUrl) != null
    }

    fun tryFallback(
        player: Player,
        database: MusicDatabase,
        scope: CoroutineScope,
    ): Boolean {
        val currentItem = player.currentMediaItem ?: return false
        if (!isJioSaavnTrack(currentItem)) return false

        val mediaId = currentItem.mediaId
        val currentUrl = currentItem.localConfiguration?.uri?.toString().orEmpty()
        val rawUrl = JioSaavnQualityCache.getRawUrl(mediaId) ?: currentUrl
        if (rawUrl.isBlank()) return false

        val next = JioSaavnDecryptor.getNextFallback(currentUrl, rawUrl) ?: return false
        val (nextTier, nextUrl) = next

        val now = SystemClock.elapsedRealtime()
        if (lastFallbackMediaId == mediaId && lastFallbackTargetUrl == nextUrl && now - lastFallbackAttemptMs.get() < 1000L) {
            return true
        }
        lastFallbackAttemptMs.set(now)
        lastFallbackMediaId = mediaId
        lastFallbackTargetUrl = nextUrl

        Timber.tag(TAG).i(
            "Seamlessly falling back JioSaavn track '%s' from %s to %s (tier=%s)",
            mediaId, currentUrl, nextUrl, nextTier
        )

        // 1. Record working tier in cache so subsequent plays skip failed URLs
        JioSaavnQualityCache.recordWorkingTier(mediaId, nextTier)

        // 2. Update format entity in Room DB with actual fallback bitrate & URL
        val nominalBitrate = when (nextTier) {
            JioSaavnQualityTier.KBPS_320 -> 320000
            JioSaavnQualityTier.KBPS_160 -> 160000
            JioSaavnQualityTier.ORIGINAL -> {
                if (JioSaavnDecryptor.detectTierFromUrl(nextUrl) == JioSaavnQualityTier.KBPS_160) 160000 else 96000
            }
        }

        scope.launch(Dispatchers.IO) {
            val song = database.song(mediaId).firstOrNull()
            val durationSec = song?.song?.duration?.toLong()?.takeIf { it > 0 } ?: 200L
            val contentLength = (nominalBitrate.toLong() / 8L) * durationSec

            val existing = database.getFormatForSource(mediaId, PlaybackSource.FLAC)
                ?: database.getFormatById(mediaId)
            val updated = (existing ?: FormatEntity(
                id = formatIdForSource(mediaId, PlaybackSource.FLAC),
                itag = 0,
                mimeType = "audio/mp4",
                codecs = "mp4a.40.2",
                bitrate = nominalBitrate,
                sampleRate = 44100,
                contentLength = contentLength,
                loudnessDb = null,
                playbackUrl = nextUrl,
                bitsPerSample = 16,
            )).copy(
                bitrate = nominalBitrate,
                playbackUrl = nextUrl,
                contentLength = contentLength,
            )
            database.upsert(updated)
            database.upsert(updated.copy(id = mediaId))
        }

        // 3. Replace player media item and prepare
        val currentIndex = player.currentMediaItemIndex
        val newItem = currentItem.buildUpon().setUri(Uri.parse(nextUrl)).build()
        player.replaceMediaItem(currentIndex, newItem)
        player.prepare()
        player.play()
        return true
    }
}
