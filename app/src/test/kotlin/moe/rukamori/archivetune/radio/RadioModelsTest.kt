/*
 * Ozone Player (2026)
 * RadioModels Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioModelsTest {

    @Test
    fun extractFrequency_parsesDecimalAndWholeFrequencies() {
        assertEquals("98.3 FM", RadioStation.extractFrequency("Radio Mirchi 98.3 FM"))
        assertEquals("94.3 FM", RadioStation.extractFrequency("Club FM 94.3"))
        assertEquals("92.7 FM", RadioStation.extractFrequency("Big FM 92.7"))
        assertEquals("93.5 FM", RadioStation.extractFrequency("Red FM 93.5 (Superhits)"))
        assertEquals("104 FM", RadioStation.extractFrequency("Fever 104 FM Delhi"))
        assertNull(RadioStation.extractFrequency("BBC Radio 1"))
        assertNull(RadioStation.extractFrequency("Classic Rock Florida"))
    }

    @Test
    fun customStation_identifiesCustomPrefix() {
        val custom = RadioStation(
            uuid = "custom-1234-abcd",
            name = "My Personal Icecast",
            streamUrl = "http://streaming.example.com/live.mp3",
        )
        assertTrue(custom.isCustom)
        assertEquals("radio:custom-1234-abcd", custom.id)

        val directoryStation = RadioStation(
            uuid = "96030c6a-0601-11e8-ae97-52543be04c81",
            name = "Dance Wave!",
            streamUrl = "http://dancewave.online/dance.mp3",
        )
        assertFalse(directoryStation.isCustom)
        assertEquals("radio:96030c6a-0601-11e8-ae97-52543be04c81", directoryStation.id)
    }

    @Test
    fun details_formatsTagsCodecAndBitrate() {
        val station = RadioStation(
            uuid = "test-uuid",
            name = "Radio Mango 91.9",
            streamUrl = "http://example.com/stream",
            codec = "MP3",
            bitrate = 128,
            country = "India",
            tags = listOf("malayalam", "news", "music"),
        )

        assertEquals(
            "91.9 FM · MP3 · 128 kbps · India · malayalam, news, music",
            station.details
        )
    }
}
