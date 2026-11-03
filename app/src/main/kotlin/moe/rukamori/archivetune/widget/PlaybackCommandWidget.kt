/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import moe.rukamori.archivetune.R

class PlaybackCommandWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackCommandContent(context)
        }
    }
}

@Composable
private fun PlaybackCommandContent(context: Context) {
    val prefs = currentState<Preferences>()
    val state = prefs.toWidgetPlaybackState(context)

    GlanceTheme(
        colors =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceTheme.colors
            } else {
                ArchiveTuneWidgetColors.providers
            },
    ) {
        val palette = rememberWidgetPalette(state.dominantColor)
        val size = LocalSize.current
        val compact = size.width < 240.dp || size.height < 124.dp

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(6.dp),
        ) {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .background(ImageProvider(R.drawable.widget_liquid_glass_bg))
                        .cornerRadius(32.dp)
                        .padding(14.dp)
                        .clickable(openArchiveTuneAction(context)),
            ) {
                if (compact) {
                    PlaybackCommandCompact(state = state, palette = palette, context = context)
                } else {
                    PlaybackCommandPanel(state = state, palette = palette, context = context)
                }
            }
        }
    }
}

@Composable
private fun PlaybackCommandCompact(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .size(52.dp)
                    .background(ImageProvider(R.drawable.widget_art_border))
                    .cornerRadius(16.dp)
                    .padding(1.dp),
        ) {
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = 50.dp,
                cornerRadius = 15.dp,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }

        Spacer(GlanceModifier.width(12.dp))

        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = state.title,
                style =
                    TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = state.artist,
                style =
                    TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                        fontSize = 12.sp,
                    ),
                maxLines = 1,
            )
        }

        if (state.isAvailable) {
            Spacer(GlanceModifier.width(10.dp))
            WidgetControlButton(
                modifier = GlanceModifier.size(48.dp),
                action = playPauseAction(),
                icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription =
                    context.getString(
                        if (state.isPlaying) R.string.widget_pause else R.string.play,
                    ),
                backgroundColor = ColorProvider(Color.White.copy(alpha = 0.20f)),
                contentColor = ColorProvider(Color.White),
                cornerRadius = 24.dp,
                iconSize = 26.dp,
            )
        }
    }
}

@Composable
private fun PlaybackCommandPanel(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Top row: Rounded square Artwork with 1dp border + Title & Artist
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    GlanceModifier
                        .size(56.dp)
                        .background(ImageProvider(R.drawable.widget_art_border))
                        .cornerRadius(16.dp)
                        .padding(1.dp),
            ) {
                WidgetArtwork(
                    artPath = state.artPath,
                    context = context,
                    contentDescription = context.getString(R.string.album_cover_desc),
                    targetSize = 54.dp,
                    cornerRadius = 15.dp,
                    palette = palette,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }

            Spacer(GlanceModifier.width(12.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    style =
                        TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = state.artist,
                    style =
                        TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                            fontSize = 13.sp,
                        ),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        // Progress row: Elapsed time (left) + Wavy progress bar (middle) + "-Remaining" (right)
        val positionMs =
            if (state.positionMs > 0L) {
                state.positionMs
            } else if (state.durationMs > 0L) {
                (state.playbackPosition * state.durationMs).toLong()
            } else {
                (state.playbackPosition * 210_000f).toLong()
            }

        val durationMs =
            if (state.durationMs > 0L) {
                state.durationMs
            } else {
                210_000L
            }

        val elapsedSec = if (state.isAvailable) (positionMs / 1000).toInt().coerceAtLeast(0) else 0
        val remainingSec =
            if (state.isAvailable && durationMs > positionMs) {
                ((durationMs - positionMs) / 1000).toInt().coerceAtLeast(0)
            } else {
                0
            }

        val elapsedStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
        val remainingStr = "-%d:%02d".format(remainingSec / 60, remainingSec % 60)

        val waveBitmap =
            remember(state.playbackPosition, state.wavePhase, state.isPlaying) {
                WavyProgressRenderer.render(
                    progress = state.playbackPosition,
                    phase = state.wavePhase,
                    isPlaying = state.isPlaying,
                )
            }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = elapsedStr,
                style =
                    TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(waveBitmap),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .height(20.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = remainingStr,
                style =
                    TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }

        Spacer(GlanceModifier.defaultWeight())

        // Bottom row: Heart, Previous, Play/Pause, Next evenly spaced; Play/Pause largest
        if (state.isAvailable) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Heart / Favorite toggle
                WidgetControlButton(
                    modifier = GlanceModifier.size(42.dp),
                    action = toggleLikeAction(),
                    icon = if (state.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                    contentDescription = context.getString(R.string.action_like),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor =
                        if (state.isLiked) {
                            ColorProvider(Color(0xFFFF3B30))
                        } else {
                            ColorProvider(Color.White.copy(alpha = 0.8f))
                        },
                    cornerRadius = 21.dp,
                    iconSize = 22.dp,
                )

                Spacer(GlanceModifier.defaultWeight())

                // Previous
                WidgetControlButton(
                    modifier = GlanceModifier.size(44.dp),
                    action = skipPreviousAction(),
                    icon = R.drawable.skip_previous,
                    contentDescription = context.getString(R.string.widget_previous),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = ColorProvider(Color.White),
                    cornerRadius = 22.dp,
                    iconSize = 24.dp,
                )

                Spacer(GlanceModifier.defaultWeight())

                // Play / Pause (largest circular glass button)
                WidgetControlButton(
                    modifier = GlanceModifier.size(56.dp),
                    action = playPauseAction(),
                    icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                    contentDescription =
                        context.getString(
                            if (state.isPlaying) R.string.widget_pause else R.string.play,
                        ),
                    backgroundColor = ColorProvider(Color.White.copy(alpha = 0.20f)),
                    contentColor = ColorProvider(Color.White),
                    cornerRadius = 28.dp,
                    iconSize = 30.dp,
                )

                Spacer(GlanceModifier.defaultWeight())

                // Next
                WidgetControlButton(
                    modifier = GlanceModifier.size(44.dp),
                    action = skipNextAction(),
                    icon = R.drawable.skip_next,
                    contentDescription = context.getString(R.string.next),
                    backgroundColor = ColorProvider(Color.Transparent),
                    contentColor = ColorProvider(Color.White),
                    cornerRadius = 22.dp,
                    iconSize = 24.dp,
                )
            }
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(ColorProvider(Color.White.copy(alpha = 0.10f)))
                        .cornerRadius(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.widget_tap_to_open),
                    style =
                        TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
            }
        }
    }
}
