/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.constants.GridThumbnailCornerRadius
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
            val cardWidth = HomeLayoutConfig.cardWidth(maxWidth)

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
                // Hero placeholder matching real hero carousel height
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )

                Spacer(modifier = Modifier.height(HomeLayoutConfig.sectionSpacing))

                // Section 1 Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HomeLayoutConfig.headerHeight)
                        .padding(horizontal = HomeLayoutConfig.sidePadding, vertical = 4.dp),
                ) {
                    TextPlaceholder(
                        height = 20.dp,
                        modifier = Modifier.width(160.dp),
                    )
                }

                // Section 1 Shelf matching real card sizes
                LazyRow(
                    contentPadding = PaddingValues(horizontal = HomeLayoutConfig.sidePadding),
                    horizontalArrangement = Arrangement.spacedBy(HomeLayoutConfig.shelfSpacing),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(5) {
                        GridItemPlaceHolder(
                            thumbnailShape = RoundedCornerShape(GridThumbnailCornerRadius),
                            cardWidth = cardWidth,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(HomeLayoutConfig.sectionSpacing))

                // Section 2 Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HomeLayoutConfig.headerHeight)
                        .padding(horizontal = HomeLayoutConfig.sidePadding, vertical = 4.dp),
                ) {
                    TextPlaceholder(
                        height = 20.dp,
                        modifier = Modifier.width(130.dp),
                    )
                }

                // Section 2 Shelf matching real card sizes
                LazyRow(
                    contentPadding = PaddingValues(horizontal = HomeLayoutConfig.sidePadding),
                    horizontalArrangement = Arrangement.spacedBy(HomeLayoutConfig.shelfSpacing),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(5) {
                        GridItemPlaceHolder(
                            thumbnailShape = RoundedCornerShape(GridThumbnailCornerRadius),
                            cardWidth = cardWidth,
                        )
                    }
                }
            }
        }
    }
}
