package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical

@Composable
internal fun NavigationDragSelectorOverlay(
    selectorState: NavigationTabSelectorState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val verticalPaddingPx = with(density) { NavigationTabSlotPaddingVertical.toPx() }
    val strokeWidthPx = with(density) { 1.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                if (selectorState.tabWidthPx > 0f && selectorState.selectorWidthAnimatable.value > 0f) {
                    val currentWidth = selectorState.selectorWidthAnimatable.value
                    val currentX = selectorState.selectorXAnimatable.value
                    val height = size.height - (verticalPaddingPx * 2)
                    val top = verticalPaddingPx
                    val left = currentX - currentWidth / 2f
                    val cornerRadius = CornerRadius(height / 2f, height / 2f)

                    // 1. Subtle illuminated ambient aura behind pill
                    drawRoundRect(
                        color = Color(0x1800F2FE),
                        topLeft = Offset(left - 2f, top - 1f),
                        size = Size(currentWidth + 4f, height + 2f),
                        cornerRadius = CornerRadius((height + 2f) / 2f, (height + 2f) / 2f),
                    )

                    // 2. Liquid gradient pill fill
                    val liquidBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x3300F2FE),
                            Color(0x244FACFE),
                            Color(0x3300F2FE),
                        ),
                        startX = left,
                        endX = left + currentWidth,
                    )
                    drawRoundRect(
                        brush = liquidBrush,
                        topLeft = Offset(left, top),
                        size = Size(currentWidth, height),
                        cornerRadius = cornerRadius,
                    )

                    // 3. Specular liquid glass reflection stroke
                    val borderBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color(0x2200F2FE),
                            Color.Transparent,
                        ),
                        startY = top,
                        endY = top + height,
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = Offset(left + strokeWidthPx / 2f, top + strokeWidthPx / 2f),
                        size = Size(currentWidth - strokeWidthPx, height - strokeWidthPx),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = strokeWidthPx),
                    )
                }
            },
    )
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
