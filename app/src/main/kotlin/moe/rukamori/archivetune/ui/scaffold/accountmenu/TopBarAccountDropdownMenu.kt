/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.scaffold.accountmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.clearLiquidGlassPill
import moe.rukamori.archivetune.ui.component.liquidBubbleHighlight
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun TopBarAccountDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    blurNavBar: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cornerRadius = 22.dp
    val shape = RoundedCornerShape(cornerRadius)
    val menuTitle = stringResource(R.string.more_options)
    val isHazeActive = blurNavBar && hazeState != null
    val surfaceColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh

    val glassModifier = if (isHazeActive) {
        Modifier
            .clearLiquidGlassPill(
                shape = shape,
                hazeState = hazeState,
                pureBlack = pureBlack,
                blurRadius = if (blurRadius > 0f) blurRadius.dp else 4.dp,
                tintAlpha = if (pureBlack) 0.14f else 0.09f,
                showBorder = true,
                cornerRadiusDp = cornerRadius,
                shadowElevation = 10.dp,
            )
            .border(
                width = SettingsDimensions.GlassBorderThickness,
                color = if (pureBlack) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.22f),
                shape = shape,
            )
    } else {
        Modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(surfaceColor.copy(alpha = if (pureBlack) 0.92f else 0.88f))
            .liquidBubbleHighlight(
                cornerRadiusDp = cornerRadius,
                isDark = true,
                hasTopSheen = true,
            )
            .border(
                width = SettingsDimensions.GlassBorderThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = shape,
            )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        shape = shape,
        modifier = modifier
            .widthIn(min = 230.dp, max = 290.dp)
            .then(glassModifier)
            .semantics { paneTitle = menuTitle },
        content = content,
    )
}
