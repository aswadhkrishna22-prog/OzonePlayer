package moe.rukamori.archivetune.kemo

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.KemoDisplayMode
import moe.rukamori.archivetune.constants.KemoDisplayModeKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.utils.rememberEnumPreference

@Composable
fun KemoSettingsItem() {
    val (kemoMode, onKemoModeChange) =
        rememberEnumPreference(
            KemoDisplayModeKey,
            defaultValue = KemoDisplayMode.WHILE_MUSIC_PLAYS,
        )

    EnumListPreference(
        title = { Text(stringResource(R.string.kemo_title)) },
        description = stringResource(R.string.kemo_desc),
        icon = { Icon(painterResource(R.drawable.mood), null, modifier = Modifier.size(24.dp)) },
        selectedValue = kemoMode,
        onValueSelected = onKemoModeChange,
        valueText = {
            when (it) {
                KemoDisplayMode.OFF -> stringResource(R.string.kemo_off)
                KemoDisplayMode.ALWAYS -> stringResource(R.string.kemo_always)
                KemoDisplayMode.WHILE_MUSIC_PLAYS -> stringResource(R.string.kemo_while_music)
            }
        },
    )
}
