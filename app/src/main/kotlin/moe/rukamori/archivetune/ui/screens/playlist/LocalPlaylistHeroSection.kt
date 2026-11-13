/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.core.common.math.lerp3
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.ui.component.HeaderType
import moe.rukamori.archivetune.ui.component.YumaMorphingHeader
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                    backgroundColor = LocalYumaColors.current.glassBackground,
                )
                .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LocalPlaylistMeshGradient(
    disableBlur: Boolean,
    gradientColors: List<Color>,
    gradientAlpha: Float,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.55f)
                    .zIndex(-1f)
                    .drawBehind {
                        val width = size.width
                        val height = size.height

                        if (gradientColors.size >= 3) {
                            val c0 = gradientColors[0]
                            val c1 = gradientColors[1]
                            val c2 = gradientColors[2]
                            val c3 = gradientColors.getOrElse(3) { c0 }
                            val c4 = gradientColors.getOrElse(4) { c1 }

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c0.copy(alpha = gradientAlpha * 0.75f),
                                                c0.copy(alpha = gradientAlpha * 0.4f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.15f),
                                        radius = width * 0.8f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c1.copy(alpha = gradientAlpha * 0.55f),
                                                c1.copy(alpha = gradientAlpha * 0.3f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.1f, height * 0.4f),
                                        radius = width * 0.6f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c2.copy(alpha = gradientAlpha * 0.5f),
                                                c2.copy(alpha = gradientAlpha * 0.25f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.9f, height * 0.35f),
                                        radius = width * 0.55f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c3.copy(alpha = gradientAlpha * 0.35f),
                                                c3.copy(alpha = gradientAlpha * 0.18f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.25f, height * 0.65f),
                                        radius = width * 0.75f,
                                    ),
                            )

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c4.copy(alpha = gradientAlpha * 0.3f),
                                                c4.copy(alpha = gradientAlpha * 0.15f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.55f, height * 0.85f),
                                        radius = width * 0.9f,
                                    ),
                            )
                        } else if (gradientColors.isNotEmpty()) {
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.7f),
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.35f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.25f),
                                        radius = width * 0.85f,
                                    ),
                            )
                        }

                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                            surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                            surfaceColor,
                                        ),
                                    startY = height * 0.4f,
                                    endY = height,
                                ),
                        )
                    },
        )
    }
}

@Composable
fun PlaylistCoverCollage(
    thumbnails: List<String>,
    modifier: Modifier = Modifier,
) {
    if (thumbnails.size == 1) {
        AsyncImage(
            model = thumbnails[0],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        )
    } else if (thumbnails.size > 1) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = thumbnails.getOrNull(0),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AsyncImage(
                    model = thumbnails.getOrNull(1),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = thumbnails.getOrNull(2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                AsyncImage(
                    model = thumbnails.getOrNull(3),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.queue_music),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlaylistMorphingHeader(
    thumbnails: List<String>,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
) {
    YumaMorphingHeader(
        collapseFraction = collapseFraction,
        type = HeaderType.PLAYLIST,
        modifier = modifier,
    ) {
        PlaylistCoverCollage(
            thumbnails = thumbnails,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun LocalPlaylistHeroSection(
    playlist: Playlist,
    playlistLength: Int,
    gradientColors: List<Color>,
    systemBarsTopPadding: Dp,
    downloadState: HeaderDownloadState,
    actions: LocalPlaylistActions,
    heroSpacerHeight: Dp,
    collapseFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val editable = playlist.playlist.isEditable
    val editCoverLabel = stringResource(R.string.change_playlist_cover)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = systemBarsTopPadding + AppBarHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(heroSpacerHeight),
        ) {
            if (editable) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = SettingsDimensions.ScreenHorizontalPadding,
                                bottom = 8.dp,
                            )
                            .graphicsLayer {
                                val fraction = collapseFractionProvider()
                                val currentScale = lerp3(start = 1f, mid = 0.44f, end = 0f, fraction = fraction)
                                scaleX = currentScale
                                scaleY = currentScale
                                alpha = (1f - fraction * 2.5f).coerceIn(0f, 1f)
                            }
                            .size(48.dp)
                            .yumaClickable(
                                enabled = collapseFractionProvider() < 0.3f,
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = actions.onPickCover,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = editCoverLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }
        }

        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val remoteSongCount = playlist.playlist.remoteSongCount
            val songCount =
                if (playlist.songCount == 0 && remoteSongCount != null) {
                    remoteSongCount
                } else {
                    playlist.songCount
                }
            MetadataChip(
                icon = R.drawable.music_note,
                text = pluralStringResource(R.plurals.n_song, songCount, songCount),
                modifier = Modifier.weight(1f, fill = false),
            )

            if (playlistLength > 0) {
                MetadataChip(
                    icon = R.drawable.timer,
                    text = makeTimeString(playlistLength * 1000L),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val playLabel = stringResource(R.string.play)
        val shuffleLabel = stringResource(R.string.shuffle)
        val deleteLabel = stringResource(R.string.delete)
        val likeLabel = stringResource(R.string.liked)
        val downloadLabel = stringResource(R.string.download)
        val editLabel = stringResource(R.string.edit)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editable) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = actions.onDelete,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = deleteLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            } else {
                val liked = playlist.playlist.bookmarkedAt != null
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = actions.onToggleLike,
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = likeLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (liked) R.drawable.favorite else R.drawable.favorite_border,
                            ),
                        contentDescription = null,
                        tint =
                            if (liked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = actions.onPlay,
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = playLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = actions.onShuffle,
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = shuffleLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaCombinedClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = actions.onDownload,
                            onLongClick = actions.onDownloadLongClick,
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = downloadLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                val state = downloadState
                when (state) {
                    HeaderDownloadState.Completed -> {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                    }

                    is HeaderDownloadState.Partial -> {
                        HeaderDownloadProgressIndicator(progress = state.progress)
                    }

                    else -> {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

fun LazyListScope.localPlaylistHeroItem(
    playlist: Playlist,
    playlistLength: Int,
    gradientColors: List<Color>,
    systemBarsTopPadding: Dp,
    downloadState: HeaderDownloadState,
    actions: LocalPlaylistActions,
    heroSpacerHeight: Dp,
    collapseFractionProvider: () -> Float,
) {
    item(key = LOCAL_PLAYLIST_KEY_HEADER, contentType = CONTENT_TYPE_LOCAL_PLAYLIST_HEADER) {
        LocalPlaylistHeroSection(
            playlist = playlist,
            playlistLength = playlistLength,
            gradientColors = gradientColors,
            systemBarsTopPadding = systemBarsTopPadding,
            downloadState = downloadState,
            actions = actions,
            heroSpacerHeight = heroSpacerHeight,
            collapseFractionProvider = collapseFractionProvider,
        )
    }
}
