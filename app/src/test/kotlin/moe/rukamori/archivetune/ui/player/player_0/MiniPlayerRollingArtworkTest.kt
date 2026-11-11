package moe.rukamori.archivetune.ui.player.player_0

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class MiniPlayerRollingArtworkTest {

    @Test
    fun constants_enforce830msRollDuration() {
        // Roll duration MUST be exactly 830 ms across mini player and lyrics sheet
        assertEquals(830, ROLLING_ARTWORK_DURATION_MS)
        assertEquals(830, MINI_ROLL_DURATION_MS)
        assertEquals(880, ARTWORK_CROSSFADE_DURATION_MS)
    }

    @Test
    fun rollWithoutSlipping_calculatesCorrectRotation() {
        val radius = 20f

        // 1. Center resting position
        val centerRotation = RollingArtworkMath.calculateRollRotation(0f, radius)
        assertEquals(0f, centerRotation, 0.001f)

        // 2. Rolling half a circle (-pi * radius) should be -180 degrees (counter-clockwise roll left)
        val halfRollLeft = -PI.toFloat() * radius
        val rotLeft = RollingArtworkMath.calculateRollRotation(halfRollLeft, radius)
        assertEquals(-180f, rotLeft, 0.01f)

        // 3. Rolling half a circle (+pi * radius) should be +180 degrees (clockwise roll right)
        val halfRollRight = PI.toFloat() * radius
        val rotRight = RollingArtworkMath.calculateRollRotation(halfRollRight, radius)
        assertEquals(180f, rotRight, 0.01f)

        // 4. Typical roll distance of 50px with radius 20px
        val typicalRoll = RollingArtworkMath.calculateRollRotation(-50f, 20f)
        val expected = (-50f / 20f) * (180f / PI.toFloat())
        assertEquals(expected, typicalRoll, 0.01f)

        // 5. Zero radius edge case
        assertEquals(0f, RollingArtworkMath.calculateRollRotation(10f, 0f), 0.001f)

        // Verify MiniRollMath matches RollingArtworkMath
        assertEquals(rotLeft, MiniRollMath.calculateRollRotation(halfRollLeft, radius), 0.001f)
    }

    @Test
    fun isForwardDirection_decidesFromQueueIndex() {
        // Next song advance
        assertTrue(RollingArtworkMath.isForwardDirection(previousIndex = 0, currentIndex = 1))
        assertTrue(RollingArtworkMath.isForwardDirection(previousIndex = 3, currentIndex = 4))

        // Previous song retreat
        assertFalse(RollingArtworkMath.isForwardDirection(previousIndex = 1, currentIndex = 0))
        assertFalse(RollingArtworkMath.isForwardDirection(previousIndex = 5, currentIndex = 4))

        // Unknown / negative index defaults to forward
        assertTrue(RollingArtworkMath.isForwardDirection(previousIndex = -1, currentIndex = 0))
        assertTrue(RollingArtworkMath.isForwardDirection(previousIndex = 0, currentIndex = -1))
        assertTrue(RollingArtworkMath.isForwardDirection(previousIndex = -1, currentIndex = -1))

        // Same index defaults to forward
        assertTrue(RollingArtworkMath.isForwardDirection(previousIndex = 2, currentIndex = 2))

        // Verify MiniRollMath matches
        assertTrue(MiniRollMath.isForwardDirection(previousIndex = 0, currentIndex = 1))
        assertFalse(MiniRollMath.isForwardDirection(previousIndex = 1, currentIndex = 0))
    }

    @Test
    fun shouldAnimateRoll_enforcesSuppressionRules() {
        // Rule 1: First composition must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = true,
                isVisible = true,
                wasVisible = true,
                queueRestoreCompleted = true,
                animationsDisabled = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // Rule 2: When mini player becomes visible, must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = true,
                wasVisible = false,
                queueRestoreCompleted = true,
                animationsDisabled = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // Rule 3: Hidden mini player must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = false,
                wasVisible = false,
                queueRestoreCompleted = true,
                animationsDisabled = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // Rule 4: On queue restore, must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = true,
                wasVisible = true,
                queueRestoreCompleted = false,
                animationsDisabled = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // Rule 5: When animations are disabled, must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = true,
                wasVisible = true,
                queueRestoreCompleted = true,
                animationsDisabled = true,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // Rule 6: Same song artwork refresh must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = true,
                wasVisible = true,
                queueRestoreCompleted = true,
                animationsDisabled = false,
                previousMediaId = "track_1",
                currentMediaId = "track_1"
            )
        )

        // Rule 7: Blank IDs must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = true,
                wasVisible = true,
                queueRestoreCompleted = true,
                animationsDisabled = false,
                previousMediaId = null,
                currentMediaId = "track_2"
            )
        )
        assertFalse(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = true,
                wasVisible = true,
                queueRestoreCompleted = true,
                animationsDisabled = false,
                previousMediaId = "track_1",
                currentMediaId = ""
            )
        )

        // Happy path: Song change while visible and ready -> MUST ANIMATE!
        assertTrue(
            RollingArtworkMath.shouldAnimateRoll(
                isFirstComposition = false,
                isVisible = true,
                wasVisible = true,
                queueRestoreCompleted = true,
                animationsDisabled = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )
    }

    @Test
    fun lyricsRollSuppression_enforcesDualPlayAndOpenCloseRules() {
        // 1. Opening the lyrics sheet must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateLyricsRoll(
                isFirstComposition = false,
                isSheetVisible = true,
                wasSheetVisible = false,
                isTargetSwitched = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // 2. Closing the lyrics sheet must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateLyricsRoll(
                isFirstComposition = false,
                isSheetVisible = false,
                wasSheetVisible = true,
                isTargetSwitched = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // 3. First composition of lyrics header must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateLyricsRoll(
                isFirstComposition = true,
                isSheetVisible = true,
                wasSheetVisible = true,
                isTargetSwitched = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // 4. Switching Dual Play sheet target (Player A -> Player B) must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateLyricsRoll(
                isFirstComposition = false,
                isSheetVisible = true,
                wasSheetVisible = true,
                isTargetSwitched = true,
                previousMediaId = "player_a_track",
                currentMediaId = "player_b_track"
            )
        )

        // 5. Switching Dual Play sheet target (Player B -> Player A) must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateLyricsRoll(
                isFirstComposition = false,
                isSheetVisible = true,
                wasSheetVisible = true,
                isTargetSwitched = true,
                previousMediaId = "player_b_track",
                currentMediaId = "player_a_track"
            )
        )

        // 6. When sheet is open and target stays unchanged, song advance MUST ANIMATE!
        assertTrue(
            RollingArtworkMath.shouldAnimateLyricsRoll(
                isFirstComposition = false,
                isSheetVisible = true,
                wasSheetVisible = true,
                isTargetSwitched = false,
                previousMediaId = "track_1",
                currentMediaId = "track_2"
            )
        )

        // 7. Same song refresh in lyrics sheet must NOT animate
        assertFalse(
            RollingArtworkMath.shouldAnimateLyricsRoll(
                isFirstComposition = false,
                isSheetVisible = true,
                wasSheetVisible = true,
                isTargetSwitched = false,
                previousMediaId = "track_1",
                currentMediaId = "track_1"
            )
        )
    }

    @Test
    fun rollingArtworkTarget_keyedByMediaIdOnly() {
        val targetA1 = RollingArtworkTarget(mediaId = "song_1", model = "https://thumb/1.jpg", placeholderResId = 100)
        val targetA2 = RollingArtworkTarget(mediaId = "song_1", model = "https://hd/1.jpg", placeholderResId = 100)
        val targetB = RollingArtworkTarget(mediaId = "song_2", model = "https://thumb/2.jpg", placeholderResId = 100)

        // Same mediaId with different coverUrl are EQUAL (prevents roll on cover refresh)
        assertEquals(targetA1, targetA2)
        assertEquals(targetA1.hashCode(), targetA2.hashCode())

        // Different mediaId are NOT EQUAL (triggers roll on song change)
        assertNotEquals(targetA1, targetB)
    }
}
