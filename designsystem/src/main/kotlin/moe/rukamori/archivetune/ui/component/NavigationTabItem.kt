package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.ozoneCombinedClickable

import moe.rukamori.archivetune.constants.NavigationTabIconSize
import moe.rukamori.archivetune.constants.NavigationTabPressedScale
import moe.rukamori.archivetune.constants.NavigationTabSelectorInitialScale
import moe.rukamori.archivetune.constants.NavigationTabSelectorVisibleThreshold
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSelectionDurationMs
import moe.rukamori.archivetune.constants.NavigationTabContentDurationMs
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical

@Composable
internal fun RowScope.NavigationTabItem(
    screen: Screens,
    selected: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    drawSelector: Boolean = false,
) {
    val selectionFactor by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(
            durationMillis = NavigationTabSelectionDurationMs,
            easing = LinearOutSlowInEasing,
        ),
        label = "SelectionFactor",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            NavBarColors.iconActive(pureBlack)
        } else {
            NavBarColors.iconInactive(pureBlack)
        },
        animationSpec = tween(durationMillis = NavigationTabContentDurationMs),
        label = "ContentColor",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 0.94f,
        animationSpec = spring(
            dampingRatio = 0.52f,
            stiffness = 380f,
        ),
        label = "IconScale",
    )

    val itemWeight = if (selected) 1.85f else 1.0f

    Box(
        modifier = modifier
            .weight(itemWeight)
            .fillMaxHeight()
            .padding(
                horizontal = NavigationTabSlotPaddingHorizontal,
                vertical = NavigationTabSlotPaddingVertical,
            )
            .clip(CircleShape)
            .ozoneCombinedClickable(
                pressedScale = NavigationTabPressedScale,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (drawSelector && selectionFactor > NavigationTabSelectorVisibleThreshold) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = lerp(NavigationTabSelectorInitialScale, 1.0f, selectionFactor)
                        scaleX = scale
                        scaleY = scale
                        alpha = selectionFactor
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha),
                        shape = CircleShape,
                    ),
            )
        }

        Box(
            modifier = Modifier
                .height(40.dp)
                .wrapContentWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selectionFactor > 0.05f) {
                        if (pureBlack) {
                            Color.White.copy(alpha = 0.20f * selectionFactor)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = selectionFactor)
                        }
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = if (selected) 12.dp else 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.wrapContentSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                    contentDescription = stringResource(screen.titleId),
                    tint = contentColor,
                    modifier = Modifier
                        .size(NavigationTabIconSize)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        expandFrom = Alignment.Start,
                    ),
                    exit = fadeOut(animationSpec = tween(100)) + shrinkHorizontally(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        shrinkTowards = Alignment.Start,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(6.dp))
                        StaggeredLetterReveal(
                            text = stringResource(screen.titleId),
                            tint = contentColor,
                            captured = false,
                        )
                    }
                }
            }
        }
    }
}
