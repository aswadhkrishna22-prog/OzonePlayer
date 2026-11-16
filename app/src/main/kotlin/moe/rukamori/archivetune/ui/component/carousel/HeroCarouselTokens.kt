/*
 * Ozone Player (2026)
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object HeroCarouselTokens {
    const val HeroSlideMillis = 1150
    const val HeroAdvanceMillis = 5500L
    const val HeroRollIdleAdvanceMillis = 11000L
    val HeroSlideEasing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f)

    const val HeroStillFrom = 0.08f
    const val HeroDim = 0.36f

    val PageSwitchBlur = 18.dp
    const val PageMediaTravel = 0.18f
    const val PageTextTravel = 0.42f
    const val PageWipeFeather = 0.55f
    const val PageWipeTrail = 0.25f
    const val PageWipeCurveReach = 0.6f
    const val PageWipeFlat = 0.04f
    const val PageWipeSettled = 0.001f
    const val PageWipeHidden = 0.999f

    const val GachaDesignHeight = 527f
    const val GachaItemSize = 187f
    const val GachaFocusSize = 221f
    const val GachaPitch = 207f
    const val GachaFocusGap = 17f
    const val GachaCenterY = 230.5f
    const val GachaOverhang = 28f
    const val GachaCornerRadius = 25f
    const val GachaPointerX = 144f
    const val GachaPointerY = 236f
    const val GachaPointerWidth = 88.4f
    const val GachaPointerHeight = 97.5f

    const val GachaCandidates = 10
    const val GachaMinPool = 4
    const val GachaMinRollMillis = 3500
    const val GachaMaxRollMillis = 9000

    val GachaRollEasing = CubicBezierEasing(0.15f, 0.45f, 0.2f, 1f)
    const val IdleStepSeconds = 1f / 30f
    const val IdleWakeMillis = 28L

    const val TopShadeEnd = 0f
    const val BottomShadeStart = 0.85f

    val MinTouchTarget = 48.dp

    const val TopBlurHeightFraction = 0.39f
    const val TopBlurRadius = 18.7f
    const val BottomBlurHeightFraction = 0.50f
    const val BottomBlurRadius = 30f

    const val ScrimTopEnd = 0.39f
    const val ScrimBottomStart = 0.533f
    const val ScrimBottomEnd = 0.989f

    val ArtOverlayTextPrimary = Color(0xFFFFFFFF)
    val ArtOverlayTextSecondary = Color(0xD1FFFFFF)
    val ArtOverlayTextTertiary = Color(0xB8FFFFFF)
    val ArtOverlayButtonSurface = Color(0xE0FFFFFF)
    val ArtOverlayButtonContent = Color(0xFF1E1E1E)
    val ArtOverlayButtonSecondarySurface = Color(0x47FFFFFF)

    val GachaPointerColor = Color(0xFFF0908D)
    val GachaPipColor = Color(0xFF3C3C3C)
    val GachaDefaultLight = Color(0xFFDEA0AC)
    val GachaDefaultDeep = Color(0xFF7C82B4)
}
