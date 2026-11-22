package moe.rukamori.archivetune

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.FloatingToolbarBottomPadding
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.MiniPlayerOnlyOffset
import moe.rukamori.archivetune.constants.MiniPlayerWithNavBarOffset
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth

internal object PlayerDockGeometry {
    val miniPlayerHeight: Dp = MiniPlayerHeight

    fun collapsedHorizontalInset(
        availableWidth: Dp,
        useRail: Boolean,
    ): Dp {
        if (useRail) return FloatingToolbarHorizontalPadding
        val centeredInset = (availableWidth - NavigationBarMaxWidth) / 2 + FloatingToolbarHorizontalPadding
        return maxOf(FloatingToolbarHorizontalPadding, centeredInset)
    }

    fun navigationBarBottomOffset(
        floatingToolbarBottomPadding: Dp,
        navigationBarHeight: Dp = FloatingToolbarHeight,
    ): Dp = floatingToolbarBottomPadding + navigationBarHeight

    fun sheetCollapsedOffset(
        safeBottomPadding: Dp,
        bottomBarHeight: Dp,
        navBarHeight: Dp = FloatingToolbarHeight,
    ): Dp {
        val progress = if (navBarHeight > 0.dp) {
            (bottomBarHeight / navBarHeight).coerceIn(0f, 1f)
        } else 0f
        val navContribution = (navBarHeight + FloatingToolbarBottomPadding) * progress
        return safeBottomPadding + navContribution + MiniPlayerBottomSpacing + MiniPlayerHeight
    }

    fun sheetCollapsedBound(
        bottomInset: Dp,
        shouldShowNav: Boolean,
        useRail: Boolean,
        navBarHeight: Dp = FloatingToolbarHeight,
    ): Dp = bottomInset + if (shouldShowNav && !useRail) {
        FloatingToolbarBottomPadding + navBarHeight + MiniPlayerBottomSpacing + MiniPlayerHeight
    } else {
        MiniPlayerOnlyOffset
    }

    fun reservedBottomSpace(
        bottomInset: Dp,
        floatingToolbarBottomPadding: Dp,
        shouldShowNavigationBar: Boolean,
        isMiniPlayerVisible: Boolean,
        useRail: Boolean,
        navBarHeight: Dp = FloatingToolbarHeight,
    ): Dp {
        val navBottom = if (shouldShowNavigationBar && !useRail) {
            navigationBarBottomOffset(floatingToolbarBottomPadding, navBarHeight)
        } else {
            bottomInset
        }
        val miniPlayerOffset = if (isMiniPlayerVisible) {
            MiniPlayerHeight + MiniPlayerBottomSpacing
        } else {
            0.dp
        }
        return navBottom + miniPlayerOffset
    }
}
