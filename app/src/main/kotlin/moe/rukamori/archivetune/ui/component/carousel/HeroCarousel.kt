/*
 * Ozone Player (2026)
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.haptics.rememberOzoneHaptics
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun HeroCarousel(
    items: List<HeroItem>,
    rollCandidates: List<HeroItem>,
    rollState: HeroRollState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    isCarouselActive: Boolean,
    onTrackClick: (String) -> Unit,
    onTrackLongClick: (String) -> Unit,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier,
    scrollOffset: () -> Float = { 0f },
) {
    val displayItems = remember(items, rollCandidates.size) {
        buildList {
            addAll(items.map { HeroDisplayItem.Track(it) })
            if (rollCandidates.size >= HeroCarouselTokens.GachaMinPool) {
                add(HeroDisplayItem.RollCard)
            }
        }
    }

    if (displayItems.isEmpty()) return

    val isSingleItem = displayItems.size == 1
    val pagerState = rememberPagerState(
        initialPage = if (isSingleItem) 0 else (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % displayItems.size),
        pageCount = { if (isSingleItem) 1 else Int.MAX_VALUE },
    )

    val context = LocalContext.current
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val edgeBlur = !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val animationsDisabled = LocalAnimationsDisabled.current
    val haptics = rememberOzoneHaptics()

    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userSwipe by remember { mutableStateOf(false) }
    var isManualInteractionInProgress by remember { mutableStateOf(false) }
    var showDotsIndicator by remember { mutableStateOf(false) }
    var lastManualSwipeTimestamp by remember { mutableStateOf(0L) }
    var lastSettledPage by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userSwipe = true
            isManualInteractionInProgress = true
            showDotsIndicator = true
            lastManualSwipeTimestamp = System.currentTimeMillis()
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) {
                userSwipe = false
                if (isManualInteractionInProgress) {
                    if (pagerState.currentPage != lastSettledPage) {
                        haptics.segmentTick()
                        lastSettledPage = pagerState.currentPage
                    }
                    lastManualSwipeTimestamp = System.currentTimeMillis()
                    delay(1500.milliseconds)
                    showDotsIndicator = false
                    isManualInteractionInProgress = false
                }
            }
        }
    }

    val wipeCurveState = animateFloatAsState(
        targetValue = if (userSwipe && !animationsDisabled) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(260),
        label = "heroWipeCurve",
    )
    val wipeCurveProvider = remember(wipeCurveState) { { wipeCurveState.value } }

    val dotsAlpha by animateFloatAsState(
        targetValue = if (showDotsIndicator) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(if (showDotsIndicator) 150 else 300),
        label = "heroDotsIndicatorAlpha",
    )

    if (!isSingleItem && !animationsDisabled) {
        LaunchedEffect(pagerState, displayItems.size, isDragged, rollState.phase, isCarouselActive, lastManualSwipeTimestamp) {
            if (isDragged || !isCarouselActive) return@LaunchedEffect
            while (true) {
                val timeSinceManual = System.currentTimeMillis() - lastManualSwipeTimestamp
                if (timeSinceManual < 5000L) {
                    delay((5000L - timeSinceManual).milliseconds)
                }
                if (isDragged || !isCarouselActive) return@LaunchedEffect

                val currentItem = displayItems[pagerState.currentPage.mod(displayItems.size)]
                val onRoll = currentItem is HeroDisplayItem.RollCard
                if (onRoll && rollState.phase != HeroRollPhase.Idle) return@LaunchedEffect
                delay((if (onRoll) HeroCarouselTokens.HeroRollIdleAdvanceMillis else HeroCarouselTokens.HeroAdvanceMillis).milliseconds)

                if (isDragged || !isCarouselActive) return@LaunchedEffect
                if (System.currentTimeMillis() - lastManualSwipeTimestamp < 5000L) continue

                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(
                        HeroCarouselTokens.HeroSlideMillis,
                        easing = HeroCarouselTokens.HeroSlideEasing,
                    ),
                )
            }
        }

        LaunchedEffect(pagerState.currentPage, displayItems) {
            val nextItem = displayItems[(pagerState.currentPage + 1).mod(displayItems.size)]
            if (nextItem is HeroDisplayItem.Track) {
                nextItem.item.thumbnailUrl?.let { url ->
                    context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSingleItem && rollState.phase != HeroRollPhase.Rolling,
            key = { page ->
                val item = displayItems[page.mod(displayItems.size)]
                "${item.key}_$page"
            },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (val displayItem = displayItems[page.mod(displayItems.size)]) {
                is HeroDisplayItem.RollCard -> {
                    HeroRollCard(
                        state = rollState,
                        pageOffset = { pagerState.offsetOf(page) },
                        wipeCurve = wipeCurveProvider,
                        isResultPlaying = isPlaying && rollState.result != null && mediaMetadata?.id == rollState.result?.id,
                        edgeBlur = edgeBlur,
                        isCarouselActive = isCarouselActive,
                        animationsDisabled = animationsDisabled,
                        onRoll = onRoll,
                        onPlayResult = onTrackClick,
                        scrollOffset = scrollOffset,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is HeroDisplayItem.Track -> {
                    HeroMediaCard(
                        item = displayItem.item,
                        pageOffset = { pagerState.offsetOf(page) },
                        wipeCurve = wipeCurveProvider,
                        isThisPlaying = isPlaying && mediaMetadata?.id == displayItem.item.id,
                        edgeBlur = edgeBlur,
                        onPrimaryClick = { onTrackClick(displayItem.item.id) },
                        onLongClick = { onTrackLongClick(displayItem.item.id) },
                        scrollOffset = scrollOffset,
                        animationsDisabled = animationsDisabled,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Soft top scrim (black 40% fading to transparent over ~120dp)
        // so greeting, avatar, and status bar icons stay readable on bright artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        if (!isSingleItem && displayItems.isNotEmpty() && (dotsAlpha > 0f || (animationsDisabled && showDotsIndicator))) {
            val totalCount = displayItems.size
            val activeIndex = pagerState.currentPage.mod(totalCount)
            val maxVisibleDots = minOf(5, totalCount)
            val windowStart = (activeIndex - maxVisibleDots / 2).coerceIn(0, totalCount - maxVisibleDots)
            val windowIndices = windowStart until (windowStart + maxVisibleDots)

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 28.dp)
                    .graphicsLayer {
                        val scroll = scrollOffset()
                        alpha = dotsAlpha * (1f - (scroll / 200f)).coerceIn(0f, 1f)
                    },
            ) {
                windowIndices.forEach { dotIndex ->
                    val isSelected = dotIndex == activeIndex
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 6.dp,
                        animationSpec = if (animationsDisabled) snap() else tween(200),
                        label = "heroDotWidth_$dotIndex",
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                        animationSpec = if (animationsDisabled) snap() else tween(200),
                        label = "heroDotColor_$dotIndex",
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}



private fun PagerState.offsetOf(page: Int): Float =
    (page - currentPage) - currentPageOffsetFraction
