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
                    val radius = height / 2f
                    val cornerRadius = CornerRadius(radius, radius)

                    // 1. Soft drop shadow under the selected lens
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.16f),
                        topLeft = Offset(left, top + 1.5.dp.toPx()),
                        size = Size(currentWidth, height),
                        cornerRadius = cornerRadius,
                    )

                    // 2. Translucent glass fill, brighter than the bar (LastWave lens formula)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.08f),
                            ),
                            startY = top,
                            endY = top + height,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(currentWidth, height),
                        cornerRadius = cornerRadius,
                    )

                    // 3. Subtle convex top sheen
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent,
                            ),
                            startY = top,
                            endY = top + (height * 0.55f),
                        ),
                        topLeft = Offset(left, top),
                        size = Size(currentWidth, height),
                        cornerRadius = cornerRadius,
                    )

                    // 4. Specular lens rim highlight
                    val halfStroke = strokeWidthPx / 2f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.10f),
                                Color.White.copy(alpha = 0.25f),
                            ),
                            start = Offset(left, top),
                            end = Offset(left + currentWidth, top + height),
                        ),
                        topLeft = Offset(left + halfStroke, top + halfStroke),
                        size = Size(currentWidth - strokeWidthPx, height - strokeWidthPx),
                        cornerRadius = CornerRadius(
                            (radius - halfStroke).coerceAtLeast(0f),
                            (radius - halfStroke).coerceAtLeast(0f),
                        ),
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
