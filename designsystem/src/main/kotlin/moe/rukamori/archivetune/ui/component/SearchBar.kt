/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.TonalElevation
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import com.kyant.backdrop.Backdrop
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.designsystem.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassStroke
import kotlin.math.max

@OptIn(ExperimentalHazeApi::class)
@ExperimentalMaterial3Api
@Composable
fun TopSearch(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors =
        SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    windowInsets: WindowInsets = WindowInsets.systemBars,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    leftFocusRequester: FocusRequester? = null,
    hazeState: HazeState? = null,
    pureBlack: Boolean = false,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    showBorder: Boolean = true,
    glassBackdrop: Backdrop? = LocalGlassBackdrop.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = pureBlack || isSystemInDarkTheme()
    val animationProgress: Float by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = AnimationDurationMillis),
        label = "SearchBarAnimation",
    )

    val defaultInputFieldShape = SearchBarDefaults.inputFieldShape
    val defaultFullScreenShape = SearchBarDefaults.fullScreenShape
    val animatedShape by remember {
        derivedStateOf {
            when {
                shape == defaultInputFieldShape -> {
                    val animatedRadius = SearchBarCornerRadius * (1 - animationProgress)
                    RoundedCornerShape(CornerSize(animatedRadius))
                }

                animationProgress == 1f -> {
                    defaultFullScreenShape
                }

                else -> {
                    shape
                }
            }
        }
    }

    val topInset = windowInsets.asPaddingValues().calculateTopPadding()
    val startInset = windowInsets.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
    val endInset = windowInsets.asPaddingValues().calculateEndPadding(LocalLayoutDirection.current)

    val topPadding = SearchBarVerticalPadding + topInset
    val animatedSurfaceTopPadding = lerp(topPadding, 0.dp, animationProgress)
    val animatedInputFieldPadding by remember {
        derivedStateOf {
            PaddingValues(
                start = startInset * animationProgress,
                top = topPadding * animationProgress,
                end = endInset * animationProgress,
                bottom = SearchBarVerticalPadding * animationProgress,
            )
        }
    }

    val containerColor = if (pureBlack) Color.Black else colors.containerColor

    BoxWithConstraints(
        modifier = modifier.offset { IntOffset(x = 0, y = 0) },
        propagateMinConstraints = true,
    ) {
        val height: Dp
        val width: Dp
        val startPadding: Dp
        val endPadding: Dp
        with(LocalDensity.current) {
            val startWidth = constraints.maxWidth.toFloat()
            val startHeight =
                max(constraints.minHeight, InputFieldHeight.roundToPx())
                    .coerceAtMost(constraints.maxHeight)
                    .toFloat()
            val endWidth = constraints.maxWidth.toFloat()
            val endHeight = constraints.maxHeight.toFloat()

            height = lerp(startHeight, endHeight, animationProgress).toDp()
            width = lerp(startWidth, endWidth, animationProgress).toDp()
            startPadding =
                lerp(
                    (SearchBarHorizontalPadding + startInset).roundToPx().toFloat(),
                    0f,
                    animationProgress,
                ).toDp()
            endPadding =
                lerp(
                    (SearchBarHorizontalPadding + endInset).roundToPx().toFloat(),
                    0f,
                    animationProgress,
                ).toDp()
        }

        val borderFade = (1f - animationProgress).coerceIn(0f, 1f)

        val glassModifier = when {
            glassBackdrop != null && animationProgress < 0.5f -> {
                Modifier.backdropLiquidGlass(
                    backdrop = glassBackdrop,
                    shape = animatedShape,
                    isDark = isDark,
                    interactive = false,
                    lensDivisor = 8f,
                    lensMaxRadiusDivisor = 3f,
                    scrimAlpha = if (isDark) 0.18f else 0.10f,
                )
            }
            animationProgress >= 0.5f -> {
                val overlayBg = (if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
                    .copy(alpha = if (pureBlack) 1f else 0.96f)
                Modifier
                    .background(overlayBg, animatedShape)
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = HazeBlurStyle {
                                    blurRadius(24.dp)
                                    backgroundColor(Color.Transparent)
                                },
                                performanceMode = HazePerformanceMode.Fixed(SettingsDimensions.HazeQualityFraction),
                            )
                        } else Modifier
                    )
            }
            else -> {
                Modifier.liquidGlass(
                    shape = animatedShape,
                    hazeState = hazeState,
                    pureBlack = pureBlack,
                    preset = if (animationProgress > 0.5f) LiquidGlassPreset.Header else LiquidGlassPreset.Pill,
                    blurRadius = androidx.compose.ui.unit.lerp(8.dp, 22.dp, animationProgress),
                    tintAlpha = if (pureBlack) 0.50f else androidx.compose.ui.util.lerp(0.10f, 0.32f, animationProgress),
                    showBorder = showBorder && borderFade > 0f,
                    cornerRadiusDp = androidx.compose.ui.unit.lerp(SearchBarCornerRadius, 0.dp, animationProgress),
                )
            }
        }

        Surface(
            shape = animatedShape,
            color = Color.Transparent,
            contentColor = contentColorFor(containerColor),
            tonalElevation = 0.dp,
            modifier =
                Modifier
                    .padding(
                        top = animatedSurfaceTopPadding,
                        start = startPadding,
                        end = endPadding,
                    )
                    .size(width = width, height = height)
                    .then(glassModifier),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val inputRowModifier = if (animationProgress >= 0.5f) {
                    val inputCapsuleShape = RoundedCornerShape(28.dp)
                    val inputGlass = if (glassBackdrop != null) {
                        Modifier.backdropLiquidGlass(
                            backdrop = glassBackdrop,
                            shape = inputCapsuleShape,
                            isDark = isDark,
                            interactive = false,
                            lensDivisor = 8f,
                            lensMaxRadiusDivisor = 3f,
                            scrimAlpha = if (isDark) 0.18f else 0.10f,
                        )
                    } else {
                        Modifier.background(
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            inputCapsuleShape,
                        )
                    }
                    Modifier
                        .padding(horizontal = 16.dp)
                        .then(inputGlass)
                        .clip(inputCapsuleShape)
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(animatedInputFieldPadding)
                        .then(inputRowModifier),
                ) {
                    SearchBarInputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = onSearch,
                        active = active,
                        onActiveChange = onActiveChange,
                        enabled = enabled,
                        placeholder = placeholder,
                        leadingIcon = leadingIcon,
                        trailingIcon = trailingIcon,
                        colors =
                            TextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        interactionSource = interactionSource,
                        focusRequester = focusRequester,
                        leftFocusRequester = leftFocusRequester,
                    )
                }

                if (animationProgress > 0) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .imePadding()
                            .alpha(animationProgress),
                    ) {
                        Column {
                            content()
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = active) {
        onActiveChange(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarInputField(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    leftFocusRequester: FocusRequester? = null,
) {
    val coercedQuery = remember(query) { query.coerceTextRanges() }
    val focused = interactionSource.collectIsFocusedAsState().value
    val searchContentDescription = stringResource(R.string.content_desc_search)
    val suggestionsAvailableDescription = stringResource(R.string.content_desc_suggestions_available)
    val textColor =
        LocalTextStyle.current.color.takeOrElse {
            if (focused) colors.focusedTextColor else colors.unfocusedTextColor
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(InputFieldHeight),
    ) {
        if (leadingIcon != null) {
            Spacer(Modifier.width(SearchBarIconOffsetX))
            leadingIcon()
        }

        BasicTextField(
            value = coercedQuery,
            onValueChange = { onQueryChange(it.coerceTextRanges()) },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .then(
                        if (leftFocusRequester != null) {
                            Modifier.focusProperties {
                                left = leftFocusRequester
                            }
                        } else {
                            Modifier
                        },
                    ).pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (upEvent != null) {
                                onActiveChange(true)
                            }
                        }
                    }.semantics {
                        contentDescription = searchContentDescription
                        if (active) {
                            stateDescription = suggestionsAvailableDescription
                        }
                    }.onKeyEvent {
                        if (it.key == Key.Enter) {
                            onSearch(coercedQuery.text)
                            return@onKeyEvent true
                        }
                        false
                    },
            enabled = enabled,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = textColor)),
            cursorBrush = SolidColor(colors.cursorColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(coercedQuery.text) }),
            interactionSource = interactionSource,
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = coercedQuery.text,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = placeholder,
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = colors,
                    contentPadding = PaddingValues(),
                    container = {},
                )
            },
        )

        if (trailingIcon != null) {
            trailingIcon()
            Spacer(Modifier.width(SearchBarIconOffsetX))
        }
    }
}

private fun TextFieldValue.coerceTextRanges(): TextFieldValue {
    val textLength = text.length
    val coercedSelection = selection.coerceInText(textLength)
    val coercedComposition = composition?.coerceInText(textLength)
    return if (coercedSelection == selection && coercedComposition == composition) {
        this
    } else {
        copy(selection = coercedSelection, composition = coercedComposition)
    }
}

private fun TextRange.coerceInText(textLength: Int): TextRange =
    TextRange(
        start = minOf(start, end).coerceIn(0, textLength),
        end = maxOf(start, end).coerceIn(0, textLength),
    )

// Measurement specs
val InputFieldHeight = 56.dp
private val SearchBarCornerRadius: Dp = 28.dp
internal val SearchBarVerticalPadding: Dp = 8.dp
internal val SearchBarHorizontalPadding: Dp = 16.dp
val SearchBarIconOffsetX: Dp = 4.dp
private const val AnimationDurationMillis: Int = 300
