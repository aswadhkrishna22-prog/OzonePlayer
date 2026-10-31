/*
 * Ozone Player (2026) | Dual Play — Ear Selection Buttons
 * GNU General Public License v3.0
 *
 * Glass-style L / R / M circle buttons.
 * Pane A shows: R  M   (Song A default = Left ear; no L button)
 * Pane B shows: L  M   (Song B default = Right ear; no R button)
 *
 * Glass spec:
 *  Unselected : translucent white fill (0.14 alpha), 1dp gradient border
 *               (white 0.55 top-left → white 0.12 bottom-right), white letter
 *  Selected   : white fill (0.85 alpha), dark letter (black 0.9), white border
 *  Disabled   : whole button graphicsLayer alpha = 0.3, no click
 *  Press      : bounceClick spring scale 0.90, haptic click
 *
 * DO NOT add Modifier.blur — it blurs the button itself, not the background.
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.playback.dualplay.EarSelection
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily

// ── Sizes ─────────────────────────────────────────────────────────────────────

private val DefaultCircleSize = 30.dp
private val DefaultButtonGap  = 4.dp
private val DefaultLetterSize = 12.sp

// ── Colors ─────────────────────────────────────────────────────────────────────

private val UnselectedFill   = Color.White.copy(alpha = 0.14f)
private val SelectedFill     = Color.White.copy(alpha = 0.85f)
private val SelectedLetter   = Color.Black.copy(alpha = 0.9f)
private val UnselectedLetter = Color.White
private val SelectedBorder   = Color.White

private fun unselectedBorderBrush(size: Dp = DefaultCircleSize) = Brush.linearGradient(
    colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f)),
    start = Offset(0f, 0f),
    end   = Offset(size.value, size.value),
)

// ── Public composables ─────────────────────────────────────────────────────────

/**
 * Single-player toolbar ear buttons: L, R, M.
 * Shown at the top right of the song page next to 3-dot menu when Dual Play is ON
 * and before split view is active.
 */
@Composable
fun FullPlayerToolbarEarButtons(
    currentEar: EarSelection?,
    onEarSelected: (EarSelection) -> Unit,
    modifier: Modifier = Modifier,
    circleSize: Dp = DefaultCircleSize,
    gap: Dp = DefaultButtonGap,
    fontSize: TextUnit = DefaultLetterSize,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EarCircleButton(
                label = "L",
                selected = currentEar == EarSelection.L,
                onClick = { onEarSelected(EarSelection.L) },
                circleSize = circleSize,
                fontSize = fontSize,
            )
            EarCircleButton(
                label = "R",
                selected = currentEar == EarSelection.R,
                onClick = { onEarSelected(EarSelection.R) },
                circleSize = circleSize,
                fontSize = fontSize,
            )
            EarCircleButton(
                label = "M",
                selected = currentEar == EarSelection.M,
                onClick = { onEarSelected(EarSelection.M) },
                circleSize = circleSize,
                fontSize = fontSize,
            )
        }
    }
}

/**
 * Pane A ear buttons: only R and M are shown.
 * L is the "default" state (no button highlighted).
 *
 * @param currentEar    Current ear selection for Pane A.
 * @param disabledEar   If Pane B has an ear selected, that ear is disabled here (M is never disabled).
 * @param onEarSelected Called when the user taps R or M.
 */
@Composable
fun PaneAEarButtons(
    currentEar: EarSelection?,
    disabledEar: EarSelection? = null,
    onEarSelected: (EarSelection) -> Unit,
    modifier: Modifier = Modifier,
    circleSize: Dp = DefaultCircleSize,
    gap: Dp = DefaultButtonGap,
    fontSize: TextUnit = DefaultLetterSize,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // R button — selecting R means Song A goes to the right ear
            EarCircleButton(
                label     = "R",
                selected  = currentEar == EarSelection.R,
                disabled  = disabledEar == EarSelection.R,
                onClick   = { onEarSelected(EarSelection.R) },
                circleSize = circleSize,
                fontSize = fontSize,
            )
            // M button — M means Song A in both ears (Mono / Mid)
            EarCircleButton(
                label     = "M",
                selected  = currentEar == EarSelection.M,
                disabled  = false,
                onClick   = { onEarSelected(EarSelection.M) },
                circleSize = circleSize,
                fontSize = fontSize,
            )
        }
    }
}

/**
 * Pane B ear buttons: only L and M are shown.
 * R is the "default" state (no button highlighted).
 *
 * @param currentEar    Current ear selection for Pane B.
 * @param disabledEar   If Pane A has an ear selected, that ear is disabled here (M is never disabled).
 * @param onEarSelected Called when the user taps L or M.
 */
@Composable
fun PaneBEarButtons(
    currentEar: EarSelection?,
    disabledEar: EarSelection? = null,
    onEarSelected: (EarSelection) -> Unit,
    modifier: Modifier = Modifier,
    circleSize: Dp = DefaultCircleSize,
    gap: Dp = DefaultButtonGap,
    fontSize: TextUnit = DefaultLetterSize,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // L button — selecting L means Song B goes to the left ear
            EarCircleButton(
                label     = "L",
                selected  = currentEar == EarSelection.L,
                disabled  = disabledEar == EarSelection.L,
                onClick   = { onEarSelected(EarSelection.L) },
                circleSize = circleSize,
                fontSize = fontSize,
            )
            // M button — M means Song B in both ears
            EarCircleButton(
                label     = "M",
                selected  = currentEar == EarSelection.M,
                disabled  = false,
                onClick   = { onEarSelected(EarSelection.M) },
                circleSize = circleSize,
                fontSize = fontSize,
            )
        }
    }
}

// ── Private: single glass circle ──────────────────────────────────────────────

@Composable
private fun EarCircleButton(
    label:    String,
    selected: Boolean,
    onClick:  () -> Unit,
    disabled: Boolean = false,
    circleSize: Dp = DefaultCircleSize,
    fontSize: TextUnit = DefaultLetterSize,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalYumaHaptics.current
    val fontFamily = LocalArchiveTuneFontFamily.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue  = if (isPressed && !disabled) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label        = "EarButtonBounce",
    )

    // Visual circle styling
    val fill         = if (selected) SelectedFill else UnselectedFill
    val letterColor  = if (selected) SelectedLetter else UnselectedLetter
    val borderBrush  = if (selected) {
        Brush.linearGradient(listOf(SelectedBorder, SelectedBorder))
    } else {
        unselectedBorderBrush(circleSize)
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Box(
            modifier = modifier
                .size(circleSize)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    alpha  = if (disabled) 0.3f else 1f
                }
                .clip(CircleShape)
                .background(fill, CircleShape)
                .border(1.dp, borderBrush, CircleShape)
                .then(
                    if (!disabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication        = null,
                        ) {
                            haptics.click()
                            onClick()
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = label,
                color      = letterColor,
                fontSize   = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
            )
        }
    }
}
