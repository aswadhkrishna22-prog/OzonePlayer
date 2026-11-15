/*
 * Ozone Player (2026)
 * Unit tests for DeviceGraphicsClassifier & GraphicsPreset
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGraphicsClassifierTest {

    @Test
    fun graphicsPreset_lowTier_disablesHeavyEffects() {
        val preset = GraphicsPreset.LOW
        assertFalse(preset.blurNavBar)
        assertEquals(0f, preset.blurRadius, 0.001f)
        assertEquals(HomeBackgroundStyle.TONAL, preset.homeBackgroundStyle)
        assertFalse(preset.parallaxEnabled)
        assertFalse(preset.forceHighRefreshRate)
    }

    @Test
    fun graphicsPreset_mediumTier_balancedVisuals() {
        val preset = GraphicsPreset.MEDIUM
        assertTrue(preset.blurNavBar)
        assertEquals(16f, preset.blurRadius, 0.001f)
        assertEquals(HomeBackgroundStyle.MESH, preset.homeBackgroundStyle)
        assertFalse(preset.parallaxEnabled)
        assertFalse(preset.forceHighRefreshRate)
    }

    @Test
    fun graphicsPreset_highTier_enablesFullQuality() {
        val preset = GraphicsPreset.HIGH
        assertTrue(preset.blurNavBar)
        assertEquals(24f, preset.blurRadius, 0.001f)
        assertEquals(HomeBackgroundStyle.CIRCLES, preset.homeBackgroundStyle)
        assertTrue(preset.parallaxEnabled)
        assertTrue(preset.forceHighRefreshRate)
    }
}
