@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.haptics.LocalOzoneHaptics
import com.kyant.backdrop.Backdrop
import io.github.fletchmckee.liquid.LiquidState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import moe.rukamori.archivetune.constants.NavigationBarCornerRadius
import moe.rukamori.archivetune.constants.NavigationBarHeight
import moe.rukamori.archivetune.constants.NavigationBarHideMinScale
import moe.rukamori.archivetune.constants.NavigationBarHideOffsetY
import moe.rukamori.archivetune.constants.NavigationBarInnerPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationBarOverflowPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationBarVisibilityDurationMs
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassStroke

internal object NavBarColors {
    @Composable
    fun container(pureBlack: Boolean) = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

    @Composable
    fun iconActive(pureBlack: Boolean) = if (pureBlack) Color.White else MaterialTheme.colorScheme.primary

    @Composable
    fun iconInactive(pureBlack: Boolean) = if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    capsuleShape: Shape = RoundedCornerShape(NavigationBarCornerRadius),
    hazeState: HazeState? = null,
    glassBackdrop: Backdrop? = null,
    navLiquidState: LiquidState? = null,
    blurNavBar: Boolean = true,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    showBorder: Boolean = true,
    blurEnabled: Boolean = true,
    visibilityFactor: Float = 1f,
    savedAlignment: String = "center",
    onAlignmentChange: (String) -> Unit = {},
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onMusicTogetherClick: (() -> Unit)? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    val hasOverflow = false

    val animatedVisibilityFactor by animateFloatAsState(
        targetValue = visibilityFactor.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = NavigationBarVisibilityDurationMs,
            easing = EaseOut,
        ),
        label = "FloatingToolbarVisibility",
    )

    val gestureState = rememberNavigationBarGestureState(
        items = items,
        isSelected = isSelected,
        onItemClick = onItemClick,
    )

    val selectedIndex = items.indexOfFirst { isSelected(it) }

    val haptics = LocalOzoneHaptics.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxSnapOffsetPx = with(density) { 44.dp.toPx() }

    val persistentOffsetAnimatable = remember { Animatable(0f) }
    var isBarDragMode by remember { mutableStateOf(false) }
    var barDragAccumulator by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(savedAlignment) {
        val targetPx = when (savedAlignment) {
            "left" -> -maxSnapOffsetPx
            "right" -> maxSnapOffsetPx
            else -> 0f
        }
        persistentOffsetAnimatable.animateTo(
            targetValue = targetPx,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        )
    }

    androidx.compose.runtime.LaunchedEffect(
        selectedIndex,
        gestureState.selectorState.rowWidthPx,
        gestureState.isDragging,
    ) {
        if (
            selectedIndex >= 0 &&
            !gestureState.isDragging &&
            gestureState.selectorState.tabWidthPx > 0f
        ) {
            gestureState.selectorState.syncToTab(selectedIndex)
        }
    }

    if (navLiquidState != null) {
        val liquidItems = items.map { screen ->
            LiquidNavigationItem(
                label = stringResource(screen.titleId),
                iconRes = screen.iconIdInactive,
                iconActiveRes = screen.iconIdActive,
            )
        }
        val baseScale = lerp(NavigationBarHideMinScale, 1.0f, animatedVisibilityFactor)

        LiquidNavigationBar(
            liquidState = navLiquidState,
            items = liquidItems,
            selectedIndex = selectedIndex.coerceAtLeast(0),
            onSelect = { index ->
                if (index in items.indices) {
                    val screen = items[index]
                    val wasSelected = isSelected(screen)
                    if (wasSelected && screen == Screens.Search && onSearchItemDoubleClick != null) {
                        onSearchItemDoubleClick()
                    } else {
                        onItemClick(screen, wasSelected)
                    }
                }
            },
            onScrubSelect = { index ->
                if (index in items.indices) {
                    onItemClick(items[index], false)
                }
            },
            isDark = pureBlack || isSystemInDarkTheme(),
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .graphicsLayer {
                val hideOffsetY = NavigationBarHideOffsetY.toPx() * (1f - animatedVisibilityFactor)
                translationY = hideOffsetY
                translationX = persistentOffsetAnimatable.value
                scaleX = baseScale
                scaleY = baseScale
                alpha = animatedVisibilityFactor
            },
            style = LiquidNavigationStyle.Tabs,
            applyWindowInsets = false,
        )
    } else {
        Box(
            modifier = modifier
                .graphicsLayer {
                    val hideOffsetY = NavigationBarHideOffsetY.toPx() * (1f - animatedVisibilityFactor)
                    translationY = hideOffsetY + gestureState.barTranslationYAnimatable.value
                    translationX = gestureState.barTranslationXAnimatable.value + persistentOffsetAnimatable.value

                    val baseScale = lerp(NavigationBarHideMinScale, 1.0f, animatedVisibilityFactor)
                    val dragScale = if (isBarDragMode) 1.03f else 1.0f
                    val scale = baseScale * gestureState.barScaleAnimatable.value * dragScale
                    scaleX = scale
                    scaleY = scale
                    alpha = animatedVisibilityFactor
                }
                .pointerInput(savedAlignment) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isBarDragMode = true
                            haptics.longPress()
                            barDragAccumulator = persistentOffsetAnimatable.value
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            barDragAccumulator += dragAmount.x
                            coroutineScope.launch {
                                persistentOffsetAnimatable.snapTo(
                                    barDragAccumulator.coerceIn(-maxSnapOffsetPx * 1.5f, maxSnapOffsetPx * 1.5f),
                                )
                            }
                        },
                        onDragEnd = {
                            isBarDragMode = false
                            val threshold = maxSnapOffsetPx * 0.35f
                            val newAlignment = when {
                                barDragAccumulator < -threshold -> "left"
                                barDragAccumulator > threshold -> "right"
                                else -> "center"
                            }
                            onAlignmentChange(newAlignment)
                            haptics.confirm()
                            val targetPx = when (newAlignment) {
                                "left" -> -maxSnapOffsetPx
                                "right" -> maxSnapOffsetPx
                                else -> 0f
                            }
                            coroutineScope.launch {
                                persistentOffsetAnimatable.animateTo(
                                    targetValue = targetPx,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                )
                            }
                        },
                        onDragCancel = {
                            isBarDragMode = false
                            val targetPx = when (savedAlignment) {
                                "left" -> -maxSnapOffsetPx
                                "right" -> maxSnapOffsetPx
                                else -> 0f
                            }
                            coroutineScope.launch {
                                persistentOffsetAnimatable.animateTo(
                                    targetValue = targetPx,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                )
                            }
                        },
                    )
                }
                .fillMaxWidth()
                .height(NavigationBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            // Layer 1: Glass background pill (Kyant Backdrop on API 31+, Haze fallback otherwise)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (glassBackdrop != null) {
                            Modifier.backdropLiquidGlass(
                                backdrop = glassBackdrop,
                                shape = capsuleShape,
                                isDark = pureBlack || isSystemInDarkTheme(),
                                interactive = false,
                            )
                        } else {
                            Modifier.clearLiquidGlassPill(
                                shape = capsuleShape,
                                hazeState = hazeState,
                                pureBlack = pureBlack,
                                showBorder = showBorder,
                                cornerRadiusDp = NavigationBarCornerRadius,
                            )
                        }
                    )
            )

            // Layer 2: Foreground content (icons, labels, drag selector) drawn on top of the glass
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NavigationBarInnerPaddingHorizontal)
                    .navigationTabDragGestures(gestureState),
                contentAlignment = Alignment.CenterStart,
            ) {
            NavigationDragSelectorOverlay(gestureState.selectorState)

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { screen ->
                    val selected = isSelected(screen)
                    NavigationTabItem(
                        screen = screen,
                        selected = selected,
                        pureBlack = pureBlack,
                        drawSelector = false,
                        onClick = {
                            gestureState.selectorState.onTabTap(screen, selected)
                        },
                        onDoubleClick = remember(screen, onSearchItemDoubleClick) {
                            if (screen == Screens.Search) onSearchItemDoubleClick else null
                        },
                    )
                }

                if (hasOverflow) {
                    Box(
                        modifier = Modifier
                            .padding(
                                start = NavigationBarOverflowPaddingHorizontal,
                                end = NavigationBarOverflowPaddingHorizontal,
                            )
                            .wrapContentSize(),
                    ) {
                        ToolbarOverflowMenu(
                            pureBlack = pureBlack,
                            onShuffleClick = onShuffleClick,
                            shuffleIconRes = shuffleIconRes,
                            shuffleContentDescription = shuffleContentDescription,
                            onMusicRecognitionClick = onMusicRecognitionClick,
                            musicRecognitionContentDescription = musicRecognitionContentDescription,
                            onMusicTogetherClick = onMusicTogetherClick,
                        )
                    }
                }
            }
        }
    }
}
}
