/*
 * Ozone Player (2026) | Beast Mode Audio Engine Unit Tests
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.beastmode

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeastModeTest {

    @Test
    fun testCalculateBeastCurve5Bands() {
        val curve = BeastModeManager.calculateBeastCurve(5)
        assertEquals(5, curve.size)
        // Subwoofer (60Hz): +3.5 dB, Punch (230Hz): +1.6 dB, Vocals (910Hz): +0.5 dB, Presence (3.6kHz): +2.4 dB, Tweeters (14kHz): +3.5 dB
        assertEquals(listOf(350, 162, 50, 237, 350), curve)
    }

    @Test
    fun testCalculateBeastCurve10Bands() {
        val curve = BeastModeManager.calculateBeastCurve(10)
        assertEquals(10, curve.size)
        assertEquals(listOf(350, 300, 200, 50, 0, 100, 200, 250, 300, 350), curve)
    }

    @Test
    fun testCalculateBeastCurveEdgeCases() {
        assertEquals(emptyList<Int>(), BeastModeManager.calculateBeastCurve(0))
        assertEquals(emptyList<Int>(), BeastModeManager.calculateBeastCurve(-5))
        assertEquals(listOf(350), BeastModeManager.calculateBeastCurve(1))
    }

    @Test
    fun testCalculateBeastCurveVCurveShape() {
        for (bands in 3..16) {
            val curve = BeastModeManager.calculateBeastCurve(bands)
            assertEquals(bands, curve.size)
            // Left end (sub-bass) and right end (highs) must be at peak boost (+350 mB)
            assertEquals(350, curve.first())
            assertEquals(350, curve.last())
            // Center band must be lower than ends
            val mid = curve[bands / 2]
            assertTrue("Center band $mid should be less than peak 350", mid < 350)
        }
    }

    @Test
    fun testBeastModeBackupStateSerialization() {
        val backup = BeastModeBackupState(
            eqEnabled = true,
            selectedProfileId = "rock",
            bandLevelsRaw = "[300, 200, 0, 200, 300]",
            outputGainEnabled = false,
            outputGainMb = 0,
            bassBoostEnabled = true,
            bassBoostStrength = 200,
            virtualizerEnabled = false,
            virtualizerStrength = 0,
            audioNormalization = true,
        )

        val jsonString = BeastModeManager.json.encodeToString(backup)
        val decoded = BeastModeManager.json.decodeFromString<BeastModeBackupState>(jsonString)

        assertEquals(backup, decoded)
        assertTrue(decoded.eqEnabled)
        assertEquals("rock", decoded.selectedProfileId)
        assertEquals(200, decoded.bassBoostStrength)
        assertTrue(decoded.audioNormalization)
    }

    @Test
    fun testDismissPromptClosesDialog() {
        BeastModeManager.onDismissPrompt()
        assertFalse(BeastModeManager.showPromptDialog.value)
    }
}
