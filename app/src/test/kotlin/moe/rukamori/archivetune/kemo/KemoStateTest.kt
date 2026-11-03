package moe.rukamori.archivetune.kemo

import moe.rukamori.archivetune.constants.KemoDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KemoStateTest {

    @Test
    fun musicPlayingAndNotDragged_returnsDance() {
        val state = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
                isDragging = false,
                activeOneShot = null,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
        assertEquals(KemoState.DANCE, state)

        // Also test nextState overload
        assertEquals(
            KemoState.DANCE,
            nextState(
                mode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
                isDragging = false,
                activeOneShot = null,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
    }

    @Test
    fun dragged_returnsHeld() {
        // Dragging takes priority over music playback
        val state = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
                isDragging = true,
                activeOneShot = null,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
        assertEquals(KemoState.HELD, state)

        // Dragging also takes priority over one-shot
        val stateDuringOneShot = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
                isDragging = true,
                activeOneShot = KemoState.HOP,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
        assertEquals(KemoState.HELD, stateDuringOneShot)
    }

    @Test
    fun droppedWhileMusicPlays_returnsHopThenDance() {
        // 1. On drop: isDragging is cleared, one-shot HOP begins
        val onDropState = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
                isDragging = false,
                activeOneShot = KemoState.HOP,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
        assertEquals(KemoState.HOP, onDropState)

        // 2. Once the one-shot HOP finishes (activeOneShot = null), transitions back to DANCE
        val afterHopState = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.WHILE_MUSIC_PLAYS,
                isDragging = false,
                activeOneShot = null,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
        assertEquals(KemoState.DANCE, afterHopState)
    }

    @Test
    fun musicStops_sleepsAfterDelay() {
        // 1. Music stops: before delay is reached -> IDLE (walk/idle)
        val beforeDelayState = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.ALWAYS,
                isDragging = false,
                activeOneShot = null,
                isMusicPlaying = false,
                isSleepTimeoutReached = false,
            ),
        )
        assertEquals(KemoState.IDLE, beforeDelayState)

        // 2. After sleep delay is reached -> SLEEP
        val afterDelayState = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.ALWAYS,
                isDragging = false,
                activeOneShot = null,
                isMusicPlaying = false,
                isSleepTimeoutReached = true,
            ),
        )
        assertEquals(KemoState.SLEEP, afterDelayState)
    }

    @Test
    fun modeOff_returnsNothing() {
        // Mode OFF always returns null (nothing composed or rendered)
        val state = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.OFF,
                isDragging = false,
                activeOneShot = null,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
        assertNull(state)

        // Even if dragged while off: still nothing
        val stateDragged = nextKemoState(
            KemoStateInputs(
                mode = KemoDisplayMode.OFF,
                isDragging = true,
                activeOneShot = null,
                isMusicPlaying = true,
                isSleepTimeoutReached = false,
            ),
        )
        assertNull(stateDragged)
    }
}
