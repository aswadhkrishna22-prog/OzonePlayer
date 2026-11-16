/*
 * Ozone Player (2026)
 * Skin Animation Clock
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.player.player_0.skins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

@Composable
fun rememberSkinAnimationClock(isPlaying: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val deltaSec = (now - lastNanos) / 1_000_000_000f
                    seconds.floatValue += deltaSec
                }
                lastNanos = now
            }
        }
    }

    return seconds
}
