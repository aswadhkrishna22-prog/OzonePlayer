/*
 * Ozone Player (2026) | Song Vibe Matcher Unit Tests
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.playback.queue

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import moe.rukamori.archivetune.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class SongVibeMatcherTest {

    private fun createMediaItem(id: String, title: String, artist: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                Media3Metadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )
            .build()
    }

    private fun createMetadata(id: String, title: String, artist: String): MediaMetadata {
        return MediaMetadata(
            id = id,
            title = title,
            artists = listOf(MediaMetadata.Artist(id = "a1", name = artist)),
            duration = 180,
        )
    }

    @Test
    fun testDetectPowerVibe() {
        assertEquals(SongVibe.POWER_DANCE, SongVibeMatcher.detectVibe("Badass", "Anirudh Ravichander"))
        assertEquals(SongVibe.POWER_DANCE, SongVibeMatcher.detectVibe("Hukum - Thalaivar Alappara", "Anirudh Ravichander"))
        assertEquals(SongVibe.POWER_DANCE, SongVibeMatcher.detectVibe("Vaathi Coming", "Anirudh Ravichander"))
        assertEquals(SongVibe.POWER_DANCE, SongVibeMatcher.detectVibe("Illuminati", "Sushin Shyam"))
        assertEquals(SongVibe.POWER_DANCE, SongVibeMatcher.detectVibe("Katchi Sera", "Sai Abhyankhar"))
        assertEquals(SongVibe.POWER_DANCE, SongVibeMatcher.detectVibe("Naa Ready", "Thalapathy Vijay, Anirudh"))
    }

    @Test
    fun testDetectLoveVibe() {
        assertEquals(SongVibe.LOVE_ROMANTIC, SongVibeMatcher.detectVibe("Nee Paartha Vizhigal", "Anirudh Ravichander"))
        assertEquals(SongVibe.LOVE_ROMANTIC, SongVibeMatcher.detectVibe("Idhazhin Oram - The Innocence of Love", "Anirudh Ravichander"))
        assertEquals(SongVibe.LOVE_ROMANTIC, SongVibeMatcher.detectVibe("Kesariya", "Arijit Singh, Pritam"))
        assertEquals(SongVibe.LOVE_ROMANTIC, SongVibeMatcher.detectVibe("Vaseegara", "Bombay Jayashri"))
    }

    @Test
    fun testDetectSadVibe() {
        assertEquals(SongVibe.EMOTIONAL_SAD, SongVibeMatcher.detectVibe("Porkanda Singam", "Anirudh Ravichander"))
        assertEquals(SongVibe.EMOTIONAL_SAD, SongVibeMatcher.detectVibe("Kanave Kanave", "Anirudh Ravichander"))
        assertEquals(SongVibe.EMOTIONAL_SAD, SongVibeMatcher.detectVibe("Po Nee Po", "Anirudh Ravichander"))
        assertEquals(SongVibe.EMOTIONAL_SAD, SongVibeMatcher.detectVibe("Channa Mereya", "Arijit Singh"))
    }

    @Test
    fun testDetectNeutralVibe() {
        assertEquals(SongVibe.NEUTRAL, SongVibeMatcher.detectVibe("Morning News Bulletin", "BBC Worldwide"))
        assertEquals(SongVibe.NEUTRAL, SongVibeMatcher.detectVibe("", ""))
        assertEquals(SongVibe.NEUTRAL, SongVibeMatcher.detectVibe(null, null))
    }

    @Test
    fun testReorderQueueWithPowerDanceSeed() {
        val seed = createMetadata("s1", "Badass", "Anirudh")
        val items = listOf(
            createMediaItem("s1", "Badass", "Anirudh"),
            createMediaItem("m1", "Porkanda Singam", "Anirudh"), // Sad (score 0)
            createMediaItem("m2", "Hukum", "Anirudh"),           // Power (score 100)
            createMediaItem("m3", "Idhazhin Oram", "Anirudh"),   // Love (score 20)
            createMediaItem("m4", "Vaathi Coming", "Anirudh"),   // Power (score 100)
        )

        val reordered = SongVibeMatcher.reorderQueue(items, seed, isBeastMode = false, enabled = true)

        // Seed "Badass" remains pinned at index 0
        assertEquals("s1", reordered[0].mediaId)
        // Next should be high-energy tracks (Hukum, Vaathi Coming)
        assertEquals("m2", reordered[1].mediaId)
        assertEquals("m4", reordered[2].mediaId)
        // Love song next
        assertEquals("m3", reordered[3].mediaId)
        // Sad ballad moved to the very bottom
        assertEquals("m1", reordered[4].mediaId)
    }

    @Test
    fun testReorderQueueWithLoveSeed() {
        val seed = createMetadata("s1", "Nee Paartha Vizhigal", "Anirudh")
        val items = listOf(
            createMediaItem("s1", "Nee Paartha Vizhigal", "Anirudh"),
            createMediaItem("m1", "Badass", "Anirudh"),          // Power (score 0)
            createMediaItem("m2", "Idhazhin Oram", "Anirudh"),   // Love (score 100)
            createMediaItem("m3", "Porkanda Singam", "Anirudh"), // Sad (score 30)
            createMediaItem("m4", "Kesariya", "Arijit"),        // Love (score 100)
        )

        val reordered = SongVibeMatcher.reorderQueue(items, seed, isBeastMode = false, enabled = true)

        assertEquals("s1", reordered[0].mediaId)
        // Love songs come first
        assertEquals("m2", reordered[1].mediaId)
        assertEquals("m4", reordered[2].mediaId)
        // Sad ballad comes before aggressive power/phonk in love context
        assertEquals("m3", reordered[3].mediaId)
        // Aggressive power track moved to end
        assertEquals("m1", reordered[4].mediaId)
    }

    @Test
    fun testReorderQueueInBeastModeForcesPowerPriority() {
        // Even if seed is neutral, Beast Mode enforces POWER_DANCE priority!
        val seed = createMetadata("s1", "Some Neutral Track", "Artist")
        val items = listOf(
            createMediaItem("s1", "Some Neutral Track", "Artist"),
            createMediaItem("m1", "Porkanda Singam", "Anirudh"), // Sad
            createMediaItem("m2", "Illuminati", "Sushin"),       // Power
            createMediaItem("m3", "Hukum", "Anirudh"),           // Power
        )

        val reordered = SongVibeMatcher.reorderQueue(items, seed, isBeastMode = true, enabled = true)

        assertEquals("s1", reordered[0].mediaId)
        assertEquals("m2", reordered[1].mediaId) // Illuminati
        assertEquals("m3", reordered[2].mediaId) // Hukum
        assertEquals("m1", reordered[3].mediaId) // Sad ballad at the end
    }
}
