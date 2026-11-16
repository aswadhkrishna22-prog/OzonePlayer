package moe.rukamori.archivetune.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class OzoneColorScheme(
    val glassBackground: Color,
    val glassBorder: Color,
    val cardBackgroundOpaque: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val rippleColor: Color
)

fun lightOzoneColorScheme(
    colorScheme: ColorScheme
): OzoneColorScheme = OzoneColorScheme(
    glassBackground = Color.White.copy(alpha = 0.65f),
    glassBorder = colorScheme.primary.copy(alpha = 0.14f),
    cardBackgroundOpaque = colorScheme.surfaceContainerLow,
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurface.copy(alpha = 0.65f),
    rippleColor = colorScheme.onSurface.copy(alpha = 0.12f)
)

fun darkOzoneColorScheme(
    colorScheme: ColorScheme
): OzoneColorScheme = OzoneColorScheme(
    glassBackground = colorScheme.onSurface.copy(alpha = 0.08f),
    glassBorder = colorScheme.primary.copy(alpha = 0.10f),
    cardBackgroundOpaque = Color(0xFF1C1C1E),
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurface.copy(alpha = 0.65f),
    rippleColor = colorScheme.onSurface.copy(alpha = 0.16f)
)

val LocalOzoneColors = staticCompositionLocalOf<OzoneColorScheme> {
    error("No OzoneColorScheme provided. Make sure to wrap content in OzoneTheme.")
}

@Composable
fun OzoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val ozoneColors = remember(darkTheme, colorScheme) {
        if (darkTheme) {
            darkOzoneColorScheme(colorScheme)
        } else {
            lightOzoneColorScheme(colorScheme)
        }
    }

    CompositionLocalProvider(
        LocalOzoneColors provides ozoneColors,
        content = content
    )
}