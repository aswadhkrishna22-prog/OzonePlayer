/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.resolvers

import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import moe.rukamori.archivetune.flaccore.streaming.FlacStreamRegistry
import moe.rukamori.archivetune.flaccore.streaming.FlacStreamUrl
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnCandidate
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnDecryptor
import moe.rukamori.archivetune.flaccore.streaming.JioSaavnStreamResolver
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JioSaavnPlaybackResolutionTest {

    private val resolver = JioSaavnStreamResolver(OkHttpClient())

    @Test
    fun decryptMediaUrl_decryptsValidToken() {
        // Real JioSaavn encrypted media token
        val encryptedToken = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyEiobTeK5J0VjLPSx8qZFoyTWpplsSzgQJaPdf6syCa8ZKCjH1RLLlRw7tS9a8Gtq"
        val decrypted = JioSaavnDecryptor.decryptMediaUrl(encryptedToken)

        assertNotNull(decrypted)
        assertEquals("https://aac.saavncdn.com/535/01c78b530edbbeff6f744cb2cc3e1c41_96.mp4", decrypted)
    }

    @Test
    fun decryptMediaUrl_returnsNullForInvalidToken() {
        assertNull(JioSaavnDecryptor.decryptMediaUrl(""))
        assertNull(JioSaavnDecryptor.decryptMediaUrl("not-valid-base64-or-des"))
    }

    @Test
    fun to320KbpsUrl_upgradesTo320() {
        val raw96 = "https://aac.saavncdn.com/535/01c78b530edbbeff6f744cb2cc3e1c41_96.mp4"
        val upgraded320 = JioSaavnDecryptor.to320KbpsUrl(raw96)
        assertEquals("https://aac.saavncdn.com/535/01c78b530edbbeff6f744cb2cc3e1c41_320.mp4", upgraded320)

        val raw160 = "https://aac.saavncdn.com/123/sample_160.mp4"
        assertEquals("https://aac.saavncdn.com/123/sample_320.mp4", JioSaavnDecryptor.to320KbpsUrl(raw160))

        val rawM4a = "https://aac.saavncdn.com/999/song_96.m4a"
        assertEquals("https://aac.saavncdn.com/999/song_320.m4a", JioSaavnDecryptor.to320KbpsUrl(rawM4a))
    }

    @Test
    fun parseSearchResponse_extractsCandidatesCorrectly() {
        val jsonSample = """
            {
              "total": 2,
              "start": 0,
              "results": [
                {
                  "id": "trk_001",
                  "title": "Samajavaragamana (Malayalam)",
                  "subtitle": "Vijay Yesudas, Thaman S - Ala Vaikunthapurramuloo (Malayalam)",
                  "image": "https://c.saavncdn.com/535/sample-150x150.jpg",
                  "more_info": {
                    "album": "Ala Vaikunthapurramuloo (Malayalam)",
                    "duration": "215",
                    "encrypted_media_url": "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyEiobTeK5J0VjLPSx8qZFoyTWpplsSzgQJaPdf6syCa8ZKCjH1RLLlRw7tS9a8Gtq",
                    "artistMap": {
                      "primary_artists": [
                        { "name": "Vijay Yesudas" }
                      ],
                      "artists": [
                        { "name": "Vijay Yesudas" },
                        { "name": "Thaman S" }
                      ]
                    }
                  }
                },
                {
                  "id": "trk_002",
                  "title": "Samajavaragamana",
                  "subtitle": "Sid Sriram, Thaman S - Ala Vaikunthapurramuloo",
                  "image": "https://c.saavncdn.com/535/telugu-150x150.jpg",
                  "more_info": {
                    "album": "Ala Vaikunthapurramuloo",
                    "duration": "220",
                    "encrypted_media_url": "dummyEncryptedUrl2",
                    "artistMap": {
                      "primary_artists": [
                        { "name": "Sid Sriram" }
                      ],
                      "artists": [
                        { "name": "Sid Sriram" },
                        { "name": "Thaman S" }
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val candidates = resolver.parseSearchResponse(jsonSample)
        assertEquals(2, candidates.size)

        val first = candidates[0]
        assertEquals("trk_001", first.id)
        assertEquals("Samajavaragamana (Malayalam)", first.title)
        assertEquals(215L, first.durationSec)
        assertTrue(first.artists.contains("Vijay Yesudas"))
        assertEquals("ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyEiobTeK5J0VjLPSx8qZFoyTWpplsSzgQJaPdf6syCa8ZKCjH1RLLlRw7tS9a8Gtq", first.encryptedMediaUrl)
    }

    @Test
    fun pickBestCandidate_picksExactMatchArtistAndTitle() {
        val candidateMalayalam = JioSaavnCandidate(
            id = "1",
            title = "Samajavaragamana Malayalam",
            subtitle = "Vijay Yesudas, Thaman S",
            album = "Ala Vaikunthapurramuloo",
            durationSec = 215,
            artists = listOf("Vijay Yesudas", "Thaman S"),
            encryptedMediaUrl = "token1",
            imageUrl = null,
        )
        val candidateTelugu = JioSaavnCandidate(
            id = "2",
            title = "Samajavaragamana",
            subtitle = "Sid Sriram, Thaman S",
            album = "Ala Vaikunthapurramuloo",
            durationSec = 220,
            artists = listOf("Sid Sriram", "Thaman S"),
            encryptedMediaUrl = "token2",
            imageUrl = null,
        )

        val queryMalayalam = TrackQuery(
            title = "Samajavaragamana (Malayalam)",
            artist = "Vijay Yesudas, Thaman S",
            durationMs = 215_000L,
        )

        val bestMalayalam = resolver.pickBestCandidate(listOf(candidateMalayalam, candidateTelugu), queryMalayalam)
        assertNotNull(bestMalayalam)
        assertEquals("1", bestMalayalam?.id)
        assertTrue(bestMalayalam?.artists?.contains("Vijay Yesudas") == true)

        val queryTelugu = TrackQuery(
            title = "Samajavaragamana",
            artist = "Sid Sriram, Thaman S",
            durationMs = 220_000L,
        )

        val bestTelugu = resolver.pickBestCandidate(listOf(candidateMalayalam, candidateTelugu), queryTelugu)
        assertNotNull(bestTelugu)
        assertEquals("2", bestTelugu?.id)
        assertTrue(bestTelugu?.artists?.contains("Sid Sriram") == true)
    }

    @Test
    fun pickBestCandidate_rejectsUnrelatedCandidate() {
        val unrelatedCandidate = JioSaavnCandidate(
            id = "99",
            title = "Shape of You",
            subtitle = "Ed Sheeran",
            album = "Divide",
            durationSec = 233,
            artists = listOf("Ed Sheeran"),
            encryptedMediaUrl = "token99",
            imageUrl = null,
        )

        val query = TrackQuery(
            title = "Kuthanthram",
            artist = "Sushin Shyam",
            durationMs = 180_000L,
        )

        val best = resolver.pickBestCandidate(listOf(unrelatedCandidate), query)
        assertNull(best)
    }

    @Test
    fun buildSearchTerms_stripsNoiseAndCreatesSearchVariations() {
        val query = TrackQuery(
            title = "Illuminati (From \"Aavesham\") [Official Music Video]",
            artist = "Sushin Shyam, Dabzee",
            durationMs = 190_000L,
        )

        val terms = resolver.buildSearchTerms(query)
        assertTrue(terms.isNotEmpty())
        assertTrue(terms.any { it.contains("Illuminati") && it.contains("Sushin Shyam") })
    }

    @Test
    fun flacStreamRegistry_prioritizesJioSaavnWhenAvailable() = runTest {
        val jioSaavnResult = FlacStreamUrl(
            url = "https://aac.saavncdn.com/320/stream.mp4",
            expiresAtMs = 0L,
            codec = "aac",
            bitrateKbps = 320,
            origin = "jiosaavn",
        )
        val qbdlxResult = FlacStreamUrl(
            url = "https://qbdlx.stream/flac.flac",
            expiresAtMs = 0L,
            codec = "flac",
            bitrateKbps = 1411,
            origin = "qbdlx",
        )

        val registry = FlacStreamRegistry(
            jiosaavn = { _, _ -> jioSaavnResult },
            qbdlx = { _, _ -> qbdlxResult },
        )

        val query = TrackQuery(title = "Kuthanthram", artist = "Sushin Shyam", durationMs = 180000)
        val resolved = registry.resolve(query, 27)

        assertNotNull(resolved)
        assertEquals("jiosaavn", resolved?.origin)
        assertEquals(320, resolved?.bitrateKbps)
        assertEquals("https://aac.saavncdn.com/320/stream.mp4", resolved?.url)
    }

    @Test
    fun flacStreamRegistry_fallsBackToQbdlxWhenJioSaavnReturnsNull() = runTest {
        val qbdlxResult = FlacStreamUrl(
            url = "https://qbdlx.stream/flac.flac",
            expiresAtMs = 0L,
            codec = "flac",
            bitrateKbps = 1411,
            origin = "qbdlx",
        )

        val registry = FlacStreamRegistry(
            jiosaavn = { _, _ -> null },
            qbdlx = { _, _ -> qbdlxResult },
        )

        val query = TrackQuery(title = "Random Track", artist = "Random Artist", durationMs = 180000)
        val resolved = registry.resolve(query, 27)

        assertNotNull(resolved)
        assertEquals("qbdlx", resolved?.origin)
        assertEquals(1411, resolved?.bitrateKbps)
    }
}
