package moe.rukamori.archivetune.kemo

import androidx.compose.ui.unit.dp

object KemoConfig {
    const val WALK_SPEED_DP_PER_SEC = 28f
    const val IDLE_MIN_DURATION_MS = 2500L
    const val IDLE_MAX_DURATION_MS = 5500L
    const val WALK_MIN_DURATION_MS = 3000L
    const val WALK_MAX_DURATION_MS = 7000L
    const val SLEEP_TIMEOUT_ALWAYS_MS = 30_000L
    const val FADE_OUT_DURATION_WHILE_PLAYING_MS = 20_000L
    const val FADE_TRANSITION_MS = 400
    const val HOP_DURATION_MS = 350L
    val HOP_HEIGHT_DP = 16.dp
    val LANE_CLEARANCE_DP = 2.dp
    val HORIZONTAL_PADDING_DP = 16.dp
}
