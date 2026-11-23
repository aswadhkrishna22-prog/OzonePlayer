/*
 * Ozone Player (2026)
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ozone signature icon style.
 *
 * Every settings entry gets a FIXED shape + FIXED two-tone gradient (not random per hash,
 * not tied to the wallpaper colours), so the app is recognisable at a glance.
 */
data class OzoneBadgeStyle(
    val top: Color,
    val bottom: Color,
    val shapeId: Int,
)

private fun hex(v: Long) = Color(0xFF000000 or v)

/** "Aurora" palette: cool atmosphere tones + a warm accent. Tweak freely. */
object OzonePalette {
    val Cyan = OzoneBadgeStyle(hex(0x5CE1E6), hex(0x1B8FB5), 0)
    val Azure = OzoneBadgeStyle(hex(0x6AA8FF), hex(0x3555E0), 1)
    val Violet = OzoneBadgeStyle(hex(0xB69CFF), hex(0x6B4BE0), 2)
    val Orchid = OzoneBadgeStyle(hex(0xF29BE8), hex(0xB93FB0), 3)
    val Coral = OzoneBadgeStyle(hex(0xFF9E8A), hex(0xE0455F), 4)
    val Amber = OzoneBadgeStyle(hex(0xFFD27A), hex(0xE88A1A), 5)
    val Mint = OzoneBadgeStyle(hex(0x8CF2C0), hex(0x1FA97A), 6)
    val Sky = OzoneBadgeStyle(hex(0x9BE7FF), hex(0x2C9BE0), 7)
    val Slate = OzoneBadgeStyle(hex(0xB8C4D9), hex(0x5B6B8C), 8)
}

/** Fixed mapping: settings item key -> style. Unknown keys fall back to a stable pick. */
fun ozoneBadgeStyleFor(key: String): OzoneBadgeStyle =
    when (key) {
        "account" -> OzonePalette.Azure
        "ai_integration" -> OzonePalette.Violet
        "appearance" -> OzonePalette.Orchid
        "lyrics" -> OzonePalette.Cyan
        "stats" -> OzonePalette.Amber
        "player" -> OzonePalette.Coral
        "content" -> OzonePalette.Sky
        "storage" -> OzonePalette.Slate
        "backup_restore" -> OzonePalette.Mint
        "internet" -> OzonePalette.Cyan
        "behavior" -> OzonePalette.Violet
        "default_links" -> OzonePalette.Azure
        "updates" -> OzonePalette.Mint
        "report_issue" -> OzonePalette.Coral
        "about" -> OzonePalette.Sky
        else -> {
            val all =
                listOf(
                    OzonePalette.Cyan, OzonePalette.Azure, OzonePalette.Violet,
                    OzonePalette.Orchid, OzonePalette.Coral, OzonePalette.Amber,
                    OzonePalette.Mint, OzonePalette.Sky,
                )
            all[kotlin.math.abs(key.hashCode()) % all.size]
        }
    }

@Composable
private fun badgeShape(id: Int): Shape =
    remember(id) {
        when (id % 7) {
            0 -> MaterialShapes.Cookie9Sided
            1 -> MaterialShapes.Clover4Leaf
            2 -> MaterialShapes.Cookie12Sided
            3 -> MaterialShapes.SoftBurst
            4 -> MaterialShapes.Flower
            5 -> MaterialShapes.Sunny
            else -> MaterialShapes.Puffy
        }
    }.toShape()

/**
 * Gradient badge with soft glow, glossy top highlight and hairline rim.
 * Icon is drawn in white for strong contrast on every theme.
 */
@Composable
fun OzoneIconBadge(
    painter: Painter,
    style: OzoneBadgeStyle,
    modifier: Modifier = Modifier,
    boxSize: Dp = 46.dp,
    iconSize: Dp = 24.dp,
    glow: Boolean = true,
) {
    val shape = badgeShape(style.shapeId)
    Box(
        modifier =
            modifier
                .size(boxSize)
                .then(
                    if (glow) {
                        Modifier.shadow(
                            elevation = 8.dp,
                            shape = shape,
                            ambientColor = style.bottom.copy(alpha = 0.55f),
                            spotColor = style.bottom.copy(alpha = 0.75f),
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(Brush.linearGradient(listOf(style.top, style.bottom)))
                // glossy highlight in the upper-left
                .drawBehind {
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                                center = Offset(size.width * 0.28f, size.height * 0.2f),
                                radius = size.minDimension * 0.7f,
                            ),
                        radius = size.minDimension * 0.7f,
                        center = Offset(size.width * 0.28f, size.height * 0.2f),
                    )
                }
                .border(
                    width = 0.8.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f)),
                        ),
                    shape = shape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}
