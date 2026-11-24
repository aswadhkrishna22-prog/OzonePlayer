/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp

object HomeLayoutConfig {
    val shelfSpacing: Dp = 10.dp
    val shelfHorizontalPadding: Dp = 16.dp
    val sidePadding: Dp = 16.dp
    val sectionSpacing: Dp = 18.dp
    val headerHeight: Dp = 48.dp

    fun cardWidth(availableWidth: Dp): Dp {
        val usableWidth = availableWidth - (shelfHorizontalPadding * 2)
        return (usableWidth / 2.35f).coerceIn(132.dp, 160.dp)
    }

    fun artistSize(availableWidth: Dp): Dp =
        (cardWidth(availableWidth) * 0.90f).coerceIn(122.dp, 146.dp)

    fun quickPicksHeroHeight(availableWidth: Dp): Dp = when {
        availableWidth >= 840.dp -> 370.dp
        availableWidth >= 600.dp -> 345.dp
        else -> 310.dp
    }

    fun heroCarouselHeight(
        availableWidth: Dp,
        screenHeight: Dp = Dp.Unspecified,
    ): Dp = when {
        availableWidth >= 840.dp -> 480.dp
        availableWidth >= 600.dp -> 420.dp
        screenHeight != Dp.Unspecified && screenHeight > 0.dp ->
            (screenHeight * 0.52f).coerceIn(340.dp, 440.dp)
        else -> 400.dp
    }
}
