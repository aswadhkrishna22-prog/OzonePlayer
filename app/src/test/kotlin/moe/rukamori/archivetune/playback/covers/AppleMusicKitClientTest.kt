/*
 * Ozone Player (2026)
 * AppleMusicKitClient Unit Tests
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.covers

import org.junit.Assert.assertEquals
import org.junit.Test

class AppleMusicKitClientTest {

    @Test
    fun formatArtworkUrl_replacesWidthAndHeightTokens() {
        val template = "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/aa/bb/cc/image.jpg/{w}x{h}bb.{f}"
        val formatted = AppleMusicKitClient.formatArtworkUrl(template, 3000, 3000)
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/aa/bb/cc/image.jpg/3000x3000bb.jpg",
            formatted
        )
    }

    @Test
    fun formatArtworkUrl_handlesCustomDimensions() {
        val template = "https://is1-ssl.mzstatic.com/image/thumb/Music/{w}x{h}{c}.{f}"
        val formatted = AppleMusicKitClient.formatArtworkUrl(template, 1200, 1200)
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/1200x1200.jpg",
            formatted
        )
    }
}
