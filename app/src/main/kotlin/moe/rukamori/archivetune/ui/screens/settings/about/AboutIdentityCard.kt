/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.appicon.OzoneLogo
import moe.rukamori.archivetune.appicon.rememberCurrentAppIcon
import moe.rukamori.archivetune.ui.settings.OzoneUi
import moe.rukamori.archivetune.ui.theme.ozoneClickable
import moe.rukamori.archivetune.viewmodels.AboutUiModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutIdentityCard(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGplLicense: () -> Unit = {},
) {
    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(OzoneUi.SurfaceCard)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        OzoneUi.Lime.copy(alpha = 0.30f),
                        Color(0xFF1E2024),
                    ),
                ),
                cardShape,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurfaceAppIcon()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(model.appNameResId),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AboutMetadataBadge(text = model.versionName, isPrimary = true)
                    model.buildHash?.let { buildHash ->
                        AboutMetadataBadge(text = buildHash)
                    }
                    AboutMetadataBadge(text = model.buildVariant)
                }
            }

            HorizontalDivider(color = Color(0xFF1E2024))

            LinkChipRow(
                links = model.primaryLinks,
                onOpenUri = onOpenUri,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = AboutContract.CREDITS_NOTICE,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = OzoneUi.Lime,
                    textAlign = TextAlign.Center,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .ozoneClickable { onOpenUri(AboutContract.UPSTREAM_REPO_URL) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )

                Text(
                    text = "Source code available under GPL-3.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = OzoneUi.TextSecondary,
                    textAlign = TextAlign.Center,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .ozoneClickable { onOpenGplLicense() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )

                Text(
                    text = AboutContract.DISCLAIMER_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = OzoneUi.TextSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun SurfaceAppIcon(modifier: Modifier = Modifier) {
    val currentVariant = rememberCurrentAppIcon()
    Box(
        modifier = modifier
            .size(88.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        OzoneUi.Lime.copy(alpha = 0.14f),
                        Color.Transparent,
                    ),
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        OzoneLogo(
            variant = currentVariant,
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
internal fun AboutMetadataBadge(
    text: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPrimary) OzoneUi.LimeContainer else Color(0xFF181A1E),
        modifier = modifier
            .border(
                1.dp,
                if (isPrimary) OzoneUi.LimeBorder else Color(0xFF282B32),
                RoundedCornerShape(12.dp),
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
            color = if (isPrimary) OzoneUi.Lime else Color(0xFFD1D5DB),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
