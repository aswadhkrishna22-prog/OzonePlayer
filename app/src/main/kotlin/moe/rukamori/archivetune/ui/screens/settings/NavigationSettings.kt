/*
 * Ozone Player (2026)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.backToMain

@Composable
fun NavigationSettings(navController: NavController) {
    OzoneSettingsScaffold(
        title = {
            Text(
                text = stringResource(R.string.ozone_navigation_title),
                fontWeight = FontWeight.Bold,
            )
        },
        onBackClick = navController::navigateUp,
        onBackLongClick = navController::backToMain,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(OzoneUi.CardTop, OzoneUi.CardBottom)))
                .border(1.dp, OzoneUi.Hairline, shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OzoneUi.Lime.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ozn_grid),
                    contentDescription = null,
                    tint = OzoneUi.Lime,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ozone_navigation_title),
                color = OzoneUi.TextPrimary,
                fontFamily = OzoneFont,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ozone_navigation_subtitle),
                color = OzoneUi.TextSecondary,
                fontFamily = OzoneFont,
                fontSize = 15.sp,
            )
        }
    }
}
