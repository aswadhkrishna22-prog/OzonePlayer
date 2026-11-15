/*
 * Ozone Player (2026)
 * Radio Playback Bridge & Player Helper
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.radio

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.RadioQueue
import timber.log.Timber

object RadioPlayer {
    private const val TAG = "RadioPlayer"

    suspend fun playStation(
        station: RadioStation,
        playerConnection: PlayerConnection,
        database: MusicDatabase,
    ) = withContext(Dispatchers.IO) {
        try {
            val itemMediaId = "radio_${station.uuid}"

            val metadata = MediaMetadata(
                id = itemMediaId,
                title = station.name,
                artists = listOf(MediaMetadata.Artist(id = null, name = "Live Radio")),
                duration = -1,
                thumbnailUrl = station.favicon,
                album = station.country?.let { MediaMetadata.Album(id = "radio_country_$it", title = it) },
            )
            database.insert(metadata)

            val formatEntity = FormatEntity(
                id = itemMediaId,
                itag = 0,
                mimeType = if (station.hls) "application/x-mpegURL" else "audio/mpeg",
                codecs = station.codec ?: "mp3",
                bitrate = station.bitrate.takeIf { it > 0 } ?: 128,
                sampleRate = 44100,
                contentLength = 0,
                loudnessDb = null,
                playbackUrl = station.streamUrl,
                bitsPerSample = 16,
            )
            database.upsert(formatEntity)

            val mediaItem = MediaItem.Builder()
                .setMediaId(itemMediaId)
                .setUri(station.streamUrl.toUri())
                .setCustomCacheKey(itemMediaId)
                .setTag(metadata)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setSubtitle("Live Radio" + (station.frequency?.let { " · $it" } ?: ""))
                        .setArtist("Live Radio")
                        .setArtworkUri(station.favicon?.toUri())
                        .setIsPlayable(true)
                        .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .build(),
                )
                .build()

            RadioRepository.recordPlayed(station)

            withContext(Dispatchers.Main) {
                playerConnection.playQueue(
                    RadioQueue(
                        title = station.name,
                        items = listOf(mediaItem),
                        startIndex = 0,
                    ),
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start radio stream for %s", station.name)
        }
    }
}
