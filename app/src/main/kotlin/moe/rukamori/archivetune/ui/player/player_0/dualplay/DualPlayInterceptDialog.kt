/*
 * Ozone Player (2026) | Dual Play Intercept Dialog
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.ui.player.player_0.dualplay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.dualplay.PendingDualPlayQueue

@Composable
fun DualPlayInterceptDialog(
    pending: PendingDualPlayQueue,
    hasSongB: Boolean,
    onAddToDualPlay: () -> Unit,
    onPlayInstead: () -> Unit,
    onCancel: () -> Unit,
) {
    val addLabel = if (hasSongB) "Replace Song B" else "Add to Dual Play"

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(painterResource(R.drawable.equalizer), contentDescription = null)
        },
        title = {
            Text(text = "Dual Play Active", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(text = "\"${pending.songTitle}\"")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hasSongB) {
                        "Do you want to replace Song B (Right ear) or play this song normally?"
                    } else {
                        "Do you want to add this song as Song B (Right ear) or replace the currently playing song?"
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onAddToDualPlay) {
                Text(addLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onPlayInstead) {
                Text("Play instead")
            }
        },
    )
}
