package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

internal const val NavHighlightSpringDamping = 0.55f
internal const val NavHighlightSpringStiffness = 350f
internal const val NavHighlightStretchFactor = 0.5f
internal const val NavHighlightVelocityDivisor = 1400f
internal const val NavHighlightMaxStretch = 0.6f
internal const val NavHighlightFlingProjectionSeconds = 0.18f

@Stable
internal class NavigationTabSelectorState(
    internal val items: List<Screens>,
    private val isSelectedProvider: () -> (Screens) -> Boolean,
    private val onItemClickProvider: () -> (Screens, Boolean) -> Unit,
    private val hapticsProvider: () -> YumaHaptics,
    private val isRtlProvider: () -> Boolean,
    internal val animationsDisabledProvider: () -> Boolean,
    private val coroutineScope: CoroutineScope,
) {
    var isDragging by mutableStateOf(false)
        internal set
    var candidateIndex by mutableIntStateOf(-1)
        internal set

    var rowWidthPx by mutableFloatStateOf(0f)
    var isInitialized by mutableStateOf(false)

    val selectorXAnimatable: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val selectorWidthAnimatable: Animatable<Float, AnimationVector1D> = Animatable(0f)

    private var dragStartHighlightX = 0f
    private var dragStartTouchX = 0f
    private var lastHapticTabIndex = -1

    val tabWidthPx: Float
        get() = if (items.isNotEmpty()) rowWidthPx / items.size else 0f

    val minHighlightX: Float
        get() = tabWidthPx / 2f

    val maxHighlightX: Float
        get() = (rowWidthPx - (tabWidthPx / 2f)).coerceAtLeast(minHighlightX)

    private fun highlightSpring(): SpringSpec<Float> = spring(
        dampingRatio = NavHighlightSpringDamping,
        stiffness = NavHighlightSpringStiffness,
    )

    fun getTabCenter(index: Int): Float {
        if (items.isEmpty() || tabWidthPx <= 0f) return 0f
        val isRtl = isRtlProvider()
        val visualIndex = if (isRtl) (items.size - 1 - index) else index
        return (visualIndex + 0.5f) * tabWidthPx
    }

    fun getNearestTabIndex(x: Float): Int {
        if (items.isEmpty() || tabWidthPx <= 0f) return 0
        val visualIndex = (x / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        val logicalIndex = if (isRtlProvider()) (items.size - 1 - visualIndex) else visualIndex
        return logicalIndex.coerceIn(0, items.size - 1)
    }

    fun getTabProximity(screen: Screens): Float {
        val index = items.indexOf(screen)
        if (index !in items.indices || tabWidthPx <= 0f) {
            return if (isSelectedProvider()(screen)) 1f else 0f
        }
        val center = getTabCenter(index)
        val distance = kotlin.math.abs(selectorXAnimatable.value - center)
        return (1f - (distance / tabWidthPx)).coerceIn(0f, 1f)
    }

    fun snapToTab(index: Int) {
        if (index !in items.indices || tabWidthPx <= 0f) return
        val targetCenter = getTabCenter(index)
        coroutineScope.launch {
            selectorXAnimatable.snapTo(targetCenter)
            selectorWidthAnimatable.snapTo(tabWidthPx)
            isInitialized = true
        }
    }

    fun animateToTab(index: Int) {
        if (index !in items.indices || tabWidthPx <= 0f) return
        val targetCenter = getTabCenter(index)
        if (kotlin.math.abs(selectorXAnimatable.targetValue - targetCenter) < 1f && selectorXAnimatable.isRunning) {
            return
        }
        coroutineScope.launch {
            if (animationsDisabledProvider()) {
                selectorXAnimatable.snapTo(targetCenter)
            } else {
                selectorXAnimatable.animateTo(
                    targetValue = targetCenter,
                    animationSpec = highlightSpring(),
                )
            }
            selectorWidthAnimatable.snapTo(tabWidthPx)
        }
    }

    fun syncToTab(index: Int, animate: Boolean = true) {
        if (isDragging || index !in items.indices || tabWidthPx <= 0f) return
        if (!isInitialized || !animate) {
            snapToTab(index)
        } else {
            animateToTab(index)
        }
    }

    fun onTabTap(screen: Screens, isSelected: Boolean) {
        val index = items.indexOf(screen)
        if (index in items.indices && tabWidthPx > 0f) {
            val targetCenter = getTabCenter(index)
            coroutineScope.launch {
                if (animationsDisabledProvider()) {
                    selectorXAnimatable.snapTo(targetCenter)
                } else {
                    selectorXAnimatable.animateTo(
                        targetValue = targetCenter,
                        animationSpec = highlightSpring(),
                    )
                }
                selectorWidthAnimatable.snapTo(tabWidthPx)
            }
        }
        onItemClickProvider()(screen, isSelected)
    }

    fun onDragStart(touchX: Float) {
        if (items.isEmpty() || tabWidthPx <= 0f) return
        isDragging = true
        dragStartHighlightX = selectorXAnimatable.value
        dragStartTouchX = touchX
        lastHapticTabIndex = getNearestTabIndex(dragStartHighlightX)
        candidateIndex = lastHapticTabIndex
        hapticsProvider().gestureStart()
    }

    fun onDrag(touchX: Float) {
        if (items.isEmpty() || tabWidthPx <= 0f) return
        val newX = (dragStartHighlightX + (touchX - dragStartTouchX))
            .coerceIn(minHighlightX, maxHighlightX)

        coroutineScope.launch {
            selectorXAnimatable.snapTo(newX)
        }

        val currentSlot = getNearestTabIndex(newX)
        candidateIndex = currentSlot
        if (currentSlot != lastHapticTabIndex) {
            hapticsProvider().segmentTick()
            lastHapticTabIndex = currentSlot
        }
    }

    fun onRelease(velocityX: Float = 0f) {
        if (items.isEmpty() || tabWidthPx <= 0f) {
            isDragging = false
            candidateIndex = -1
            return
        }

        val currentX = selectorXAnimatable.value
        val projectedX = (currentX + velocityX * NavHighlightFlingProjectionSeconds)
            .coerceIn(minHighlightX, maxHighlightX)
        val targetIndex = getNearestTabIndex(projectedX)
        val targetCenter = getTabCenter(targetIndex)

        val currentSelectedIndex = items.indexOfFirst { isSelectedProvider()(it) }
        val hasChanged = targetIndex in items.indices && targetIndex != currentSelectedIndex

        isDragging = false
        candidateIndex = targetIndex

        coroutineScope.launch {
            if (animationsDisabledProvider()) {
                selectorXAnimatable.snapTo(targetCenter)
            } else {
                selectorXAnimatable.animateTo(
                    targetValue = targetCenter,
                    initialVelocity = velocityX,
                    animationSpec = highlightSpring(),
                )
            }
            selectorWidthAnimatable.snapTo(tabWidthPx)
        }

        if (hasChanged) {
            val candidate = items[targetIndex]
            onItemClickProvider()(candidate, false)
        }
    }

    fun onCancel() {
        isDragging = false
        candidateIndex = -1
        val currentSelectedIndex = items.indexOfFirst { isSelectedProvider()(it) }.coerceAtLeast(0)
        val targetCenter = getTabCenter(currentSelectedIndex)
        coroutineScope.launch {
            if (animationsDisabledProvider()) {
                selectorXAnimatable.snapTo(targetCenter)
            } else {
                selectorXAnimatable.animateTo(
                    targetValue = targetCenter,
                    animationSpec = highlightSpring(),
                )
            }
            selectorWidthAnimatable.snapTo(tabWidthPx)
        }
    }
}
