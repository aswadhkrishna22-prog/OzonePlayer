/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JioSaavnFallbackUrlTest {

    @Before
    fun setUp() {
        JioSaavnQualityCache.clear()
    }

    @Test
    fun urlBuilders_generateExpectedQualitiesForMp4AndM4a() {
        val raw96 = "https://aac.saavncdn.com/535/sample_96.mp4"
        assertEquals("https://aac.saavncdn.com/535/sample_320.mp4", JioSaavnDecryptor.to320KbpsUrl(raw96))
        assertEquals("https://aac.saavncdn.com/535/sample_160.mp4", JioSaavnDecryptor.to160KbpsUrl(raw96))
        assertEquals("https://aac.saavncdn.com/535/sample_96.mp4", JioSaavnDecryptor.to96KbpsUrl(raw96))

        val raw160 = "https://aac.saavncdn.com/123/track_160.m4a"
        assertEquals("https://aac.saavncdn.com/123/track_320.m4a", JioSaavnDecryptor.to320KbpsUrl(raw160))
        assertEquals("https://aac.saavncdn.com/123/track_160.m4a", JioSaavnDecryptor.to160KbpsUrl(raw160))
        assertEquals("https://aac.saavncdn.com/123/track_96.m4a", JioSaavnDecryptor.to96KbpsUrl(raw160))
    }

    @Test
    fun buildFallbackSequence_enforcesStrictFallbackOrder_320_then_160_then_original() {
        val raw96 = "https://aac.saavncdn.com/535/track_96.mp4"
        val sequence = JioSaavnDecryptor.buildFallbackSequence(raw96)

        assertEquals(3, sequence.size)
        assertEquals(JioSaavnQualityTier.KBPS_320, sequence[0].first)
        assertEquals("https://aac.saavncdn.com/535/track_320.mp4", sequence[0].second)

        assertEquals(JioSaavnQualityTier.KBPS_160, sequence[1].first)
        assertEquals("https://aac.saavncdn.com/535/track_160.mp4", sequence[1].second)

        assertEquals(JioSaavnQualityTier.ORIGINAL, sequence[2].first)
        assertEquals("https://aac.saavncdn.com/535/track_96.mp4", sequence[2].second)
    }

    @Test
    fun buildFallbackSequence_avoidsDuplicateUrlsWhenRawIsAlready160() {
        val raw160 = "https://aac.saavncdn.com/535/track_160.mp4"
        val sequence = JioSaavnDecryptor.buildFallbackSequence(raw160)

        // Must still have fallback progression: 320 -> 160 -> 96
        assertEquals(3, sequence.size)
        assertEquals("https://aac.saavncdn.com/535/track_320.mp4", sequence[0].second)
        assertEquals("https://aac.saavncdn.com/535/track_160.mp4", sequence[1].second)
        assertEquals("https://aac.saavncdn.com/535/track_96.mp4", sequence[2].second)
    }

    @Test
    fun getNextFallback_progressesOneStepAtATime_thenStops() {
        val raw = "https://aac.saavncdn.com/535/track_96.mp4"
        val url320 = "https://aac.saavncdn.com/535/track_320.mp4"

        // Step 1: 320 fails -> fallback to 160
        val step1 = JioSaavnDecryptor.getNextFallback(url320, raw)
        assertNotNull(step1)
        assertEquals(JioSaavnQualityTier.KBPS_160, step1!!.first)
        assertEquals("https://aac.saavncdn.com/535/track_160.mp4", step1.second)

        // Step 2: 160 fails -> fallback to original (96)
        val step2 = JioSaavnDecryptor.getNextFallback(step1.second, raw)
        assertNotNull(step2)
        assertEquals(JioSaavnQualityTier.ORIGINAL, step2!!.first)
        assertEquals("https://aac.saavncdn.com/535/track_96.mp4", step2.second)

        // Step 3: original fails -> all fail, returns null
        val step3 = JioSaavnDecryptor.getNextFallback(step2.second, raw)
        assertNull(step3)
    }

    @Test
    fun qualityCache_remembersWorkingQualityAndSkipsFailed320() {
        val songId = "jiosaavn_test_track_123"
        val rawUrl = "https://aac.saavncdn.com/999/song_96.mp4"

        // Initially default tier is 320
        assertEquals(JioSaavnQualityTier.KBPS_320, JioSaavnQualityCache.getWorkingTier(songId))

        // Store raw URL and simulate 320 failure -> fallback to 160
        JioSaavnQualityCache.storeRawUrl(songId, rawUrl)
        JioSaavnQualityCache.recordWorkingTier(songId, JioSaavnQualityTier.KBPS_160)

        // Next time track is queried, cache immediately provides 160 kbps tier
        assertEquals(JioSaavnQualityTier.KBPS_160, JioSaavnQualityCache.getWorkingTier(songId))
        assertEquals(rawUrl, JioSaavnQualityCache.getRawUrl(songId))
        assertEquals("https://aac.saavncdn.com/999/song_160.mp4", JioSaavnDecryptor.getUrlForTier(rawUrl, JioSaavnQualityCache.getWorkingTier(songId)))
    }
}
