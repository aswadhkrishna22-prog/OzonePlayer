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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.OzoneUi

// ---------------------------------------------------------------------------------------------
// Header: Collapsing OZONE Settings Header & Search Bar
// ---------------------------------------------------------------------------------------------

@Composable
fun OzoneSettingsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    collapseFraction: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = (1f - collapseFraction * 1.5f).coerceIn(0f, 1f)
                    },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 110.dp)) {
                Text(
                    text = stringResource(R.string.ozone_brand_name),
                    color = OzoneUi.Lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            OzoneVinylArc(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 8.dp)
                        .graphicsLayer {
                            val fade = (1f - collapseFraction * 1.8f).coerceIn(0f, 1f)
                            alpha = fade
                            scaleX = 0.85f * (0.8f + 0.2f * fade)
                            scaleY = 0.85f * (0.8f + 0.2f * fade)
                        },
            )
        }

        Spacer(Modifier.height(16.dp))

        OzoneSearchBar(query = query, onQueryChange = onQueryChange)
    }
}

/** Compact collapsing toolbar that appears on scroll with just the "Settings" title. */
@Composable
fun OzoneSettingsTopBar(
    collapseFraction: Float,
    modifier: Modifier = Modifier,
) {
    if (collapseFraction <= 0.01f) return

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(OzoneUi.Bg.copy(alpha = collapseFraction))
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.settings),
            color = Color.White.copy(alpha = collapseFraction),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        if (collapseFraction > 0.85f) {
            val dividerAlpha = ((collapseFraction - 0.85f) / 0.15f).coerceIn(0f, 1f)
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                thickness = 1.dp,
                color = OzoneUi.Hairline.copy(alpha = dividerAlpha),
            )
        }
    }
}

/** Half "vinyl record" decorative element scaled down. */
@Composable
fun OzoneVinylArc(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 120.dp, height = 72.dp).clipToBounds()) {
        val outer = 60.dp.toPx()
        val inner = 20.dp.toPx()
        val center = Offset(size.width / 2f, outer)

        drawCircle(
            brush =
                Brush.sweepGradient(
                    0.00f to Color(0xFF0E2606),
                    0.52f to Color(0xFF1C4509),
                    0.68f to Color(0xFF3F8A10),
                    0.76f to Color(0xFFA3F52B),
                    0.84f to Color(0xFF3A7D0E),
                    0.92f to Color(0xFF173A08),
                    1.00f to Color(0xFF0E2606),
                    center = center,
                ),
            radius = outer,
            center = center,
        )
        // vinyl grooves
        var r = inner + 3.dp.toPx()
        while (r < outer) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = r,
                center = center,
                style = Stroke(width = 0.6.dp.toPx()),
            )
            r += 3.5.dp.toPx()
        }
        // centre hole
        drawCircle(color = OzoneUi.Bg, radius = inner, center = center)
    }
}

@Composable
fun OzoneSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(shape)
                .background(OzoneUi.SurfaceElevated)
                .border(1.dp, OzoneUi.Hairline, shape)
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ozn_search),
            contentDescription = null,
            tint = OzoneUi.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.ozone_search_settings),
                    color = OzoneUi.TextSecondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = OzoneUi.TextPrimary, fontSize = 16.sp),
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
            color = OzoneUi.SectionLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(0x1FFFFFFF)),
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
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(OzoneUi.CardTop, OzoneUi.CardBottom)))
                .border(1.dp, OzoneUi.Hairline, shape),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = OzoneUi.Hairline)
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
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = item.onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Neutral icon container with update indicator
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = item.icon,
                contentDescription = null,
                tint = OzoneUi.TextSecondary,
                modifier = Modifier.size(26.dp),
            )
            if (item.showUpdateIndicator) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = OzoneUi.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    color = OzoneUi.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 2,
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
            tint = OzoneUi.Chevron,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun OzonePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = OzoneUi.LimeDark,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(OzoneUi.Lime)
                .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Trailing widgets
// ---------------------------------------------------------------------------------------------

/** Used / total device storage with a lime progress bar. */
@Composable
fun OzoneStorageMeter(modifier: Modifier = Modifier) {
    val (used, total) =
        remember {
            runCatching {
                val st = StatFs(Environment.getDataDirectory().path)
                val t = st.totalBytes
                (t - st.availableBytes) to t
            }.getOrDefault(0L to 1L)
        }
    val fraction = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val label =
        String.format(Locale.US, "%.1f GB / %d GB", used / 1e9, (total / 1e9).roundToInt())

    Column(modifier = modifier.width(128.dp), horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            color = OzoneUi.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(OzoneUi.Lime.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(OzoneUi.Lime),
            )
        }
    }
}

/** Small green "wave" thumbnail shown on the Appearance row. */
@Composable
fun OzoneThemePreviewThumb(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Canvas(
        modifier =
            modifier
                .size(width = 100.dp, height = 52.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF0B2205), Color(0xFF2D6A0E))))
                .border(1.dp, OzoneUi.Hairline, shape),
    ) {
        val w = size.width
        val h = size.height
        val wave =
            Path().apply {
                moveTo(0f, h * 0.75f)
                cubicTo(w * 0.25f, h * 0.35f, w * 0.45f, h * 1.0f, w * 0.65f, h * 0.55f)
                cubicTo(w * 0.78f, h * 0.30f, w * 0.88f, h * 0.15f, w * 0.96f, h * 0.0f)
            }
        drawPath(
            path = wave,
            brush = Brush.horizontalGradient(listOf(Color(0xFF3F8A10), OzoneUi.Lime)),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
