/*
 * Ozone Player (2026)
 * Radio Playback Queue
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.models.MediaMetadata

class RadioQueue(
    val title: String? = "Live Radio",
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {
    // Keep preloadItem null so QueuePlayOrchestrator plays items directly by their direct URI
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val safeStartIndex = if (items.isEmpty()) 0 else startIndex.coerceIn(items.indices)
        return Queue.Status(title, items, safeStartIndex, position)
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = throw UnsupportedOperationException()
}
