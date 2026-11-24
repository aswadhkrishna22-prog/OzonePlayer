/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.account.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalMarqueeEnabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.LocalPreferenceInGroup
import moe.rukamori.archivetune.ui.settings.OzoneUi
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun ExpressiveSegmentedRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    selectedValue: Boolean,
    onValueSelected: (Boolean) -> Unit,
) {
    val isInGroup = LocalPreferenceInGroup.current
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (isInGroup) {
                        Modifier
                    } else {
                        Modifier
                            .clip(cardShape)
                            .background(Brush.verticalGradient(listOf(OzoneUi.CardTop, OzoneUi.CardBottom)))
                            .border(1.dp, OzoneUi.Hairline, cardShape)
                    }
                )
                .padding(
                    horizontal = SettingsDimensions.RowHorizontalPadding,
                    vertical = SettingsDimensions.RowVerticalPadding,
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemPaddingVertical)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.RowIconSpacing),
            ) {
                ExpressiveRowIcon(
                    icon = icon,
                    title = title,
                    tint = OzoneUi.Lime,
                )

                val marqueeEnabled = LocalMarqueeEnabled.current
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = OzoneUi.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OzoneUi.TextSecondary,
                            ),
                            maxLines = 1,
                            overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier,
                        )
                    }
                }
            }

            val indicatorOffset by animateFloatAsState(
                targetValue = if (selectedValue) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "SourceIndicatorOffset"
            )

            val barShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(OzoneUi.CardBottom)
                    .border(1.dp, OzoneUi.Hairline, barShape),
                contentAlignment = Alignment.CenterStart
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    val tabWidth = maxWidth / 2

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OzoneUi.Lime,
                        modifier = Modifier
                            .size(width = tabWidth, height = 36.dp)
                            .offset(x = tabWidth * indicatorOffset)
                    ) {}

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onValueSelected(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.home_provider_youtube_music),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!selectedValue) {
                                    OzoneUi.LimeDark
                                } else {
                                    OzoneUi.TextSecondary
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onValueSelected(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.home_provider_spotify),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedValue) {
                                    OzoneUi.LimeDark
                                } else {
                                    OzoneUi.TextSecondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveRowIcon(
    icon: Painter,
    title: String,
    tint: Color = OzoneUi.Lime,
    emphasized: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.18f), Color.Transparent),
                    ),
                    radius = size.minDimension / 1.5f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = title,
            modifier = Modifier.size(28.dp),
            tint = tint,
        )
    }
}
