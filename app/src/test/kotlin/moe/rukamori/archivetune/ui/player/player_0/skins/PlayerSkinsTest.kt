/*
 * Ozone Player (2026)
 * Unit tests for Creative Player Skins (Cassette, Halo, Polaroid)
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.player.player_0.skins

import moe.rukamori.archivetune.constants.PlayerSkinPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSkinsTest {

    @Test
    fun playerSkinPreference_next_cyclesCorrectly() {
        assertEquals(PlayerSkinPreference.CASSETTE, PlayerSkinPreference.DEFAULT.next())
        assertEquals(PlayerSkinPreference.HALO, PlayerSkinPreference.CASSETTE.next())
        assertEquals(PlayerSkinPreference.POLAROID, PlayerSkinPreference.HALO.next())
        assertEquals(PlayerSkinPreference.DEFAULT, PlayerSkinPreference.POLAROID.next())
    }

    @Test
    fun playerSkinPreference_labels_areHumanReadable() {
        assertEquals("Classic", PlayerSkinPreference.DEFAULT.label)
        assertEquals("Cassette", PlayerSkinPreference.CASSETTE.label)
        assertEquals("Halo", PlayerSkinPreference.HALO.label)
        assertEquals("Polaroid", PlayerSkinPreference.POLAROID.label)
    }

    @Test
    fun haloLevel_notPlaying_returnsRestingBaseLevel() {
        assertEquals(0.08f, haloLevel(i = 0, t = 0f, playing = false), 0.0001f)
        assertEquals(0.08f, haloLevel(i = 36, t = 10f, playing = false), 0.0001f)
        assertEquals(0.08f, haloLevel(i = 71, t = 100f, playing = false), 0.0001f)
    }

    @Test
    fun haloLevel_playing_staysWithinValidNormalizedBounds() {
        for (i in 0 until 72) {
            for (tSec in listOf(0f, 0.5f, 1.25f, 5.0f, 12.3f, 45.6f)) {
                val level = haloLevel(i = i, t = tSec, playing = true)
                assertTrue("Level $level for bar $i at t=$tSec out of [0, 1] bounds", level in 0f..1f)
            }
        }
    }
}
