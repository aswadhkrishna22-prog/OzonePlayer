/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder

@Composable
internal fun HomeShimmer(
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val insetsPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    ShimmerHost(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val heroHeight = HomeLayoutConfig.heroCarouselHeight(maxWidth, configuration.screenHeightDp.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = insetsPadding.calculateLeftPadding(layoutDirection),
                        top = 0.dp,
                        end = insetsPadding.calculateRightPadding(layoutDirection),
                        bottom = insetsPadding.calculateBottomPadding(),
                    ),
            ) {
                // Full bleed hero placeholder (edge to edge, extending behind status bar)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

                Spacer(modifier = Modifier.height(HomeLayoutConfig.sectionSpacing))

                // Continue block placeholder (2-column grid of 56dp tiles, 12dp radius)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HomeLayoutConfig.sidePadding),
                ) {
                    repeat(2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            repeat(2) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                ) {
                                    Spacer(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    TextPlaceholder(
                                        height = 14.dp,
                                        modifier = Modifier
                                            .width(70.dp)
                                            .padding(end = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 1 Header (24dp top, 12dp bottom padding)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = HomeLayoutConfig.sidePadding,
                            end = HomeLayoutConfig.sidePadding,
                            top = HomeLayoutConfig.headerTopPadding,
                            bottom = HomeLayoutConfig.headerBottomPadding,
                        ),
                ) {
                    TextPlaceholder(
                        height = 20.dp,
                        modifier = Modifier.width(150.dp),
                    )
                }

                // Section 1 Shelf matching 148dp card sizes with 16dp radius
                LazyRow(
                    contentPadding = PaddingValues(horizontal = HomeLayoutConfig.sidePadding),
                    horizontalArrangement = Arrangement.spacedBy(HomeLayoutConfig.shelfSpacing),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(5) {
                        GridItemPlaceHolder(
                            thumbnailShape = RoundedCornerShape(HomeLayoutConfig.cardCornerRadius),
                            cardWidth = HomeLayoutConfig.cardWidth,
                        )
                    }
                }
            }
        }
    }
}
