/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Playlist(
    @Embedded
    val playlist: PlaylistEntity,
    val songCount: Int,
    @Relation(
        entity = SongEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        projection = ["thumbnailUrl"],
        associateBy =
            Junction(
                value = PlaylistSongMapPreview::class,
                parentColumn = "playlistId",
                entityColumn = "songId",
            ),
    )
    val songThumbnails: List<String?>,
) : LocalItem() {
    override val id: String
        get() = playlist.id
    override val title: String
        get() = playlist.name
    override val thumbnailUrl: String?
        get() = null

    val isCustomCover: Boolean
        get() = isCustomPlaylistCover(playlist.thumbnailUrl)

    val thumbnails: List<String>
        get() {
            return if (isCustomCover && playlist.thumbnailUrl != null) {
                listOf(playlist.thumbnailUrl)
            } else {
                val songThumbs = songThumbnails.filterNotNull().filter { it.isNotBlank() }
                if (songThumbs.isNotEmpty()) {
                    songThumbs
                } else if (playlist.thumbnailUrl != null) {
                    listOf(playlist.thumbnailUrl)
                } else {
                    emptyList()
                }
            }
        }

    companion object {
        fun isCustomPlaylistCover(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return url.startsWith("content://") || url.startsWith("file://") || url.startsWith("custom://")
        }
    }
}
