/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import moe.rukamori.archivetune.viewmodels.OnlineSearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSlideAnimationTest {

    @Test
    fun testAnimationConstantsMatchSpecification() {
        assertEquals(8, SEARCH_SLIDE_MAX_ANIMATED_ROWS)
        assertEquals(45L, SEARCH_SLIDE_ROW_STAGGER_MS)
        assertEquals(450, SEARCH_SLIDE_DURATION_MS)
        assertEquals(270, SEARCH_SLIDE_ALPHA_DURATION_MS)
        assertEquals(0.5f, SEARCH_SLIDE_START_OFFSET_RATIO, 0.001f)
    }

    @Test
    fun testSearchSlideTrigger_triggersOnSourceChange() {
        assertTrue(SearchSlideTrigger.shouldTriggerOnSourceChange(OnlineSearchSource.YOUTUBE, OnlineSearchSource.SPOTIFY))
        assertTrue(SearchSlideTrigger.shouldTriggerOnSourceChange(OnlineSearchSource.SPOTIFY, OnlineSearchSource.YOUTUBE))
        assertFalse(SearchSlideTrigger.shouldTriggerOnSourceChange(OnlineSearchSource.YOUTUBE, OnlineSearchSource.YOUTUBE))
        assertFalse(SearchSlideTrigger.shouldTriggerOnSourceChange(OnlineSearchSource.SPOTIFY, OnlineSearchSource.SPOTIFY))
    }

    @Test
    fun testSearchSlideTrigger_doesNotTriggerOnCategoryFilterChange() {
        assertFalse(SearchSlideTrigger.shouldTriggerOnCategoryFilterChange())
    }

    @Test
    fun testRequestIdAndPlayedAnimationKeysLogic() {
        val savedState = SavedStateHandle()
        val query = "Samajavaragamana"
        var counter = 1

        val initialYtKey = "req_${query}_${OnlineSearchSource.YOUTUBE.name}_${counter}_yt_songs"
        val playedKeys = mutableSetOf<String>()

        assertFalse(playedKeys.contains(initialYtKey))
        playedKeys.add(initialYtKey)
        savedState["played_search_slide_keys"] = ArrayList(playedKeys)

        assertTrue(playedKeys.contains(initialYtKey))

        // When switching source chip, counter increments
        if (SearchSlideTrigger.shouldTriggerOnSourceChange(OnlineSearchSource.YOUTUBE, OnlineSearchSource.SPOTIFY)) {
            counter++
        }
        val spotifyKey = "req_${query}_${OnlineSearchSource.SPOTIFY.name}_${counter}_spotify_songs"
        assertFalse(playedKeys.contains(spotifyKey))

        // Category filter change does NOT increment counter
        if (SearchSlideTrigger.shouldTriggerOnCategoryFilterChange()) {
            counter++
        }
        assertEquals(2, counter)

        // Restoration from SavedStateHandle preserves played keys
        val restoredKeys: List<String> = savedState.get<ArrayList<String>>("played_search_slide_keys") ?: emptyList()
        assertTrue(restoredKeys.contains(initialYtKey))
    }
}
