/*
 * Ozone Player (2026)
 * Unit tests for LyricsCandidatesManager
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.lyrics

import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LyricsCandidatesManagerTest {

    @Before
    @After
    fun cleanUp() {
        LyricsCandidatesManager.clearCache()
    }

    @Test
    fun resolveProvider_returnsValidInstanceForAllProviders() {
        for (provider in PreferredLyricsProvider.entries) {
            val impl = LyricsCandidatesManager.resolveProvider(provider)
            assertNotNull("Provider $provider resolved to null", impl)
            assertEquals(provider.name, provider.name)
        }
    }

    @Test
    fun cache_updatesAndRetrievesCorrectly() {
        val songId = "test_song_123"
        assertNull(LyricsCandidatesManager.getCached(songId))

        val state = CandidateState(
            isChecking = false,
            lyrics = "[00:01.00] Hello world",
            syncKind = LyricsSyncKind.LINE,
        )

        LyricsCandidatesManager.updateCache(songId, PreferredLyricsProvider.LRCLIB, state)

        val cached = LyricsCandidatesManager.getCached(songId)
        assertNotNull(cached)
        assertEquals(state, cached?.get(PreferredLyricsProvider.LRCLIB))

        LyricsCandidatesManager.clearCache()
        assertNull(LyricsCandidatesManager.getCached(songId))
    }

    @Test
    fun candidateState_defaults_areCheckingTrue() {
        val defaultState = CandidateState()
        assertEquals(true, defaultState.isChecking)
        assertNull(defaultState.lyrics)
        assertEquals(LyricsSyncKind.NONE, defaultState.syncKind)
    }
}
