/*
 * Ozone Player (2026)
 * AutoEq Parser Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.autoeq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqParserTest {

    @Test
    fun parseIndex_extractsValidEntries() {
        val sampleIndex = """
            # AutoEq Results Index
            - [Sony WH-1000XM4](./oratory1990/harman_over-ear_2018/Sony%20WH-1000XM4) by oratory1990 on Harman over-ear 2018
            - [Apple AirPods Pro (2nd generation)](./crinacle/harman_in-ear_2019v2/Apple%20AirPods%20Pro%20(2nd%20generation)) by crinacle on Harman in-ear 2019v2
            - [Sennheiser HD 600](./rtings/harman_over-ear_2018/Sennheiser%20HD%20600) by rtings on Harman over-ear 2018
            Random non-matching line
        """.trimIndent()

        val entries = AutoEqParser.parseIndex(sampleIndex)
        assertEquals(3, entries.size)

        assertEquals("Sony WH-1000XM4", entries[0].name)
        assertEquals("oratory1990", entries[0].source)
        assertEquals("./oratory1990/harman_over-ear_2018/Sony%20WH-1000XM4", entries[0].path)

        assertEquals("Apple AirPods Pro (2nd generation)", entries[1].name)
        assertEquals("crinacle", entries[1].source)

        assertEquals("Sennheiser HD 600", entries[2].name)
        assertEquals("rtings", entries[2].source)
    }

    @Test
    fun profileUrl_buildsCorrectRawGitHubUrl() {
        val entry = AutoEqEntry(
            name = "Sony WH-1000XM4",
            source = "oratory1990",
            path = "./oratory1990/harman_over-ear_2018/Sony%20WH-1000XM4",
        )

        val url = AutoEqParser.profileUrl(entry)
        assertTrue(url.startsWith("https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"))
        assertTrue(url.endsWith("Sony%20WH-1000XM4%20ParametricEQ.txt"))
    }

    @Test
    fun parseParametric_parsesPreampAndFilters() {
        val sampleParametric = """
            Preamp: -6.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
            Filter 2: ON PK Fc 240 Hz Gain -3.2 dB Q 1.40
            Filter 3: ON PK Fc 1250 Hz Gain 2.1 dB Q 2.00
            Filter 4: ON HSC Fc 10000 Hz Gain -1.8 dB Q 0.71
            Filter 5: OFF PK Fc 4000 Hz Gain 0.0 dB Q 1.00
        """.trimIndent()

        val profile = AutoEqParser.parseParametric(
            text = sampleParametric,
            name = "Test Headphone",
            source = "oratory1990",
        )

        assertEquals("Test Headphone", profile.name)
        assertEquals("oratory1990", profile.source)
        assertEquals(-6.4f, profile.preampDb, 0.001f)
        assertEquals(4, profile.filters.size)

        assertEquals(AutoEqFilterType.LOW_SHELF, profile.filters[0].type)
        assertEquals(105f, profile.filters[0].freqHz, 0.01f)
        assertEquals(5.5f, profile.filters[0].gainDb, 0.01f)
        assertEquals(0.70f, profile.filters[0].q, 0.01f)

        assertEquals(AutoEqFilterType.PEAK, profile.filters[1].type)
        assertEquals(240f, profile.filters[1].freqHz, 0.01f)
        assertEquals(-3.2f, profile.filters[1].gainDb, 0.01f)
        assertEquals(1.40f, profile.filters[1].q, 0.01f)

        assertEquals(AutoEqFilterType.HIGH_SHELF, profile.filters[3].type)
        assertEquals(10000f, profile.filters[3].freqHz, 0.01f)
        assertEquals(-1.8f, profile.filters[3].gainDb, 0.01f)
    }

    @Test
    fun search_matchesMultiWordTokens() {
        val entries = listOf(
            AutoEqEntry("Sony WH-1000XM4", "oratory1990", "./path1"),
            AutoEqEntry("Sony WH-1000XM5", "crinacle", "./path2"),
            AutoEqEntry("Sony WF-1000XM4", "rtings", "./path3"),
            AutoEqEntry("Apple AirPods Pro", "crinacle", "./path4"),
            AutoEqEntry("Sennheiser HD 600", "oratory1990", "./path5"),
        )

        val results1 = AutoEqParser.search(entries, "sony wh")
        assertEquals(2, results1.size)
        assertTrue(results1.all { it.name.startsWith("Sony WH") })

        val results2 = AutoEqParser.search(entries, "airpods pro")
        assertEquals(1, results2.size)
        assertEquals("Apple AirPods Pro", results2[0].name)

        val results3 = AutoEqParser.search(entries, "nonexistent")
        assertTrue(results3.isEmpty())
    }
}
