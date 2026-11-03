package moe.rukamori.archivetune.kemo

import androidx.compose.ui.unit.dp

object KemoConfig {
    const val BLINK_MIN_INTERVAL_MS = 3000L
    const val BLINK_MAX_INTERVAL_MS = 6000L

    const val IDLE_MIN_INTERVAL_MS = 3000L
    const val IDLE_MAX_INTERVAL_MS = 5500L

    const val SLEEP_TIMEOUT_ALWAYS_MS = 30_000L
    const val FADE_OUT_DURATION_WHILE_PLAYING_MS = 20_000L
    const val FADE_TRANSITION_MS = 400

    // Hop walk movement per hop in sprite pixels
    const val HOP_WALK_DISTANCE_SPRITE_PX = 20

    // Particles config
    const val PARTICLE_FLOAT_SPRITE_PX = 12 // floats up about 12 sprite pixels
    const val PARTICLE_DURATION_MS = 800L // fades out in about 800 ms
    const val DANCE_NOTE_INTERVAL_MS = 480L // emits alternating note and note2
    const val SLEEP_ZZZ_INTERVAL_MS = 1400L // emits zzz while sleeping

    val LANE_CLEARANCE_DP = 2.dp
    val HORIZONTAL_PADDING_DP = 16.dp
}
