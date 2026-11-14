/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FakePreferencesDataStore(initialPreferences: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initialPreferences)
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class SecureDataStoreTest {

    private val themeColorKey = stringPreferencesKey("customThemeColor")
    private val fontPrefKey = stringPreferencesKey("fontPreference")
    private val disableBlurKey = booleanPreferencesKey("disableBlur")

    private val spotifyDcKey = stringPreferencesKey("spotify_sp_dc")
    private val discordTokenKey = stringPreferencesKey("discordToken")
    private val innerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
    private val qobuzAuthTokenKey = stringPreferencesKey("qobuz_user_auth_token")
    private val aiApiKey = stringPreferencesKey("ai_api_key")

    @Test
    fun testSensitiveKeysSeparation_routesSensitiveToSecureStoreAndNormalToNormalStore() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()
        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)

        // 1. Write non-sensitive UI preferences and sensitive credentials in one edit
        compositeStore.edit { prefs ->
            prefs[themeColorKey] = "9CCFFF"
            prefs[fontPrefKey] = "INTER"
            prefs[disableBlurKey] = true

            prefs[spotifyDcKey] = "secret_sp_dc_12345"
            prefs[discordTokenKey] = "secret_discord_token_xyz"
            prefs[innerTubeCookieKey] = "SAPISID=abc123"
            prefs[qobuzAuthTokenKey] = "qobuz_token_999"
            prefs[aiApiKey] = "ai_key_secret"
        }

        // 2. Verify normalStore has UI preferences and ZERO sensitive keys
        val normalData = normalStore.data.first()
        assertEquals("9CCFFF", normalData[themeColorKey])
        assertEquals("INTER", normalData[fontPrefKey])
        assertEquals(true, normalData[disableBlurKey])

        assertNull("Normal store must NOT contain spotify_sp_dc", normalData[spotifyDcKey])
        assertNull("Normal store must NOT contain discordToken", normalData[discordTokenKey])
        assertNull("Normal store must NOT contain innerTubeCookie", normalData[innerTubeCookieKey])
        assertNull("Normal store must NOT contain qobuz_user_auth_token", normalData[qobuzAuthTokenKey])
        assertNull("Normal store must NOT contain ai_api_key", normalData[aiApiKey])

        // 3. Verify secureStore has sensitive credentials and ZERO non-sensitive keys
        val secureData = secureStore.data.first()
        assertEquals("secret_sp_dc_12345", secureData[spotifyDcKey])
        assertEquals("secret_discord_token_xyz", secureData[discordTokenKey])
        assertEquals("SAPISID=abc123", secureData[innerTubeCookieKey])
        assertEquals("qobuz_token_999", secureData[qobuzAuthTokenKey])
        assertEquals("ai_key_secret", secureData[aiApiKey])

        assertNull("Secure store must NOT contain themeColor", secureData[themeColorKey])
        assertNull("Secure store must NOT contain fontPref", secureData[fontPrefKey])
        assertNull("Secure store must NOT contain disableBlur", secureData[disableBlurKey])

        // 4. Verify compositeStore seamlessly reads both
        val compositeData = compositeStore.data.first()
        assertEquals("9CCFFF", compositeData[themeColorKey])
        assertEquals("secret_sp_dc_12345", compositeData[spotifyDcKey])
        assertEquals("secret_discord_token_xyz", compositeData[discordTokenKey])
    }

    @Test
    fun testLegacyMigration_movesSensitiveKeysToSecureStoreAndPreservesUiPreferences() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        // Seed normalStore simulating existing installation prior to this architecture
        normalStore.edit { prefs ->
            prefs[themeColorKey] = "ED5564"
            prefs[fontPrefKey] = "DEFAULT"
            prefs[spotifyDcKey] = "legacy_spotify_cookie"
            prefs[discordTokenKey] = "legacy_discord_token"
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)
        compositeStore.runMigration()

        // Verify normalStore preserves UI preferences
        val normalData = normalStore.data.first()
        assertEquals("ED5564", normalData[themeColorKey])
        assertEquals("DEFAULT", normalData[fontPrefKey])

        // Verify normalStore purged sensitive keys
        assertNull("Legacy spotify_sp_dc must be purged from normalStore", normalData[spotifyDcKey])
        assertNull("Legacy discordToken must be purged from normalStore", normalData[discordTokenKey])

        // Verify secureStore now holds the migrated sensitive keys
        val secureData = secureStore.data.first()
        assertEquals("legacy_spotify_cookie", secureData[spotifyDcKey])
        assertEquals("legacy_discord_token", secureData[discordTokenKey])

        // Verify compositeStore still seamlessly serves all keys to the app
        val compositeData = compositeStore.data.first()
        assertEquals("ED5564", compositeData[themeColorKey])
        assertEquals("legacy_spotify_cookie", compositeData[spotifyDcKey])
    }

    @Test
    fun testBackupAndDataExtractionRules_onlyExcludeSecureSettings() {
        val dataExtractionFile = File("app/src/main/res/xml/data_extraction_rules.xml")
        val backupRulesFile = File("app/src/main/res/xml/backup_rules.xml")

        assertTrue("data_extraction_rules.xml must exist", dataExtractionFile.exists())
        assertTrue("backup_rules.xml must exist", backupRulesFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        // 1. Verify data_extraction_rules.xml
        val deDoc = builder.parse(dataExtractionFile)
        val cloudBackupExcludes = mutableListOf<String>()
        val deviceTransferExcludes = mutableListOf<String>()

        val cloudNode = deDoc.getElementsByTagName("cloud-backup").item(0)
        val cloudChildNodes = cloudNode.childNodes
        for (i in 0 until cloudChildNodes.length) {
            val node = cloudChildNodes.item(i)
            if (node.nodeName == "exclude") {
                cloudBackupExcludes.add(node.attributes.getNamedItem("path").nodeValue)
            }
        }

        val deviceNode = deDoc.getElementsByTagName("device-transfer").item(0)
        val deviceChildNodes = deviceNode.childNodes
        for (i in 0 until deviceChildNodes.length) {
            val node = deviceChildNodes.item(i)
            if (node.nodeName == "exclude") {
                deviceTransferExcludes.add(node.attributes.getNamedItem("path").nodeValue)
            }
        }

        assertTrue(
            "cloud-backup must exclude datastore/secure_settings.preferences_pb",
            cloudBackupExcludes.contains("datastore/secure_settings.preferences_pb")
        )
        assertFalse(
            "cloud-backup must NOT exclude datastore/settings.preferences_pb (preserves UI prefs)",
            cloudBackupExcludes.contains("datastore/settings.preferences_pb")
        )

        assertTrue(
            "device-transfer must exclude datastore/secure_settings.preferences_pb",
            deviceTransferExcludes.contains("datastore/secure_settings.preferences_pb")
        )
        assertFalse(
            "device-transfer must NOT exclude datastore/settings.preferences_pb (preserves UI prefs)",
            deviceTransferExcludes.contains("datastore/settings.preferences_pb")
        )

        // 2. Verify backup_rules.xml
        val brDoc = builder.parse(backupRulesFile)
        val fullBackupExcludes = mutableListOf<String>()
        val fullBackupNode = brDoc.getElementsByTagName("full-backup-content").item(0)
        val fbChildNodes = fullBackupNode.childNodes
        for (i in 0 until fbChildNodes.length) {
            val node = fbChildNodes.item(i)
            if (node.nodeName == "exclude") {
                fullBackupExcludes.add(node.attributes.getNamedItem("path").nodeValue)
            }
        }

        assertTrue(
            "full-backup-content must exclude datastore/secure_settings.preferences_pb",
            fullBackupExcludes.contains("datastore/secure_settings.preferences_pb")
        )
        assertFalse(
            "full-backup-content must NOT exclude datastore/settings.preferences_pb",
            fullBackupExcludes.contains("datastore/settings.preferences_pb")
        )
    }
}
