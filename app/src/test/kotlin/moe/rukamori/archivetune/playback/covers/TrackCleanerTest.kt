/*
 * Ozone Player (2026)
 * TrackCleaner Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.covers

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackCleanerTest {

    @Test
    fun clean_stripsOfficialVideoAndJunkDecorations() {
        val test1 = TrackCleaner.clean("Blinding Lights (Official Music Video)", "The Weeknd")
        assertEquals("Blinding Lights", test1.title)
        assertEquals("The Weeknd", test1.artist)

        val test2 = TrackCleaner.clean("Starboy [Official Audio]", "The Weeknd ft. Daft Punk")
        assertEquals("Starboy", test2.title)

        val test3 = TrackCleaner.clean("In the End (4K Remastered)", "Linkin Park")
        assertEquals("In the End", test3.title)

        val test4 = TrackCleaner.clean("Bohemian Rhapsody (Live at Wembley)", "Queen")
        assertEquals("Bohemian Rhapsody", test4.title)
    }

    @Test
    fun clean_handlesTopicSuffix() {
        val test = TrackCleaner.clean("Midnight City", "M83 - Topic")
        assertEquals("Midnight City", test.title)
        assertEquals("M83", test.artist)
    }

    @Test
    fun clean_parsesArtistDashTitleFormat() {
        val test = TrackCleaner.clean("Coldplay - Yellow (Official Video)", "Various Artists")
        assertEquals("Yellow", test.title)
        assertEquals("Coldplay", test.artist)
    }

    @Test
    fun primaryArtist_extractsLeadArtist() {
        assertEquals("Drake", TrackCleaner.primaryArtist("Drake, 21 Savage"))
        assertEquals("Calvin Harris", TrackCleaner.primaryArtist("Calvin Harris & Dua Lipa"))
        assertEquals("Eminem", TrackCleaner.primaryArtist("Eminem feat. Rihanna"))
        assertEquals("Adele", TrackCleaner.primaryArtist("Adele"))
    }

    @Test
    fun normalize_removesPunctuationAndLowercases() {
        assertEquals("blindinglights", TrackCleaner.normalize("Blinding Lights!"))
        assertEquals("theweeknd", TrackCleaner.normalize("The Weeknd - Topic"))
    }
}
