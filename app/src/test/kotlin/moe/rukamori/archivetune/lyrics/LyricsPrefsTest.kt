package moe.rukamori.archivetune.lyrics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKannadaKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeMalayalamKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsRomanizeTamilKey
import moe.rukamori.archivetune.constants.LyricsRomanizeTeluguKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LyricsPrefsTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testFile: File

    @Before
    fun setup() {
        testFile = File.createTempFile("test_datastore", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )
    }

    @Test
    fun testLyricsRomanizationPreferences_defaultsToTrue() = runBlocking {
        val flow = dataStore.data.map { preferences ->
            LyricsRomanizationPreferences(
                romanizeJapanese = preferences[LyricsRomanizeJapaneseKey] ?: true,
                romanizeKorean = preferences[LyricsRomanizeKoreanKey] ?: true,
                romanizeChinese = preferences[LyricsRomanizeChineseKey] ?: true,
                romanizeHindi = preferences[LyricsRomanizeHindiKey] ?: true,
                romanizeMalayalam = preferences[LyricsRomanizeMalayalamKey] ?: true,
                romanizeTamil = preferences[LyricsRomanizeTamilKey] ?: true,
                romanizeTelugu = preferences[LyricsRomanizeTeluguKey] ?: true,
                romanizeKannada = preferences[LyricsRomanizeKannadaKey] ?: true,
                romanizeOther = preferences[LyricsRomanizeOtherLanguagesKey] ?: true
            )
        }

        val prefs = flow.first()
        assertTrue(prefs.romanizeJapanese)
        assertTrue(prefs.romanizeKorean)
        assertTrue(prefs.romanizeChinese)
        assertTrue(prefs.romanizeHindi)
        assertTrue(prefs.romanizeMalayalam)
        assertTrue(prefs.romanizeTamil)
        assertTrue(prefs.romanizeTelugu)
        assertTrue(prefs.romanizeKannada)
        assertTrue(prefs.romanizeOther)
        assertTrue(prefs.isEnabled)
    }

    @Test
    fun testLyricsRomanizationPreferences_emitsUpdatedValue() = runBlocking {
        val flow = dataStore.data.map { preferences ->
            LyricsRomanizationPreferences(
                romanizeJapanese = preferences[LyricsRomanizeJapaneseKey] ?: true,
                romanizeKorean = preferences[LyricsRomanizeKoreanKey] ?: true,
                romanizeChinese = preferences[LyricsRomanizeChineseKey] ?: true,
                romanizeHindi = preferences[LyricsRomanizeHindiKey] ?: true,
                romanizeMalayalam = preferences[LyricsRomanizeMalayalamKey] ?: true,
                romanizeTamil = preferences[LyricsRomanizeTamilKey] ?: true,
                romanizeTelugu = preferences[LyricsRomanizeTeluguKey] ?: true,
                romanizeKannada = preferences[LyricsRomanizeKannadaKey] ?: true,
                romanizeOther = preferences[LyricsRomanizeOtherLanguagesKey] ?: true
            )
        }

        dataStore.edit { preferences ->
            preferences[LyricsRomanizeJapaneseKey] = false
            preferences[LyricsRomanizeKoreanKey] = false
            preferences[LyricsRomanizeMalayalamKey] = false
        }

        val prefs = flow.first()
        assertFalse(prefs.romanizeJapanese)
        assertFalse(prefs.romanizeKorean)
        assertTrue(prefs.romanizeChinese)
        assertTrue(prefs.romanizeHindi)
        assertFalse(prefs.romanizeMalayalam)
        assertTrue(prefs.romanizeTamil)
        assertTrue(prefs.romanizeTelugu)
        assertTrue(prefs.romanizeKannada)
        assertTrue(prefs.romanizeOther)
        assertTrue(prefs.isEnabled)
    }
}