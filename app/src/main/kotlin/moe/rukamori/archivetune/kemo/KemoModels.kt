package moe.rukamori.archivetune.kemo

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.constants.KemoDisplayMode

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
    HELD,
}

data class KemoStateInputs(
    val mode: KemoDisplayMode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
    val isDragging: Boolean = false,
    val activeOneShot: KemoState? = null,
    val isMusicPlaying: Boolean = false,
    val isSleepTimeoutReached: Boolean = false,
)

/**
 * Pure function determining the active KemoState with strict priority:
 * 1. Mode OFF: returns null (nothing composed/rendered)
 * 2. Held (while being dragged): returns [KemoState.HELD]
 * 3. One-shot animation (hop / happy / blink): returns [activeOneShot]
 * 4. Dance (music is playing): returns [KemoState.DANCE]
 * 5. Sleep (nothing playing for the sleep delay): returns [KemoState.SLEEP]
 * 6. Idle / Walk: returns [KemoState.IDLE]
 */
fun nextKemoState(inputs: KemoStateInputs): KemoState? {
    if (inputs.mode == KemoDisplayMode.OFF) return null
    if (inputs.isDragging) return KemoState.HELD
    if (inputs.activeOneShot != null) return inputs.activeOneShot
    if (inputs.isMusicPlaying) return KemoState.DANCE
    if (inputs.isSleepTimeoutReached) return KemoState.SLEEP
    return KemoState.IDLE
}

fun nextKemoState(
    mode: KemoDisplayMode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
    isDragging: Boolean = false,
    activeOneShot: KemoState? = null,
    isMusicPlaying: Boolean = false,
    isSleepTimeoutReached: Boolean = false,
): KemoState? = nextKemoState(
    KemoStateInputs(
        mode = mode,
        isDragging = isDragging,
        activeOneShot = activeOneShot,
        isMusicPlaying = isMusicPlaying,
        isSleepTimeoutReached = isSleepTimeoutReached,
    ),
)

fun nextState(inputs: KemoStateInputs): KemoState? = nextKemoState(inputs)

fun nextState(
    mode: KemoDisplayMode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
    isDragging: Boolean = false,
    activeOneShot: KemoState? = null,
    isMusicPlaying: Boolean = false,
    isSleepTimeoutReached: Boolean = false,
): KemoState? = nextKemoState(mode, isDragging, activeOneShot, isMusicPlaying, isSleepTimeoutReached)
