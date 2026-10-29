package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

@Stable
internal class NavigationTabSelectorState(
    internal val items: List<Screens>,
    private val isSelectedProvider: () -> (Screens) -> Boolean,
    private val onItemClickProvider: () -> (Screens, Boolean) -> Unit,
    private val hapticsProvider: () -> YumaHaptics,
    private val animationsDisabledProvider: () -> Boolean,
    private val coroutineScope: CoroutineScope,
) {
    var isDragging by mutableStateOf(false)
        internal set
    var candidateIndex by mutableIntStateOf(-1)
        internal set

    var rowWidthPx by mutableFloatStateOf(0f)
    var isInitialized by mutableStateOf(false)

    val selectorXAnimatable = Animatable(0f)
    val selectorWidthAnimatable = Animatable(0f)

    var startDragIndex by mutableIntStateOf(0)
        private set
    var accumulatedDragX by mutableFloatStateOf(0f)
        private set
    private var didTriggerThresholdHaptic by mutableStateOf(false)

    val tabWidthPx: Float
        get() = if (items.isNotEmpty()) rowWidthPx / items.size else 0f

    private fun <T> settleSpring(): AnimationSpec<T> =
        if (animationsDisabledProvider()) {
            snap()
        } else {
            spring(dampingRatio = 0.54f, stiffness = 420f)
        }

    fun syncToTab(index: Int, animate: Boolean = true) {
        if (isDragging || index !in items.indices || tabWidthPx <= 0f) return
        val targetCenter = (index + 0.5f) * tabWidthPx
        if (!isInitialized) {
            coroutineScope.launch {
                selectorXAnimatable.snapTo(targetCenter)
                selectorWidthAnimatable.snapTo(tabWidthPx)
                isInitialized = true
            }
        } else if (animate) {
            coroutineScope.launch {
                val distance = kotlin.math.abs(targetCenter - selectorXAnimatable.value)
                if (distance > 10f) {
                    launch {
                        val stretchWidth = tabWidthPx * 1.25f
                        selectorWidthAnimatable.animateTo(stretchWidth, spring(dampingRatio = 0.58f, stiffness = 550f))
                        selectorWidthAnimatable.animateTo(tabWidthPx, settleSpring())
                    }
                }
                selectorXAnimatable.animateTo(targetCenter, settleSpring())
                selectorWidthAnimatable.animateTo(tabWidthPx, settleSpring())
            }
        } else {
            coroutineScope.launch {
                selectorXAnimatable.snapTo(targetCenter)
                selectorWidthAnimatable.snapTo(tabWidthPx)
            }
        }
    }

    fun onTabTap(screen: Screens, isSelected: Boolean) {
        val index = items.indexOf(screen)
        if (index in items.indices && tabWidthPx > 0f) {
            val targetCenter = (index + 0.5f) * tabWidthPx
            val distance = kotlin.math.abs(targetCenter - selectorXAnimatable.value)

            coroutineScope.launch {
                if (distance > 10f) {
                    launch {
                        val stretchWidth = tabWidthPx * 1.28f
                        selectorWidthAnimatable.animateTo(stretchWidth, spring(dampingRatio = 0.55f, stiffness = 550f))
                        selectorWidthAnimatable.animateTo(tabWidthPx, settleSpring())
                    }
                }
                selectorXAnimatable.animateTo(targetCenter, settleSpring())
            }
        }
        onItemClickProvider()(screen, isSelected)
    }

    suspend fun onDragStart(offsetX: Float) {
        if (items.isEmpty() || tabWidthPx <= 0f) return
        hapticsProvider().gestureStart()
        isDragging = true
        didTriggerThresholdHaptic = false
        accumulatedDragX = 0f

        val currentSelected = items.indexOfFirst { isSelectedProvider()(it) }
        val touchIndex = (offsetX / tabWidthPx).toInt().coerceIn(0, items.size - 1)
        startDragIndex = if (currentSelected in items.indices) currentSelected else touchIndex
        candidateIndex = startDragIndex

        val center = (startDragIndex + 0.5f) * tabWidthPx
        selectorXAnimatable.snapTo(center)
        selectorWidthAnimatable.snapTo(tabWidthPx)
    }

    suspend fun onDrag(
        dragAmountX: Float,
        currentTouchX: Float = selectorXAnimatable.value + dragAmountX,
    ) {
        if (items.isEmpty() || tabWidthPx <= 0f) return
        accumulatedDragX += dragAmountX

        val startCenter = (startDragIndex + 0.5f) * tabWidthPx
        val directTouchIndex = (currentTouchX / tabWidthPx).toInt().coerceIn(0, items.size - 1)

        val targetIndex: Int
        val visualCenterX: Float
        val visualWidth: Float

        if (accumulatedDragX < 0f) {
            // Swiping LEFT -> Intended target is NEXT tab (startDragIndex + 1)
            val nextIndex = startDragIndex + 1
            if (nextIndex < items.size) {
                val nextCenter = (nextIndex + 0.5f) * tabWidthPx
                val progress = (-accumulatedDragX / tabWidthPx).coerceIn(0f, 1.25f)

                targetIndex = if (progress >= 0.28f) nextIndex else startDragIndex

                val interp = progress.coerceIn(0f, 1f)
                visualCenterX = startCenter + (nextCenter - startCenter) * interp

                // Liquid jelly stretch: stretches wider in transit between tabs
                val stretch = 4f * interp * (1f - interp)
                visualWidth = tabWidthPx * (1f + 0.32f * stretch + progress * 0.06f)
            } else {
                // At rightmost tab (Library): rubber band overscroll resistance
                targetIndex = startDragIndex
                val rubberBand = accumulatedDragX * 0.2f
                visualCenterX = startCenter + rubberBand
                visualWidth = tabWidthPx * (1f + kotlin.math.abs(rubberBand) / tabWidthPx * 0.45f)
            }
        } else {
            // Swiping / dragging RIGHT
            if (startDragIndex > 0) {
                // Swiping towards PREVIOUS tab (startDragIndex - 1)
                val prevIndex = startDragIndex - 1
                val prevCenter = (prevIndex + 0.5f) * tabWidthPx
                val progress = (accumulatedDragX / tabWidthPx).coerceIn(0f, 1.25f)

                targetIndex = if (progress >= 0.28f) prevIndex else startDragIndex

                val interp = progress.coerceIn(0f, 1f)
                visualCenterX = startCenter + (prevCenter - startCenter) * interp

                val stretch = 4f * interp * (1f - interp)
                visualWidth = tabWidthPx * (1f + 0.32f * stretch + progress * 0.06f)
            } else if (directTouchIndex > 0 || accumulatedDragX > 15f) {
                // Dragging from Home towards Search/Library
                val candidateTarget = if (directTouchIndex > 0) directTouchIndex else (startDragIndex + 1).coerceAtMost(items.size - 1)
                val targetCenter = (candidateTarget + 0.5f) * tabWidthPx
                val maxDist = kotlin.math.max(1f, targetCenter - startCenter)
                val progress = (accumulatedDragX / maxDist).coerceIn(0f, 1.25f)

                targetIndex = if (progress >= 0.28f) candidateTarget else startDragIndex

                val interp = progress.coerceIn(0f, 1f)
                visualCenterX = startCenter + (targetCenter - startCenter) * interp

                val stretch = 4f * interp * (1f - interp)
                visualWidth = tabWidthPx * (1f + 0.32f * stretch + progress * 0.06f)
            } else {
                // At leftmost tab (Home): rubber band overscroll resistance
                targetIndex = startDragIndex
                val rubberBand = accumulatedDragX * 0.2f
                visualCenterX = startCenter + rubberBand
                visualWidth = tabWidthPx * (1f + rubberBand / tabWidthPx * 0.45f)
            }
        }

        if (targetIndex != candidateIndex) {
            candidateIndex = targetIndex
            if (targetIndex != startDragIndex && !didTriggerThresholdHaptic) {
                hapticsProvider().segmentTick()
                didTriggerThresholdHaptic = true
            } else if (targetIndex == startDragIndex) {
                didTriggerThresholdHaptic = false
            }
        }

        selectorXAnimatable.snapTo(visualCenterX)
        selectorWidthAnimatable.snapTo(visualWidth)
    }

    fun onRelease(velocityX: Float = 0f) {
        val currentSelected = items.indexOfFirst { isSelectedProvider()(it) }.coerceAtLeast(0)
        val flingThreshold = 400f
        val distanceThreshold = tabWidthPx * 0.28f

        val resolvedIndex = when {
            // Flick left -> Next tab
            velocityX < -flingThreshold && startDragIndex < items.size - 1 -> {
                startDragIndex + 1
            }
            // Flick right -> Prev tab (or if from 0, Search)
            velocityX > flingThreshold -> {
                if (startDragIndex > 0) {
                    startDragIndex - 1
                } else if (items.size > 1) {
                    1
                } else {
                    0
                }
            }
            // Swiped left past distance threshold -> Next tab
            accumulatedDragX < -distanceThreshold && startDragIndex < items.size - 1 -> {
                startDragIndex + 1
            }
            // Swiped right past distance threshold
            accumulatedDragX > distanceThreshold -> {
                if (startDragIndex > 0) {
                    startDragIndex - 1
                } else if (candidateIndex > 0) {
                    candidateIndex
                } else {
                    startDragIndex
                }
            }
            candidateIndex in items.indices && candidateIndex != startDragIndex -> {
                candidateIndex
            }
            else -> {
                startDragIndex
            }
        }.coerceIn(0, items.size - 1)

        candidateIndex = resolvedIndex

        if (resolvedIndex in items.indices && resolvedIndex != currentSelected) {
            val candidate = items[resolvedIndex]
            onItemClickProvider()(candidate, false)
            hapticsProvider().click()
        }
    }

    suspend fun animateRelease() = coroutineScope {
        val targetIndex = if (candidateIndex in items.indices) {
            candidateIndex
        } else {
            items.indexOfFirst { isSelectedProvider()(it) }.coerceAtLeast(0)
        }
        val targetCenter = (targetIndex + 0.5f) * tabWidthPx

        val animX = launch {
            selectorXAnimatable.animateTo(
                targetValue = targetCenter,
                animationSpec = settleSpring(),
            )
        }
        val animWidth = launch {
            selectorWidthAnimatable.animateTo(
                targetValue = tabWidthPx,
                animationSpec = settleSpring(),
            )
        }

        animX.join()
        animWidth.join()
    }

    fun onCancel() {
        candidateIndex = -1
    }

    suspend fun animateCancel() = coroutineScope {
        val confirmedIndex = items.indexOfFirst { isSelectedProvider()(it) }.coerceAtLeast(0)
        val targetCenter = (confirmedIndex + 0.5f) * tabWidthPx

        val animX = launch {
            selectorXAnimatable.animateTo(
                targetValue = targetCenter,
                animationSpec = settleSpring(),
            )
        }
        val animWidth = launch {
            selectorWidthAnimatable.animateTo(
                targetValue = tabWidthPx,
                animationSpec = settleSpring(),
            )
        }

        animX.join()
        animWidth.join()
    }
}
