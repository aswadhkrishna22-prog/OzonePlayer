package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun DualPlayBetaDialog(
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = "Dual Play (Beta)",
                fontWeight = FontWeight.Bold,
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Text(
            text = "Dual Play is in beta. It works, but a few things are still being polished. Thanks for trying it!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DualPlayBetaChip(
    modifier: Modifier = Modifier,
    isGlass: Boolean = false,
) {
    if (isGlass) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.16f),
            modifier = modifier,
        ) {
            Text(
                text = "Beta",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(SettingsDimensions.BadgeCornerRadius),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier,
        ) {
            Text(
                text = "Beta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(
                    horizontal = SettingsDimensions.BadgePaddingH,
                    vertical = SettingsDimensions.BadgePaddingV,
                ),
            )
        }
    }
}

@Composable
fun DualPlayBetaInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGlass: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(24.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_about),
            contentDescription = "Dual Play Beta Info",
            tint = if (isGlass) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
