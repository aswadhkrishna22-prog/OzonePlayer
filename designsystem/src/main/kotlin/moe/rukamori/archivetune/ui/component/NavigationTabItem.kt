package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import moe.rukamori.archivetune.constants.NavigationTabContentPaddingVertical
import moe.rukamori.archivetune.constants.NavigationTabIconSize
import moe.rukamori.archivetune.constants.NavigationTabLabelSize
import moe.rukamori.archivetune.constants.NavigationTabLabelTopPadding
import moe.rukamori.archivetune.constants.NavigationTabPressedScale
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSelectorInitialScale
import moe.rukamori.archivetune.constants.NavigationTabSelectorVisibleThreshold
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable

@Composable
internal fun RowScope.NavigationTabItem(
    screen: Screens,
    selected: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    drawSelector: Boolean = false,
    proximity: Float = if (selected) 1f else 0f,
) {
    val clampedProximity = proximity.coerceIn(0f, 1f)
    val activeColor = NavBarColors.iconActive(pureBlack)
    val inactiveColor = NavBarColors.iconInactive(pureBlack)

    val contentColor = androidx.compose.ui.graphics.lerp(
        start = inactiveColor,
        stop = activeColor,
        fraction = clampedProximity,
    )

    val iconScale = 1.0f + 0.10f * clampedProximity

    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(
                horizontal = NavigationTabSlotPaddingHorizontal,
                vertical = NavigationTabSlotPaddingVertical,
            )
            .clip(CircleShape)
            .yumaCombinedClickable(
                pressedScale = NavigationTabPressedScale,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (drawSelector && clampedProximity > NavigationTabSelectorVisibleThreshold) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = lerp(NavigationTabSelectorInitialScale, 1.0f, clampedProximity)
                        scaleX = scale
                        scaleY = scale
                        alpha = clampedProximity
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha),
                        shape = CircleShape,
                    ),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = NavigationTabContentPaddingVertical),
        ) {
            Icon(
                painter = painterResource(if (clampedProximity >= 0.5f) screen.iconIdActive else screen.iconIdInactive),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(NavigationTabIconSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Text(
                text = stringResource(screen.titleId),
                color = contentColor,
                fontSize = NavigationTabLabelSize,
                fontWeight = if (clampedProximity >= 0.5f) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(top = NavigationTabLabelTopPadding),
            )
        }
    }
}
