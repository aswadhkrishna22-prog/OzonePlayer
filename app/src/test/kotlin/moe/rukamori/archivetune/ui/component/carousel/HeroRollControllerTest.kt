/*
 * Ozone Player (2026) | Adapted from YumaPlayer
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.component.carousel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import moe.rukamori.archivetune.ui.haptics.NoOpYumaHaptics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeroRollControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun sampleItems(count: Int): List<HeroItem> =
        List(count) { i ->
            HeroItem(
                id = "track_$i",
                title = "Title $i",
                artist = "Artist $i",
                thumbnailUrl = "https://example.com/$i.jpg",
            )
        }

    @Test
    fun `syncCandidates populates candidates from items`() {
        val state = HeroRollState()
        val controller = HeroRollController(state, NoOpYumaHaptics, testScope)
        val items = sampleItems(5)

        controller.syncCandidates(items)

        assertEquals(5, state.candidates.size)
        assertTrue(items.map { it.id }.containsAll(state.candidates.map { it.id }))
    }

    @Test
    fun `syncCandidates resets stale result when item is removed`() {
        val state = HeroRollState()
        val controller = HeroRollController(state, NoOpYumaHaptics, testScope)
        val items = sampleItems(5)

        controller.syncCandidates(items)
        state.result = items[0]
        state.phase = HeroRollPhase.Landed

        // Now sync with list that does not contain items[0]
        val updatedItems = items.drop(1)
        controller.syncCandidates(updatedItems)

        assertNull(state.result)
        assertEquals(HeroRollPhase.Idle, state.phase)
    }

    @Test
    fun `cancelRoll resets phase to Idle`() {
        val state = HeroRollState()
        val controller = HeroRollController(state, NoOpYumaHaptics, testScope)

        state.phase = HeroRollPhase.Rolling
        controller.cancelRoll()

        assertEquals(HeroRollPhase.Idle, state.phase)
    }

    @Test
    fun `onActivityChanged cancels roll when carousel becomes inactive`() {
        val state = HeroRollState()
        val controller = HeroRollController(state, NoOpYumaHaptics, testScope)

        state.phase = HeroRollPhase.Rolling
        controller.onActivityChanged(isCarouselActive = false, animationsDisabled = false)

        assertEquals(HeroRollPhase.Idle, state.phase)
    }

    @Test
    fun `roll with animationsDisabled immediately resolves winner and increments token`() {
        val state = HeroRollState()
        val controller = HeroRollController(state, NoOpYumaHaptics, testScope)
        val items = sampleItems(3)
        controller.syncCandidates(items)

        val initialToken = state.completionToken
        controller.roll(isCarouselActive = true, animationsDisabled = true)

        assertEquals(HeroRollPhase.Landed, state.phase)
        assertNotNull(state.result)
        assertTrue(items.any { it.id == state.result?.id })
        assertEquals(initialToken + 1, state.completionToken)
    }

    @Test
    fun `roll does nothing when pool has fewer than 2 candidates`() {
        val state = HeroRollState()
        val controller = HeroRollController(state, NoOpYumaHaptics, testScope)
        val items = sampleItems(1)
        controller.syncCandidates(items)

        controller.roll(isCarouselActive = true, animationsDisabled = true)

        assertEquals(HeroRollPhase.Idle, state.phase)
        assertNull(state.result)
    }
}
