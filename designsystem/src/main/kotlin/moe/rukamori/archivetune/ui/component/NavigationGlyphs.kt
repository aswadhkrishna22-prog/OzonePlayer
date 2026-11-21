/*
 * Ozone Player (2026) | Real Liquid Glass Engine
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.ui.theme.LocalDisableAnimations

@Composable
internal fun LiquidNavigationItem.DrawIcon(isSelected: Boolean, tint: Color, size: Dp) {
    when {
        iconRes != 0 -> {
            val resId = if (isSelected && iconActiveRes != 0) iconActiveRes else iconRes
            Icon(
                painter = painterResource(resId),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size),
            )
        }
        icon != null -> {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * Staggered letter-by-letter reveal animation for the active tab label.
 * Each character fades in and slides up 6dp with a 25ms stagger.
 */
@Composable
internal fun StaggeredLetterReveal(
    text: String,
    tint: Color,
    captured: Boolean,
    modifier: Modifier = Modifier,
    disableAnimations: Boolean = LocalDisableAnimations.current,
) {
    if (disableAnimations || captured) {
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = modifier,
        )
        return
    }

    val density = LocalDensity.current
    val slidePx = with(density) { 6.dp.toPx() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        text.forEachIndexed { index, char ->
            val charAnim = remember(text, index) { Animatable(0f) }
            LaunchedEffect(charAnim) {
                delay(index * 25L)
                charAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing),
                )
            }
            Text(
                text = char.toString(),
                color = tint,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer {
                    val p = charAnim.value.coerceIn(0f, 1f)
                    alpha = p
                    translationY = (1f - p) * slidePx
                },
            )
        }
    }
}

/** Shared geometry for the visible rail and the lens's captured glyph plane. */
@Composable
internal fun NavigationGlyphs(
    items: List<LiquidNavigationItem>,
    selectedIndex: Int,
    captionIndex: Int,
    weights: List<Float>,
    neutral: Color,
    active: Color,
    captured: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeIndex = captionIndex.coerceIn(items.indices)
    Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            val isSelected = activeIndex == index
            val tint = if (isSelected) active else neutral
            val weight = weights.getOrElse(index) { 1f }
            val inputModifier = if (captured) {
                // The optical copy must neither receive touches nor duplicate tab semantics.
                Modifier.clearAndSetSemantics { }
            } else {
                Modifier
                    .testTag("liquid_navigation_tab_$index")
                    .semantics { contentDescription = item.label }
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        role = Role.Tab,
                        interactionSource = remember(index) { MutableInteractionSource() },
                        indication = null,
                    )
            }
            val textSemantics = if (captured) Modifier.clearAndSetSemantics { } else Modifier

            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .then(inputModifier),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.wrapContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    item.DrawIcon(isSelected = isSelected, tint = tint, size = 24.dp)
                    if (item.label.isNotBlank()) {
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                expandFrom = Alignment.Start,
                            ),
                            exit = fadeOut(animationSpec = tween(100)) + shrinkHorizontally(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                shrinkTowards = Alignment.Start,
                            ),
                        ) {
                            Row(
                                modifier = textSemantics,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(Modifier.width(6.dp))
                                StaggeredLetterReveal(
                                    text = item.label,
                                    tint = tint,
                                    captured = captured,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
