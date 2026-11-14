/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AlbumFilter
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalOzoneColors
import moe.rukamori.archivetune.ui.theme.ozoneClickable
import moe.rukamori.archivetune.ui.theme.ozoneGlassCard

@Composable
fun AlbumsControlsRow(
    filter: AlbumFilter,
    sortType: AlbumSortType,
    sortDescending: Boolean,
    isGridView: Boolean,
    onFilterChange: (AlbumFilter) -> Unit,
    onSortTypeChange: (AlbumSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onViewModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ozoneColors = LocalOzoneColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var showSortMenu by remember { mutableStateOf(false) }
        val currentSortLabel =
            if (filter == AlbumFilter.DOWNLOADED_FULL) {
                stringResource(R.string.filter_downloaded)
            } else {
                when (sortType) {
                    AlbumSortType.CREATE_DATE -> {
                        if (sortDescending) {
                            stringResource(R.string.newest_first)
                        } else {
                            stringResource(R.string.oldest_first)
                        }
                    }

                    AlbumSortType.NAME -> {
                        if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                    }

                    AlbumSortType.ARTIST -> {
                        stringResource(R.string.sort_artist)
                    }

                    AlbumSortType.YEAR -> {
                        if (sortDescending) stringResource(R.string.newest_year) else stringResource(R.string.oldest_year)
                    }

                    AlbumSortType.SONG_COUNT -> {
                        if (sortDescending) {
                            stringResource(R.string.most_tracks)
                        } else {
                            stringResource(R.string.least_tracks)
                        }
                    }

                    AlbumSortType.LENGTH -> {
                        if (sortDescending) {
                            stringResource(R.string.longest_duration)
                        } else {
                            stringResource(R.string.shortest_duration)
                        }
                    }

                    AlbumSortType.PLAY_TIME -> {
                        stringResource(R.string.most_played_sort)
                    }
                }
            }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Row(
                    modifier =
                        Modifier
                            .height(SettingsDimensions.LibraryChipHeight)
                            .ozoneClickable { showSortMenu = true }
                            .ozoneGlassCard(
                                shape = CircleShape,
                                backgroundColor = ozoneColors.glassBackground,
                                borderColor = ozoneColors.glassBorder,
                                strokeWidth = SettingsDimensions.GlassBorderThickness,
                            )
                            .clip(CircleShape)
                            .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentSortLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    AlbumSortType.entries.forEach { type ->
                        val label =
                            when (type) {
                                AlbumSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                AlbumSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                AlbumSortType.ARTIST -> stringResource(R.string.sort_artist)
                                AlbumSortType.YEAR -> stringResource(R.string.year_sort)
                                AlbumSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                AlbumSortType.LENGTH -> stringResource(R.string.duration_sort)
                                AlbumSortType.PLAY_TIME -> stringResource(R.string.most_played_sort)
                            }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onFilterChange(AlbumFilter.LIKED)
                                onSortTypeChange(type)
                                if (type == AlbumSortType.NAME) onSortDescendingChange(false)
                                showSortMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_downloaded)) },
                        onClick = {
                            onFilterChange(AlbumFilter.DOWNLOADED_FULL)
                            showSortMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.offline),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier =
                    Modifier
                        .size(SettingsDimensions.LibraryChipHeight)
                        .ozoneClickable { onSortDescendingChange(!sortDescending) }
                        .ozoneGlassCard(
                            shape = CircleShape,
                            backgroundColor = ozoneColors.glassBackground,
                            borderColor = ozoneColors.glassBorder,
                            strokeWidth = SettingsDimensions.GlassBorderThickness,
                        )
                        .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            id = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                        ),
                    contentDescription =
                        if (sortDescending) {
                            stringResource(R.string.sort_descending)
                        } else {
                            stringResource(R.string.sort_ascending)
                        },
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .height(SettingsDimensions.LibraryChipHeight)
                    .ozoneGlassCard(
                        shape = CircleShape,
                        backgroundColor = ozoneColors.glassBackground,
                        borderColor = ozoneColors.glassBorder,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                    )
                    .clip(CircleShape)
                    .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (!isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .ozoneClickable { onViewModeChange(false) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.queue_music),
                    contentDescription = stringResource(R.string.list_view),
                    tint = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .ozoneClickable { onViewModeChange(true) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.album),
                    contentDescription = stringResource(R.string.grid_view),
                    tint = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
