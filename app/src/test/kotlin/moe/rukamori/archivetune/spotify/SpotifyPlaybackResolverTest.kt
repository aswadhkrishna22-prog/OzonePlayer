/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.spotify.matching.SpotifyTrackScorer
import moe.rukamori.archivetune.spotify.matching.SpotifyTrackScorer.AmbiguityResult
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun buildStandardQuery_appendsTargetLangWhenNotPresent() {
        val track = SpotifyTrack(name = "Samajavaragamana", artists = listOf(SpotifySimpleArtist(name = "Thaman S")))
        val query = SpotifyPlaybackResolver.buildStandardQuery(track, "malayalam")
        assertEquals("Thaman S Samajavaragamana malayalam", query)
    }

    @Test
    fun buildStandardQuery_doesNotDuplicateLanguageWhenAlreadyPresent() {
        val track = SpotifyTrack(name = "Samajavaragamana Malayalam", artists = listOf(SpotifySimpleArtist(name = "Thaman S")))
        val query = SpotifyPlaybackResolver.buildStandardQuery(track, "malayalam")
        assertEquals("Thaman S Samajavaragamana Malayalam", query)
    }

    @Test
    fun cacheKeys_differentiateVersionsAndPrefixV2() {
        val teluguKey = SpotifyTrackScorer.cacheKey("spotify:track:track_telugu_111")
        val malayalamKey = SpotifyTrackScorer.cacheKey("spotify:track:track_malayalam_222")
        val tamilKey = SpotifyTrackScorer.cacheKey("spotify:track:track_tamil_333")
        val hindiKey = SpotifyTrackScorer.cacheKey("spotify:track:track_hindi_444")

        assertEquals("v2:track_telugu_111", teluguKey)
        assertEquals("v2:track_malayalam_222", malayalamKey)
        assertEquals("v2:track_tamil_333", tamilKey)
        assertEquals("v2:track_hindi_444", hindiKey)

        val keys = setOf(teluguKey, malayalamKey, tamilKey, hindiKey)
        assertEquals(4, keys.size)
    }

    @Test
    fun matcher_fixturesTeluguMalayalamTamilHindi_picksRightVersionEachTime() {
        val candTelugu = SongItem(
            id = "yt_telugu",
            title = "Samajavaragamana - Telugu Audio",
            artists = listOf(Artist(name = "Sid Sriram", id = null), Artist(name = "Thaman S", id = null)),
            duration = 214,
        )
        val candMalayalam = SongItem(
            id = "yt_malayalam",
            title = "Samajavaragamana - Malayalam Audio",
            artists = listOf(Artist(name = "Vijay Yesudas", id = null), Artist(name = "Thaman S", id = null)),
            duration = 214,
        )
        val candTamil = SongItem(
            id = "yt_tamil",
            title = "Samajavaragamana - Tamil",
            artists = listOf(Artist(name = "Haricharan", id = null), Artist(name = "Thaman S", id = null)),
            duration = 214,
        )
        val candHindi = SongItem(
            id = "yt_hindi",
            title = "Samajavaragamana Hindi Version",
            artists = listOf(Artist(name = "Jubin Nautiyal", id = null), Artist(name = "Thaman S", id = null)),
            duration = 214,
        )
        val fixtures = listOf(candTelugu, candMalayalam, candTamil, candHindi)

        // 1. Target: Malayalam (Vijay Yesudas, Thaman S)
        val malScored = fixtures.map { cand ->
            SpotifyTrackScorer.scoreCandidate(
                targetTitle = "Samajavaragamana (Malayalam)",
                targetArtists = listOf("Vijay Yesudas", "Thaman S"),
                targetDurationMs = 214000L,
                candidate = cand,
                isOfficialSong = true,
            )
        }
        val malWinner = SpotifyTrackScorer.evaluateAmbiguity(malScored, listOf("Vijay Yesudas", "Thaman S"))
        assertTrue("Malayalam must be ClearWinner", malWinner is AmbiguityResult.ClearWinner)
        assertEquals("yt_malayalam", (malWinner as AmbiguityResult.ClearWinner).accepted.id)

        // 2. Target: Telugu (Sid Sriram, Thaman S)
        val telScored = fixtures.map { cand ->
            SpotifyTrackScorer.scoreCandidate(
                targetTitle = "Samajavaragamana (Telugu)",
                targetArtists = listOf("Sid Sriram", "Thaman S"),
                targetDurationMs = 214000L,
                candidate = cand,
                isOfficialSong = true,
            )
        }
        val telWinner = SpotifyTrackScorer.evaluateAmbiguity(telScored, listOf("Sid Sriram", "Thaman S"))
        assertTrue("Telugu must be ClearWinner", telWinner is AmbiguityResult.ClearWinner)
        assertEquals("yt_telugu", (telWinner as AmbiguityResult.ClearWinner).accepted.id)

        // 3. Target: Tamil (Haricharan, Thaman S)
        val tamScored = fixtures.map { cand ->
            SpotifyTrackScorer.scoreCandidate(
                targetTitle = "Samajavaragamana (Tamil)",
                targetArtists = listOf("Haricharan", "Thaman S"),
                targetDurationMs = 214000L,
                candidate = cand,
                isOfficialSong = true,
            )
        }
        val tamWinner = SpotifyTrackScorer.evaluateAmbiguity(tamScored, listOf("Haricharan", "Thaman S"))
        assertTrue("Tamil must be ClearWinner", tamWinner is AmbiguityResult.ClearWinner)
        assertEquals("yt_tamil", (tamWinner as AmbiguityResult.ClearWinner).accepted.id)

        // 4. Target: Hindi (Jubin Nautiyal, Thaman S)
        val hinScored = fixtures.map { cand ->
            SpotifyTrackScorer.scoreCandidate(
                targetTitle = "Samajavaragamana Hindi",
                targetArtists = listOf("Jubin Nautiyal", "Thaman S"),
                targetDurationMs = 214000L,
                candidate = cand,
                isOfficialSong = true,
            )
        }
        val hinWinner = SpotifyTrackScorer.evaluateAmbiguity(hinScored, listOf("Jubin Nautiyal", "Thaman S"))
        assertTrue("Hindi must be ClearWinner", hinWinner is AmbiguityResult.ClearWinner)
        assertEquals("yt_hindi", (hinWinner as AmbiguityResult.ClearWinner).accepted.id)
    }

    @Test
    fun artistSetComparison_penalizesMissingPerformerAndRewardsFullMatch() {
        val targetArtists = listOf("Vijay Yesudas", "Thaman S")

        // Candidate with Sid Sriram instead of Vijay Yesudas
        val wrongPerformerComp = SpotifyTrackScorer.compareArtistSets(
            targetArtists = targetArtists,
            candArtists = listOf("Sid Sriram", "Thaman S"),
        )
        assertTrue(wrongPerformerComp.missingTargetArtists.contains("vijay yesudas"))
        assertTrue(wrongPerformerComp.foreignArtists.contains("sid sriram"))
        assertTrue(wrongPerformerComp.score < 0.0)

        // Candidate with both target performers
        val correctPerformerComp = SpotifyTrackScorer.compareArtistSets(
            targetArtists = targetArtists,
            candArtists = listOf("Vijay Yesudas", "Thaman S"),
        )
        assertEquals(1.0, correctPerformerComp.overlapRatio, 0.001)
        assertTrue(correctPerformerComp.score >= 45.0)

        assertTrue(correctPerformerComp.score > wrongPerformerComp.score + 40.0)
    }

    @Test
    fun ambiguityGuard_triggersWhenCandidatesCloseAndDifferentArtists() {
        val cand1 = SongItem(
            id = "yt_1",
            title = "Track Title",
            artists = listOf(Artist(name = "Performer A", id = null)),
            duration = 180,
        )
        val cand2 = SongItem(
            id = "yt_2",
            title = "Track Title",
            artists = listOf(Artist(name = "Performer B", id = null)),
            duration = 180,
        )

        val scored = listOf(
            SpotifyTrackScorer.ScoredCandidate(
                candidate = cand1,
                totalScore = 40.0,
                titleScore = 30.0,
                artistScore = 5.0,
                languageScore = 0.0,
                officialScore = 0.0,
                durationScore = 5.0,
            ),
            SpotifyTrackScorer.ScoredCandidate(
                candidate = cand2,
                totalScore = 38.0,
                titleScore = 30.0,
                artistScore = 3.0,
                languageScore = 0.0,
                officialScore = 0.0,
                durationScore = 5.0,
            ),
        )

        val result = SpotifyTrackScorer.evaluateAmbiguity(scored, listOf("Performer A"), ambiguityDeltaThreshold = 15.0)
        assertTrue("Ambiguity guard must trigger when scores are close and artist sets differ", result is AmbiguityResult.Ambiguous)
        assertEquals(2, (result as AmbiguityResult.Ambiguous).topCandidates.size)
    }
}
