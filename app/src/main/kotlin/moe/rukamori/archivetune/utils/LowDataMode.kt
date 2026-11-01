/*
 * Ozone Player (2026) | Low Data Mode
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import moe.rukamori.archivetune.lowdata.LowDataManager

fun Context.isLowDataModeActive(): Boolean = LowDataManager.isLowDataModeActive.value

fun Context.isLowDataModeActive(enabled: Boolean): Boolean =
    if (!enabled) false else LowDataManager.isLowDataModeActive.value

@Composable
fun rememberLowDataModeActive(): Boolean {
    val active by LowDataManager.isLowDataModeActive.collectAsState()
    return active
}

@Composable
fun rememberLowDataModeActive(enabled: Boolean): Boolean {
    val active by LowDataManager.isLowDataModeActive.collectAsState()
    return if (!enabled) false else active
}
