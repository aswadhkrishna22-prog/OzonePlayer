/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist

import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.utils.resolver.PlaybackFormatSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistDownloadSheetTest {

    @Test
    fun calculateDownloadEstimate_withValidDurations() {
        val songs = listOf(
            DownloadableSong(id = "1", title = "Song 1", artistNames = "Artist 1", durationSeconds = 180, thumbnailUrl = null),
            DownloadableSong(id = "2", title = "Song 2", artistNames = "Artist 2", durationSeconds = 120, thumbnailUrl = null),
        )
        // Total duration: 300 seconds
        // At 256 kbps (32,000 bytes/sec): 300 * 32,000 = 9,600,000 bytes
        val estimate = PlaylistDownloadCalculator.calculateDownloadEstimate(songs, bitrateBps = 256_000)

        assertEquals(300L, estimate.totalDurationSeconds)
        assertEquals(0, estimate.unknownDurationCount)
        assertEquals(9_600_000L, estimate.totalBytes)
    }

    @Test
    fun calculateDownloadEstimate_withUnknownDurations() {
        val songs = listOf(
            DownloadableSong(id = "1", title = "Song 1", artistNames = "Artist 1", durationSeconds = 200, thumbnailUrl = null),
            DownloadableSong(id = "2", title = "Song 2", artistNames = "Artist 2", durationSeconds = -1, thumbnailUrl = null),
            DownloadableSong(id = "3", title = "Song 3", artistNames = "Artist 3", durationSeconds = 0, thumbnailUrl = null),
        )
        val estimate = PlaylistDownloadCalculator.calculateDownloadEstimate(songs, bitrateBps = 160_000)

        assertEquals(200L, estimate.totalDurationSeconds)
        assertEquals(2, estimate.unknownDurationCount)
        // 200 * (160,000 / 8) = 200 * 20,000 = 4,000,000 bytes
        assertEquals(4_000_000L, estimate.totalBytes)
    }

    @Test
    fun formatDurationHoursMinutes_formatting() {
        assertEquals("0 min", PlaylistDownloadCalculator.formatDurationHoursMinutes(0))
        assertEquals("45s", PlaylistDownloadCalculator.formatDurationHoursMinutes(45))
        assertEquals("5 min", PlaylistDownloadCalculator.formatDurationHoursMinutes(300))
        assertEquals("1 hr 15 min", PlaylistDownloadCalculator.formatDurationHoursMinutes(4500))
        assertEquals("2 hr", PlaylistDownloadCalculator.formatDurationHoursMinutes(7200))
        assertEquals("2 hr 41 min", PlaylistDownloadCalculator.formatDurationHoursMinutes(9660))
    }

    @Test
    fun formatQualityChip_formatting() {
        assertEquals(
            "AAC, up to 256 kbps",
            PlaylistDownloadCalculator.formatQualityChip(AudioQuality.HIGHEST, networkMeteredOrLowData = false, bitrateBps = 256_000),
        )
        assertEquals(
            "Opus, up to 160 kbps",
            PlaylistDownloadCalculator.formatQualityChip(AudioQuality.HIGH, networkMeteredOrLowData = false, bitrateBps = 160_000),
        )
        assertEquals(
            "Opus, up to 70 kbps",
            PlaylistDownloadCalculator.formatQualityChip(AudioQuality.LOW, networkMeteredOrLowData = false, bitrateBps = 70_000),
        )
        assertEquals(
            "Opus, up to 160 kbps",
            PlaylistDownloadCalculator.formatQualityChip(AudioQuality.AUTO, networkMeteredOrLowData = true, bitrateBps = 160_000),
        )
    }

    @Test
    fun formatSelector_bitratesMatchQuality() {
        // Unmetered
        assertEquals(AudioQuality.HIGHEST, PlaybackFormatSelector.resolveEffectiveAudioQuality(AudioQuality.AUTO, networkMetered = false))
        assertEquals(256_000, PlaybackFormatSelector.resolveTargetBitrateBps(AudioQuality.AUTO, networkMetered = false))

        // Metered
        assertEquals(AudioQuality.HIGH, PlaybackFormatSelector.resolveEffectiveAudioQuality(AudioQuality.AUTO, networkMetered = true))
        assertEquals(160_000, PlaybackFormatSelector.resolveTargetBitrateBps(AudioQuality.AUTO, networkMetered = true))

        // Low
        assertEquals(AudioQuality.LOW, PlaybackFormatSelector.resolveEffectiveAudioQuality(AudioQuality.LOW, networkMetered = false))
        assertEquals(70_000, PlaybackFormatSelector.resolveTargetBitrateBps(AudioQuality.LOW, networkMetered = false))
    }
}
