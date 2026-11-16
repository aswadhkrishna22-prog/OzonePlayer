/*
 * Ozone Player (2026)
 * RadioRepository Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioRepositoryTest {

    @Test
    fun addCustomStation_configuresHlsAndCodec() {
        val hlsStation = RadioRepository.addCustomStation(
            name = "Test HLS Stream",
            streamUrl = "https://stream.example.com/live/playlist.m3u8",
        )

        assertTrue(hlsStation.isCustom)
        assertTrue(hlsStation.uuid.startsWith("custom-"))
        assertTrue(hlsStation.hls)
        assertEquals("HLS", hlsStation.codec)
        assertEquals("Test HLS Stream", hlsStation.name)

        val mp3Station = RadioRepository.addCustomStation(
            name = "Test MP3 Stream",
            streamUrl = "http://icecast.example.com:8000/stream.mp3",
        )

        assertTrue(mp3Station.isCustom)
        assertFalse(mp3Station.hls)
        assertEquals("MP3/AAC", mp3Station.codec)
    }

    @Test
    fun toggleFavorite_addsAndRemovesStation() {
        val station = RadioStation(
            uuid = "test-uuid-fav",
            name = "Favorite Radio",
            streamUrl = "http://stream.example.com/test",
        )

        assertFalse(RadioRepository.isFavorite(station.uuid))

        RadioRepository.toggleFavorite(station)
        assertTrue(RadioRepository.isFavorite(station.uuid))

        RadioRepository.toggleFavorite(station)
        assertFalse(RadioRepository.isFavorite(station.uuid))
    }
}
