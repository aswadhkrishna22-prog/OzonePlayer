/*
 * Ozone Player (2026)
 * Retro Cassette Player Skin with Dynamic Turning Reels
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.player.player_0.skins

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CASSETTE_SHELL = Color(0xFF1D1B22)
private val CASSETTE_LABEL = Color(0xFFF4F1EA)
private val CASSETTE_INK = Color(0xFF16141A)
private val CASSETTE_TAPE = Color(0xFF3A2A22)
private val CASSETTE_WINDOW = Color(0xFF0E0D12)

@Composable
fun CassetteSkin(
    coverPainter: Painter?,
    songTitle: String?,
    artistName: String?,
    isPlaying: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val clockSeconds by rememberSkinAnimationClock(isPlaying = isPlaying)
    val accent = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.58f)
            .clip(RoundedCornerShape(20.dp))
            .background(CASSETTE_SHELL),
    ) {
        val w = maxWidth
        val clampedProgress = progress.coerceIn(0f, 1f)

        // Reels and tape canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCassetteReels(
                progress = clampedProgress,
                seconds = clockSeconds,
                accent = accent,
            )
        }

        // Cassette Label: cover art, title, artist, and Side A
        Row(
            modifier = Modifier
                .padding(start = w * 0.075f, end = w * 0.075f, top = w * 0.045f)
                .fillMaxWidth()
                .aspectRatio(4.4f)
                .clip(RoundedCornerShape(8.dp))
                .background(CASSETTE_LABEL)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coverPainter != null) {
                Image(
                    painter = coverPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(5.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(CASSETTE_SHELL),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = songTitle?.ifEmpty { "Playing Track" } ?: "Playing Track",
                    color = CASSETTE_INK,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artistName?.ifEmpty { "Ozone Cassette" } ?: "Ozone Cassette",
                    color = CASSETTE_INK.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = "A",
                color = accent,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private fun DrawScope.drawCassetteReels(
    progress: Float,
    seconds: Float,
    accent: Color,
) {
    val w = size.width
    val h = size.height

    // Cassette viewing window
    val win = Size(w * 0.62f, h * 0.35f)
    val winTop = Offset((w - win.width) / 2f, h * 0.44f)
    drawRoundRect(
        color = CASSETTE_WINDOW,
        topLeft = winTop,
        size = win,
        cornerRadius = CornerRadius(win.height / 2f),
    )

    val cy = winTop.y + win.height / 2f
    val leftCenter = Offset(winTop.x + win.height / 2f, cy)
    val rightCenter = Offset(winTop.x + win.width - win.height / 2f, cy)

    val hubRadius = win.height * 0.20f
    val fullRadius = win.height * 0.47f
    val emptyRadius = hubRadius * 1.25f

    // Physical tape winding progression: left decreases, right increases
    val rLeft = emptyRadius + (fullRadius - emptyRadius) * (1f - progress)
    val rRight = emptyRadius + (fullRadius - emptyRadius) * progress

    // Magnetic tape bridge line
    drawLine(
        color = CASSETTE_TAPE,
        start = Offset(leftCenter.x, cy + rLeft),
        end = Offset(rightCenter.x, cy + rRight),
        strokeWidth = 3f,
    )

    // Turning spools (angular velocity is inversely proportional to spool radius)
    val tapeSpeed = 34f
    drawReelSpool(leftCenter, rLeft, hubRadius, seconds * tapeSpeed / rLeft * 57.3f)
    drawReelSpool(rightCenter, rRight, hubRadius, seconds * tapeSpeed / rRight * 57.3f)

    // Accent strip at bottom
    drawRoundRect(
        color = accent.copy(alpha = 0.85f),
        topLeft = Offset(w * 0.075f, h * 0.85f),
        size = Size(w * 0.85f, h * 0.025f),
        cornerRadius = CornerRadius(h * 0.015f),
    )

    // Corner screw holes
    listOf(
        Offset(w * 0.04f, h * 0.06f),
        Offset(w * 0.96f, h * 0.06f),
        Offset(w * 0.04f, h * 0.94f),
        Offset(w * 0.96f, h * 0.94f),
        Offset(w * 0.50f, h * 0.94f),
    ).forEach { centerPoint ->
        drawCircle(Color.White.copy(alpha = 0.2f), radius = h * 0.018f, center = centerPoint)
    }
}

private fun DrawScope.drawReelSpool(
    center: Offset,
    radius: Float,
    hub: Float,
    degrees: Float,
) {
    // Tape wound on the spool
    drawCircle(CASSETTE_TAPE, radius = radius, center = center)
    drawCircle(Color.White.copy(alpha = 0.08f), radius = radius, center = center, style = Stroke(1.5f))

    // Inner plastic hub
    drawCircle(Color(0xFFE9E5DC), radius = hub, center = center)

    // 6-spoke spinning teeth
    rotate(degrees, center) {
        repeat(6) { i ->
            val angle = i * PI.toFloat() / 3f
            drawLine(
                color = CASSETTE_WINDOW,
                start = Offset(center.x + cos(angle) * hub * 0.35f, center.y + sin(angle) * hub * 0.35f),
                end = Offset(center.x + cos(angle) * hub * 0.85f, center.y + sin(angle) * hub * 0.85f),
                strokeWidth = hub * 0.18f,
                cap = StrokeCap.Round,
            )
        }
    }

    // Spindle axle hole
    drawCircle(CASSETTE_WINDOW, radius = hub * 0.28f, center = center)
}
