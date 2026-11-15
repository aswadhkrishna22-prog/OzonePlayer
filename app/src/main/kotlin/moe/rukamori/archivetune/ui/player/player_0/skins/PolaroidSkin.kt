/*
 * Ozone Player (2026)
 * Polaroid Instant Photo Player Skin with Organic Gentle Sway
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.player.player_0.skins

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

private val POLAROID_PAPER = Color(0xFFFAF8F3)
private val POLAROID_INK = Color(0xFF16141A)

@Composable
fun PolaroidSkin(
    coverPainter: Painter?,
    songTitle: String?,
    artistName: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val clockSeconds by rememberSkinAnimationClock(isPlaying = isPlaying)
    val swayDegrees = if (isPlaying) -2.2f + 1.4f * sin(clockSeconds * 0.9f) else -2.2f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .graphicsLayer {
                    rotationZ = swayDegrees
                    shadowElevation = 20.dp.toPx()
                    shape = RoundedCornerShape(8.dp)
                    clip = true
                }
                .background(POLAROID_PAPER)
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Instant photo square viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                if (coverPainter != null) {
                    Image(
                        painter = coverPainter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Handwritten caption metadata
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = songTitle?.ifEmpty { "Now Playing" } ?: "Now Playing",
                    color = POLAROID_INK,
                    fontSize = 17.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artistName?.ifEmpty { "Unknown Artist" } ?: "Unknown Artist",
                    color = POLAROID_INK.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
