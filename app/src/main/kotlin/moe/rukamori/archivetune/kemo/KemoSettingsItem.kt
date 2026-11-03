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
import moe.rukamori.archivetune.constants.KemoSize
import moe.rukamori.archivetune.constants.KemoSizeKey
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.utils.rememberEnumPreference

@Composable
fun KemoSettingsItem() {
    val (kemoMode, onKemoModeChange) =
        rememberEnumPreference(
            KemoDisplayModeKey,
            defaultValue = KemoDisplayMode.WHILE_MUSIC_PLAYS,
        )
    val (kemoSize, onKemoSizeChange) =
        rememberEnumPreference(
            KemoSizeKey,
            defaultValue = KemoSize.NORMAL,
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

    if (kemoMode != KemoDisplayMode.OFF) {
        EnumListPreference(
            title = { Text(stringResource(R.string.kemo_size_title)) },
            description = stringResource(R.string.kemo_size_desc),
            icon = { Icon(painterResource(R.drawable.style), null, modifier = Modifier.size(24.dp)) },
            selectedValue = kemoSize,
            onValueSelected = onKemoSizeChange,
            valueText = {
                when (it) {
                    KemoSize.SMALL -> stringResource(R.string.kemo_size_small)
                    KemoSize.NORMAL -> stringResource(R.string.kemo_size_normal)
                    KemoSize.LARGE -> stringResource(R.string.kemo_size_large)
                }
            },
        )
    }
}
