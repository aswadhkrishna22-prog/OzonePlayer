package moe.rukamori.archivetune.kemo

import kotlinx.serialization.Serializable

@Serializable
data class KemoStepSpec(
    val f: Int = 0,
    val ms: Long = 200L,
    val dy: Int = 0,
    val flip: Boolean = false,
)

@Serializable
data class KemoAnimationSpec(
    val loop: Boolean = true,
    val steps: List<KemoStepSpec> = emptyList(),
)

@Serializable
data class KemoParticlesSpec(
    val file: String = "kemo_particles.png",
    val cell: Int = 16,
    val items: List<String> = emptyList(),
)

@Serializable
data class KemoSpec(
    val frameWidth: Int = 64,
    val frameHeight: Int = 96,
    val anchor: String = "bottomCenter",
    val facing: String = "right",
    val frames: Map<String, Int> = emptyMap(),
    val animations: Map<String, KemoAnimationSpec> = emptyMap(),
    val particles: KemoParticlesSpec = KemoParticlesSpec(),
)

enum class KemoState {
    IDLE,
    BLINK,
    HOP,
    DANCE,
    SLEEP,
    HAPPY,
}
