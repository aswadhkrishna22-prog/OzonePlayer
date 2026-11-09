package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical

@Composable
internal fun NavigationDragSelectorOverlay(
    selectorState: NavigationTabSelectorState,
    modifier: Modifier = Modifier,
) {
    if (selectorState.tabWidthPx <= 0f) return

    val selectorColor = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha)
    val animationsDisabled = selectorState.animationsDisabledProvider()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = NavigationTabSlotPaddingVertical)
                .layout { measurable, constraints ->
                    val w = selectorState.tabWidthPx.toInt()
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = w, maxWidth = w),
                    )
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                .graphicsLayer {
                    val x = selectorState.selectorXAnimatable.value
                    val v = selectorState.selectorXAnimatable.velocity
                    val stretch = if (animationsDisabled) {
                        0f
                    } else {
                        NavHighlightStretchFactor * kotlin.math.min(
                            kotlin.math.abs(v) / NavHighlightVelocityDivisor,
                            NavHighlightMaxStretch,
                        )
                    }
                    val sX = 1f + stretch
                    val sY = 1f / kotlin.math.sqrt(sX)

                    translationX = x - (selectorState.tabWidthPx / 2f)
                    scaleX = sX
                    scaleY = sY
                }
                .background(
                    color = selectorColor,
                    shape = RoundedCornerShape(percent = 50),
                ),
        )
    }
}

@Composable
internal fun NavigationDragSelectorOverlay(
    dragState: NavigationBarGestureState,
    modifier: Modifier = Modifier,
) {
    NavigationDragSelectorOverlay(
        selectorState = dragState.selectorState,
        modifier = modifier,
    )
}
