package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.screens.Screens

@Stable
internal class NavigationBarGestureState(
    val selectorState: NavigationTabSelectorState,
    val repulsionState: NavigationBarRepulsionState,
    private val coroutineScope: CoroutineScope,
) {
    var isDragging by mutableStateOf(false)
        internal set

    private var settlingJob: Job? = null
    val barScaleAnimatable get() = repulsionState.barScaleAnimatable
    val barTranslationXAnimatable get() = repulsionState.barTranslationXAnimatable
    val barTranslationYAnimatable get() = repulsionState.barTranslationYAnimatable

    private var dragJob: Job? = null

    fun onDragStart(offsetX: Float, offsetY: Float, maxRepulsionXPx: Float, maxRepulsionYPx: Float) {
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) return
        settlingJob?.cancel()
        settlingJob = null
        dragJob?.cancel()
        dragJob = null

        isDragging = true
        selectorState.isDragging = true

        coroutineScope.launch {
            selectorState.onDragStart(offsetX)
            repulsionState.onDragStart(
                offsetX = offsetX,
                offsetY = offsetY,
                maxRepulsionXPx = maxRepulsionXPx,
                maxRepulsionYPx = maxRepulsionYPx,
            )
        }
    }

    fun onDrag(
        dragAmountX: Float,
        currentTouchX: Float,
        currentTouchY: Float,
        maxRepulsionXPx: Float,
        maxRepulsionYPx: Float,
    ) {
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) return

        dragJob?.cancel()
        dragJob = coroutineScope.launch {
            selectorState.onDrag(dragAmountX, currentTouchX)
            repulsionState.onDrag(
                currentTouchX = currentTouchX,
                currentTouchY = currentTouchY,
                maxRepulsionXPx = maxRepulsionXPx,
                maxRepulsionYPx = maxRepulsionYPx,
            )
        }
    }

    fun onDragEnd(velocityX: Float = 0f) {
        dragJob?.cancel()
        dragJob = null
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) {
            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
            return
        }

        selectorState.onRelease(velocityX)

        settlingJob?.cancel()
        settlingJob = coroutineScope.launch {
            val animSelector = launch { selectorState.animateRelease() }
            val animRepulsion = launch { repulsionState.animateRelease() }

            animSelector.join()
            animRepulsion.join()

            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
        }
    }

    fun onDragCancel() {
        dragJob?.cancel()
        dragJob = null
        if (selectorState.items.isEmpty() || selectorState.tabWidthPx <= 0f) {
            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
            return
        }

        selectorState.onCancel()

        settlingJob?.cancel()
        settlingJob = coroutineScope.launch {
            val animSelector = launch { selectorState.animateCancel() }
            val animRepulsion = launch { repulsionState.animateRelease() }

            animSelector.join()
            animRepulsion.join()

            isDragging = false
            selectorState.isDragging = false
            selectorState.candidateIndex = -1
        }
    }
}

@Composable
internal fun rememberNavigationBarGestureState(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    haptics: YumaHaptics = LocalYumaHaptics.current,
): NavigationBarGestureState {
    val currentIsSelected by androidx.compose.runtime.rememberUpdatedState(isSelected)
    val currentOnItemClick by androidx.compose.runtime.rememberUpdatedState(onItemClick)
    val currentHaptics by androidx.compose.runtime.rememberUpdatedState(haptics)
    val animationsDisabled = moe.rukamori.archivetune.LocalAnimationsDisabled.current
    val currentAnimationsDisabled by androidx.compose.runtime.rememberUpdatedState(animationsDisabled)

    val selectorState =
        remember(items) {
            NavigationTabSelectorState(
                items = items,
                isSelectedProvider = { currentIsSelected },
                onItemClickProvider = { currentOnItemClick },
                hapticsProvider = { currentHaptics },
                animationsDisabledProvider = { currentAnimationsDisabled },
                coroutineScope = coroutineScope,
            )
        }
    val repulsionState =
        remember {
            NavigationBarRepulsionState()
        }
    return remember(selectorState, repulsionState) {
        NavigationBarGestureState(
            selectorState = selectorState,
            repulsionState = repulsionState,
            coroutineScope = coroutineScope,
        )
    }
}

internal fun Modifier.navigationTabDragGestures(
    gestureState: NavigationBarGestureState,
): Modifier = this
    .onSizeChanged { size ->
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        gestureState.selectorState.rowWidthPx = width
        gestureState.repulsionState.rowWidthPx = width
        gestureState.repulsionState.rowHeightPx = height
    }
    .pointerInput(gestureState) {
        val maxRepulsionXPx = 6.dp.toPx()
        val maxRepulsionYPx = 3.dp.toPx()
        val touchSlop = viewConfiguration.touchSlop
        val velocityTracker = VelocityTracker()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            velocityTracker.resetTracking()
            velocityTracker.addPointerInputChange(down)

            var isDragActive = false
            val startX = down.position.x
            val startY = down.position.y
            var lastX = down.position.x
            var lastY = down.position.y

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!change.pressed) {
                    if (isDragActive) {
                        change.consume()
                        val velocity = velocityTracker.calculateVelocity().x
                        gestureState.onDragEnd(velocity)
                    }
                    break
                }

                velocityTracker.addPointerInputChange(change)
                val currentX = change.position.x
                val currentY = change.position.y
                val totalDeltaX = currentX - startX
                val totalDeltaY = currentY - startY
                val stepDeltaX = currentX - lastX
                val stepDeltaY = currentY - lastY
                lastX = currentX
                lastY = currentY

                if (!isDragActive) {
                    if (kotlin.math.abs(totalDeltaX) > touchSlop && kotlin.math.abs(totalDeltaX) > kotlin.math.abs(totalDeltaY) * 1.1f) {
                        isDragActive = true
                        change.consume()
                        gestureState.onDragStart(
                            offsetX = currentX,
                            offsetY = currentY,
                            maxRepulsionXPx = maxRepulsionXPx,
                            maxRepulsionYPx = maxRepulsionYPx,
                        )
                    }
                } else {
                    change.consume()
                    gestureState.onDrag(
                        dragAmountX = stepDeltaX,
                        currentTouchX = currentX,
                        currentTouchY = currentY,
                        maxRepulsionXPx = maxRepulsionXPx,
                        maxRepulsionYPx = maxRepulsionYPx,
                    )
                }
            }
        }
    }

@Deprecated(
    message = "Use rememberNavigationBarGestureState instead",
    replaceWith = ReplaceWith("rememberNavigationBarGestureState(items, isSelected, onItemClick, coroutineScope, haptics)"),
)
@Composable
internal fun rememberNavigationTabDragState(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    haptics: YumaHaptics = LocalYumaHaptics.current,
): NavigationBarGestureState = rememberNavigationBarGestureState(items, isSelected, onItemClick, coroutineScope, haptics)

internal typealias NavigationTabDragState = NavigationBarGestureState
