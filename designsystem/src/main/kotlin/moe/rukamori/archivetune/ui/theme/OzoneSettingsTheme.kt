/*
 * Ozone Player (2026)
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import moe.rukamori.archivetune.ui.settings.OzoneUi

/** Material3 colour scheme where every "blue/periwinkle" slot is Vivid Acid Lime. */
val OzoneAcidLimeDarkScheme =
    darkColorScheme(
        primary = Color(0xFFB6FF00),
        onPrimary = Color(0xFF050505),
        primaryContainer = Color(0xFF1B2905),
        onPrimaryContainer = Color(0xFFB6FF00),
        secondary = Color(0xFFB6FF00),
        onSecondary = Color(0xFF050505),
        secondaryContainer = Color(0xFF1B2905),
        onSecondaryContainer = Color(0xFFB6FF00),
        tertiary = Color(0xFFD2FF5C),
        onTertiary = Color(0xFF050505),
        tertiaryContainer = Color(0xFF1B2905),
        onTertiaryContainer = Color(0xFFD2FF5C),
        background = Color(0xFF060606),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF111214),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF17191D),
        onSurfaceVariant = Color(0xFFA6ABB4),
        surfaceTint = Color(0xFFB6FF00),
        surfaceContainerLowest = Color(0xFF060606),
        surfaceContainerLow = Color(0xFF111214),
        surfaceContainer = Color(0xFF14161A),
        surfaceContainerHigh = Color(0xFF17191D),
        surfaceContainerHighest = Color(0xFF1F2228),
        outline = Color(0xFF282B32),
        outlineVariant = Color(0xFF1E2024),
        error = Color(0xFFFF4D5A),
        onError = Color(0xFF050505),
        scrim = Color(0xFF000000),
    )

/**
 * Wrap any settings-area screen (and the dialogs / bottom sheets it opens) in the Ozone look:
 * lime colour scheme, pure obsidian canvas and a very subtle top-right lime glow.
 */
@Composable
fun OzoneSettingsTheme(
    withBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = OzoneAcidLimeDarkScheme,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        OzoneTheme(darkTheme = true) {
            CompositionLocalProvider(LocalContentColor provides OzoneUi.TextPrimary) {
                if (withBackground) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(OzoneUi.Bg)
                                .drawBehind {
                                    drawRect(
                                        brush =
                                            Brush.radialGradient(
                                                colors = listOf(OzoneUi.Lime.copy(alpha = 0.07f), Color.Transparent),
                                                center = Offset(size.width, 0f),
                                                radius = size.width * 0.9f,
                                            ),
                                    )
                                },
                    ) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}
