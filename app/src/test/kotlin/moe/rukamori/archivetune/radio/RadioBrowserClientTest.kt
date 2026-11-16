/*
 * Ozone Player (2026)
 * RadioBrowserClient Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserClientTest {

    private val sampleJson = """
        [
          {
            "changeuuid": "8e37e954-5a21-4f11-9a25-a130f14d8692",
            "stationuuid": "96030c6a-0601-11e8-ae97-52543be04c81",
            "serveruuid": null,
            "name": "Dance Wave!",
            "url": "http://dancewave.online/dance.mp3",
            "url_resolved": "https://dancewave.online/dance.mp3",
            "homepage": "https://dancewave.online/",
            "favicon": "https://dancewave.online/logo.png",
            "tags": "dance,electronic,club",
            "country": "Hungary",
            "countrycode": "HU",
            "iso_3166_2": null,
            "state": "Budapest",
            "language": "english",
            "languagecodes": "en",
            "votes": 5231,
            "lastchangetime": "2024-01-15 12:00:00",
            "codec": "MP3",
            "bitrate": 128,
            "hls": 0,
            "lastcheckok": 1,
            "clickcount": 25039
          },
          {
            "stationuuid": "broken-station",
            "name": "No Stream Station",
            "url": "",
            "url_resolved": ""
          }
        ]
    """.trimIndent()

    @Test
    fun parseStations_extractsValidStationsAndFiltersBrokenStreams() {
        val stations = RadioBrowserClient.parseStations(sampleJson)
        assertEquals(1, stations.size)

        val station = stations[0]
        assertEquals("96030c6a-0601-11e8-ae97-52543be04c81", station.uuid)
        assertEquals("Dance Wave!", station.name)
        assertEquals("https://dancewave.online/dance.mp3", station.streamUrl)
        assertEquals("https://dancewave.online/logo.png", station.favicon)
        assertEquals("MP3", station.codec)
        assertEquals(128, station.bitrate)
        assertEquals("Hungary", station.country)
        assertEquals("HU", station.countryCode)
        assertEquals(listOf("dance", "electronic", "club"), station.tags)
    }

    @Test
    fun curatedGenres_containsEssentialCategories() {
        val tags = RadioBrowserClient.CURATED_GENRES.map { it.tag }
        assertTrue(tags.contains("popular"))
        assertTrue(tags.contains("bollywood"))
        assertTrue(tags.contains("malayalam"))
        assertTrue(tags.contains("lofi"))
        assertTrue(tags.contains("jazz"))
        assertTrue(tags.contains("rock"))
    }
}
