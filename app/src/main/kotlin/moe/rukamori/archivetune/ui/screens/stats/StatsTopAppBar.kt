/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.ui.settings.OzoneUi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsTopAppBar(
    navController: NavController,
    onOpenYearPicker: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "OZONE",
                    color = OzoneUi.Lime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Text(
                    text = stringResource(R.string.stats),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
        actions = {
            IconButton(
                onClick = onOpenYearPicker,
                onLongClick = {},
            ) {
                Icon(
                    painterResource(R.drawable.auto_awesome),
                    contentDescription = stringResource(R.string.year_in_music),
                    tint = OzoneUi.Lime,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}
