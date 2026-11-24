/*
 * Ozone Player (2026)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.settings

import androidx.compose.ui.graphics.Color

/** Obsidian + Vivid Acid Lime tokens (see Ozone design guide). Single source of truth for settings UI. */
object OzoneUi {
    // accent
    val Lime = Color(0xFFB6FF00)
    val LimeDark = Color(0xFF050505) // OnAcidLime
    val LimeContainer = Color(0xFF1B2905)
    val LimeBorder = Color(0x66B6FF00)
    val LimeGlow = Color(0x29B6FF00)

    // surfaces
    val Bg = Color(0xFF060606)
    val SurfaceCard = Color(0xFF111214)
    val SurfaceElevated = Color(0xFF17191D)
    val SurfaceInput = Color(0xFF0A0B0D)
    val CardTop = SurfaceCard
    val CardBottom = SurfaceCard

    // lines
    val Hairline = Color(0xFF1E2024) // BorderSubtle
    val BorderElevated = Color(0xFF282B32)
    val Divider = Color(0xFF1A1C20)

    // text
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E9299)
    val SectionLabel = Color(0xFF7E828A)
    val TextDisabled = Color(0xFF4E525A)
    val Chevron = Color(0xFF6C7078)

    // controls
    val TrackInactive = Color(0xFF23262C)
    val SwitchOffTrack = Color(0xFF1B1D22)
    val SwitchOffBorder = Color(0xFF343842)
    val SwitchOffThumb = Color(0xFF7E828A)
    val Destructive = Color(0xFFFF4D5A)
}
