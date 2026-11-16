/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ColorExtractorTest {

    @Test
    fun extractVibrantHex_emptyPixels_returnsNull() {
        val result = ColorExtractor.extractVibrantHex(intArrayOf())
        assertNull(result)
    }

    @Test
    fun extractVibrantHex_validPixels_returnsValidHexFormat() {
        val pixels = IntArray(100) { 0xFFED5564.toInt() }
        val result = ColorExtractor.extractVibrantHex(pixels)
        assertNotNull(result)
        assertEquals(9, result?.length) // #AARRGGBB
        assertEquals('#', result?.first())
    }
}
