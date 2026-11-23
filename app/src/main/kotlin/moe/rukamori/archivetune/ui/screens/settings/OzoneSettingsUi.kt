/*
 * Ozone Player (2026)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R

/** Ozone brand tokens used by the settings screen. */
object OzoneUi {
    val Lime = Color(0xFFA3F52B)
    val LimeDark = Color(0xFF0D1805)
    val Bg = Color(0xFF0A0B10)
    val CardTop = Color(0xFF1B1C23)
    val CardBottom = Color(0xFF14151B)
    val Hairline = Color(0x16FFFFFF)
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFFA3A9B8)
    val SectionLabel = Color(0xFFB4B8C5)
    val Chevron = Color(0xFF757B8C)
}

/** Geometric typeface matching the reference screenshots. */
val OzoneFont = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_bold, FontWeight.Bold),
    Font(R.font.google_sans_bold, FontWeight.SemiBold),
    Font(R.font.google_sans_regular, FontWeight.Medium),
)

// ---------------------------------------------------------------------------------------------
// Header: OZONE / Settings / tagline / vinyl arc / moon button / search bar
// ---------------------------------------------------------------------------------------------

@Composable
fun OzoneSettingsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp),
    ) {
        // Vinyl record arc behind search bar on the right
        OzoneVinylArc(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 55.dp, y = 10.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ozone_brand_name),
                        color = OzoneUi.Lime,
                        fontFamily = OzoneFont,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings),
                        color = Color.White,
                        fontFamily = OzoneFont,
                        fontSize = 46.sp,
                        lineHeight = 50.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.ozone_settings_tagline),
                            color = OzoneUi.TextSecondary,
                            fontFamily = OzoneFont,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ozn_music_note),
                            contentDescription = null,
                            tint = OzoneUi.Lime,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x1FFFFFFF), CircleShape)
                        .clickable(onClick = onThemeClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ozn_moon),
                        contentDescription = stringResource(R.string.appearance),
                        tint = OzoneUi.Lime,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            OzoneSearchBar(query = query, onQueryChange = onQueryChange)
        }
    }
}

/** Half vinyl record peeking out from behind the search bar. */
@Composable
private fun OzoneVinylArc(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = 280.dp, height = 180.dp)
            .clipToBounds()
    ) {
        val outerRadius = 140.dp.toPx()
        val innerRadius = 48.dp.toPx()
        val center = Offset(size.width * 0.72f, size.height + 15.dp.toPx())

        // Outer vinyl disc with sweep gradient
        drawCircle(
            brush = Brush.sweepGradient(
                0.00f to Color(0xFF0D2506),
                0.40f to Color(0xFF19420A),
                0.60f to Color(0xFF3B8310),
                0.72f to Color(0xFFA3F52B),
                0.82f to Color(0xFF459413),
                0.92f to Color(0xFF163C08),
                1.00f to Color(0xFF0D2506),
                center = center,
            ),
            radius = outerRadius,
            center = center,
        )

        // Concentric vinyl grooves
        var r = innerRadius + 4.dp.toPx()
        while (r < outerRadius - 2.dp.toPx()) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.32f),
                radius = r,
                center = center,
                style = Stroke(width = 0.7.dp.toPx()),
            )
            r += 4.5.dp.toPx()
        }

        // Inner centre hole
        drawCircle(
            color = OzoneUi.Bg,
            radius = innerRadius,
            center = center,
        )
        // Center rim highlight
        drawCircle(
            color = OzoneUi.Lime.copy(alpha = 0.45f),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun OzoneSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color(0xF0181A22))
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ozn_search),
            contentDescription = null,
            tint = Color(0xFF757B8C),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.ozone_search_settings),
                    color = Color(0xFF7A8090),
                    fontFamily = OzoneFont,
                    fontSize = 17.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = OzoneUi.TextPrimary,
                    fontFamily = OzoneFont,
                    fontSize = 17.sp,
                ),
                cursorBrush = SolidColor(OzoneUi.Lime),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Section label with trailing hairline
// ---------------------------------------------------------------------------------------------

@Composable
fun OzoneSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = Color(0xFF8D93A3),
            fontFamily = OzoneFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0x18FFFFFF)),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Card + rows
// ---------------------------------------------------------------------------------------------

@Composable
fun OzoneSettingsCard(
    items: List<SettingsItem>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(OzoneUi.CardTop, OzoneUi.CardBottom)))
            .border(1.dp, Color(0x18FFFFFF), shape),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(thickness = 1.dp, color = Color(0x10FFFFFF))
            }
            OzoneSettingsRow(item)
        }
    }
}

@Composable
fun OzoneSettingsRow(
    item: SettingsItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .heightIn(min = 74.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Outline lime icon with soft radial glow
        Box(
            modifier = Modifier
                .size(44.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(OzoneUi.Lime.copy(alpha = 0.16f), Color.Transparent),
                        ),
                        radius = size.minDimension / 1.4f,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = item.icon,
                contentDescription = null,
                tint = OzoneUi.Lime,
                modifier = Modifier.size(28.dp),
            )
            if (item.showUpdateIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = OzoneUi.TextPrimary,
                fontFamily = OzoneFont,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = OzoneUi.TextSecondary,
                    fontFamily = OzoneFont,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.trailing != null) {
            Spacer(Modifier.width(10.dp))
            item.trailing.invoke()
        } else if (item.badge != null) {
            Spacer(Modifier.width(10.dp))
            OzonePill(item.badge)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ozn_chevron_right),
            contentDescription = null,
            tint = Color(0xFF757B8C),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun OzonePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = OzoneUi.LimeDark,
        fontFamily = OzoneFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(OzoneUi.Lime)
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Trailing widgets
// ---------------------------------------------------------------------------------------------

/** Used / total device storage with a lime progress bar. */
@Composable
fun OzoneStorageMeter(modifier: Modifier = Modifier) {
    val (used, total) = remember {
        runCatching {
            val st = StatFs(Environment.getDataDirectory().path)
            val t = st.totalBytes
            (t - st.availableBytes) to t
        }.getOrDefault(60_200_000_000L to 105_000_000_000L)
    }
    val fraction = (used.toFloat() / total.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val usedGb = used / 1e9
    val totalGb = (total / 1e9).roundToInt()
    val label = String.format(Locale.US, "%.1f GB / %d GB", usedGb, totalGb)

    Column(
        modifier = modifier.width(115.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = label,
            color = OzoneUi.TextSecondary,
            fontFamily = OzoneFont,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF223614)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(OzoneUi.Lime),
            )
        }
    }
}

/** Small green "wave" thumbnail shown on the Appearance row. */
@Composable
fun OzoneThemePreviewThumb(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Canvas(
        modifier = modifier
            .size(width = 74.dp, height = 34.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0D2407), Color(0xFF1E460D))))
            .border(1.dp, Color(0x28A3F52B), shape),
    ) {
        val w = size.width
        val h = size.height

        val wave = Path().apply {
            moveTo(0f, h * 0.70f)
            cubicTo(w * 0.30f, h * 0.40f, w * 0.50f, h * 0.90f, w * 0.72f, h * 0.48f)
            cubicTo(w * 0.82f, h * 0.28f, w * 0.92f, h * 0.12f, w, 0f)
        }

        // Soft ambient glow
        drawPath(
            path = wave,
            color = Color(0xFFA3F52B).copy(alpha = 0.22f),
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
        )

        // Core bright wave
        drawPath(
            path = wave,
            brush = Brush.horizontalGradient(listOf(Color(0xFF459914), OzoneUi.Lime)),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
