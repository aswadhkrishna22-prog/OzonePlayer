/*
 * Ozone Player (2026)
 * UltraHdArtworkRepository Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UltraHdArtworkRepositoryTest {

    @Test
    fun makeSongKey_generatesConsistentCanonicalKey() {
        val key1 = UltraHdArtworkRepository.makeSongKey(
            rawTitle = "Blinding Lights (Official Video)",
            rawArtist = "The Weeknd",
            rawAlbum = "After Hours"
        )
        val key2 = UltraHdArtworkRepository.makeSongKey(
            rawTitle = "Blinding Lights",
            rawArtist = "The Weeknd",
            rawAlbum = "After Hours"
        )

        assertEquals("the weeknd|blinding lights|after hours", key1)
        assertEquals(key1, key2)
    }

    @Test
    fun makeSongKey_normalizesMultiArtist() {
        val key = UltraHdArtworkRepository.makeSongKey(
            rawTitle = "Save Your Tears",
            rawArtist = "The Weeknd & Ariana Grande",
            rawAlbum = null
        )
        assertEquals("the weeknd|save your tears|", key)
    }
}
