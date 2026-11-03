package moe.rukamori.archivetune.kemo

import kotlinx.serialization.Serializable

@Serializable
data class KemoAnimationSpec(
    val frames: List<Int> = emptyList(),
    val loop: Boolean = true,
    val durationMs: Long = 400L,
)

@Serializable
data class KemoSpriteSpec(
    val frameWidth: Int = 32,
    val frameHeight: Int = 32,
    val fps: Int = 10,
    val animations: Map<String, KemoAnimationSpec> = emptyMap(),
)

enum class KemoState {
    IDLE,
    WALK,
    DANCE,
    SLEEP,
    HOP,
}
