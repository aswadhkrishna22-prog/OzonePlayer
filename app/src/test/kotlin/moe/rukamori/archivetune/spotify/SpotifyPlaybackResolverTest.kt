/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyPlaybackResolverTest {

    @Test
    fun extractBaseTitle_stripsFromMovieQuotes() {
        assertEquals("Kuthanthram", SpotifyPlaybackResolver.extractBaseTitle("Kuthanthram - From \"Manjummel Boys\""))
        assertEquals("Illuminati", SpotifyPlaybackResolver.extractBaseTitle("Illuminati (From \"Aavesham\")"))
        assertEquals("Ta Takkara", SpotifyPlaybackResolver.extractBaseTitle("Ta Takkara - From \"Kalki 2898 AD\""))
        assertEquals("Chuttamalle", SpotifyPlaybackResolver.extractBaseTitle("Chuttamalle - From \"Devara Part 1\""))
    }

    @Test
    fun extractBaseTitle_stripsFromWithoutQuotes() {
        assertEquals("Illuminati", SpotifyPlaybackResolver.extractBaseTitle("Illuminati - From Aavesham"))
        assertEquals("Ordinary Person", SpotifyPlaybackResolver.extractBaseTitle("Ordinary Person (From Leo)"))
        assertEquals("Hukum", SpotifyPlaybackResolver.extractBaseTitle("Hukum - From Jailer"))
    }

    @Test
    fun extractBaseTitle_stripsSoundtrackAndOst() {
        assertEquals("Main Theme", SpotifyPlaybackResolver.extractBaseTitle("Main Theme - Original Motion Picture Soundtrack"))
        assertEquals("Credits", SpotifyPlaybackResolver.extractBaseTitle("Credits (Original Soundtrack)"))
        assertEquals("Battle Theme", SpotifyPlaybackResolver.extractBaseTitle("Battle Theme - OST"))
        assertEquals("Intro", SpotifyPlaybackResolver.extractBaseTitle("Intro (OST)"))
    }

    @Test
    fun extractBaseTitle_stripsFeatAndFtTags() {
        assertEquals("Ghost", SpotifyPlaybackResolver.extractBaseTitle("Ghost (feat. Justin Bieber)"))
        assertEquals("Stay", SpotifyPlaybackResolver.extractBaseTitle("Stay (ft. Justin Bieber)"))
        assertEquals("Peaches", SpotifyPlaybackResolver.extractBaseTitle("Peaches (featuring Daniel Caesar)"))
    }

    @Test
    fun extractBaseTitle_preservesCleanTitles() {
        assertEquals("Blinding Lights", SpotifyPlaybackResolver.extractBaseTitle("Blinding Lights"))
        assertEquals("Shape of You", SpotifyPlaybackResolver.extractBaseTitle("Shape of You"))
        assertEquals("Believer", SpotifyPlaybackResolver.extractBaseTitle("Believer"))
    }

    @Test
    fun detectLanguage_detectsTargetLanguages() {
        assertEquals("malayalam", SpotifyPlaybackResolver.detectLanguage("Samajavaragamana [Malayalam]"))
        assertEquals("malayalam", SpotifyPlaybackResolver.detectLanguage("Samajavaragamana - Malayalam"))
        assertEquals("kannada", SpotifyPlaybackResolver.detectLanguage("Samajavaragamana (Kannada)"))
        assertEquals("telugu", SpotifyPlaybackResolver.detectLanguage("Samajavaragamana Telugu Song"))
        assertEquals("tamil", SpotifyPlaybackResolver.detectLanguage("Katchi Sera - Tamil"))
        assertEquals("hindi", SpotifyPlaybackResolver.detectLanguage("Chaleya (Hindi)"))
    }

    @Test
    fun detectLanguage_ignoresEnglishSubtitles() {
        assertEquals("malayalam", SpotifyPlaybackResolver.detectLanguage("Samajavaragamana Malayalam Song (English Subtitles)"))
        assertEquals("malayalam", SpotifyPlaybackResolver.detectLanguage("Samajavaragamana Malayalam [Eng Sub]"))
        assertEquals(null, SpotifyPlaybackResolver.detectLanguage("Shape of You"))
    }
}
