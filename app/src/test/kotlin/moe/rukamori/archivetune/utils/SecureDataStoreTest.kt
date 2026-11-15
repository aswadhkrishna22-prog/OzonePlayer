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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

class FakePreferencesDataStore(initialPreferences: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initialPreferences)
    override val data: Flow<Preferences> = state.asStateFlow()

    @Volatile var failOnUpdate: Throwable? = null
    @Volatile var updateCount: Int = 0

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        failOnUpdate?.let { throw it }
        val updated = transform(state.value)
        state.value = updated
        updateCount++
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
    fun testTransientMigrationFailure_followedBySuccessfulRetry() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        normalStore.edit { prefs ->
            prefs[themeColorKey] = "ED5564"
            prefs[spotifyDcKey] = "transient_secret"
            prefs[discordTokenKey] = "transient_discord"
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)

        // Inject transient I/O failure on normalStore update (e.g. during delete phase)
        normalStore.failOnUpdate = IOException("Disk I/O failure during migration")

        try {
            compositeStore.runMigration()
            fail("runMigration() must propagate I/O failure and NOT swallow it")
        } catch (e: IOException) {
            assertEquals("Disk I/O failure during migration", e.message)
        }

        // Must NOT mark migrationDone after failure
        assertFalse("Migration must NOT be marked done after failure", compositeStore.isMigrationDone)

        // Normal store must still retain credentials so data is never lost prematurely
        val normalDataAfterFail = normalStore.data.first()
        assertEquals("transient_secret", normalDataAfterFail[spotifyDcKey])
        assertEquals("transient_discord", normalDataAfterFail[discordTokenKey])

        // I/O recovers
        normalStore.failOnUpdate = null

        // Subsequent retry must succeed
        compositeStore.runMigration()
        assertTrue("Migration must be marked done after successful retry", compositeStore.isMigrationDone)

        val normalDataFinal = normalStore.data.first()
        assertNull("Sensitive key must be purged from normalStore after retry", normalDataFinal[spotifyDcKey])
        assertNull("Sensitive key must be purged from normalStore after retry", normalDataFinal[discordTokenKey])
        assertEquals("ED5564", normalDataFinal[themeColorKey])

        val secureDataFinal = secureStore.data.first()
        assertEquals("transient_secret", secureDataFinal[spotifyDcKey])
        assertEquals("transient_discord", secureDataFinal[discordTokenKey])

        val compositeFinal = compositeStore.data.first()
        assertEquals("ED5564", compositeFinal[themeColorKey])
        assertEquals("transient_secret", compositeFinal[spotifyDcKey])
        assertEquals("transient_discord", compositeFinal[discordTokenKey])
    }

    @Test
    fun testFailureAfterSecureWriteBeforeNormalDeletion_subsequentLaunchSafelyCompletes() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        // Simulate crash right after secureStore write succeeded, but before normalStore deletion
        normalStore.edit { prefs ->
            prefs[themeColorKey] = "00AAFF"
            prefs[spotifyDcKey] = "persisted_secret_token"
            prefs[discordTokenKey] = "discord_token_abc"
        }
        secureStore.edit { prefs ->
            prefs[spotifyDcKey] = "persisted_secret_token"
            prefs[discordTokenKey] = "discord_token_abc"
        }

        // New launch starts
        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)
        assertFalse(compositeStore.isMigrationDone)

        compositeStore.runMigration()
        assertTrue(compositeStore.isMigrationDone)

        // Normal store has cleaned up duplicate sensitive keys without touching UI preferences
        val normalData = normalStore.data.first()
        assertEquals("00AAFF", normalData[themeColorKey])
        assertNull(normalData[spotifyDcKey])
        assertNull(normalData[discordTokenKey])

        // Secure store retains intact credentials
        val secureData = secureStore.data.first()
        assertEquals("persisted_secret_token", secureData[spotifyDcKey])
        assertEquals("discord_token_abc", secureData[discordTokenKey])
        assertNull(secureData[themeColorKey])

        // Merged output is pristine
        val compositeData = compositeStore.data.first()
        assertEquals("00AAFF", compositeData[themeColorKey])
        assertEquals("persisted_secret_token", compositeData[spotifyDcKey])
        assertEquals("discord_token_abc", compositeData[discordTokenKey])
    }

    @Test
    fun testMigrationConflict_divergentValuesPreservesBothWithoutSilentOverwrite() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        // Setup a genuine conflict: normalStore has a legacy token, secureStore has a divergent token
        normalStore.edit { prefs ->
            prefs[themeColorKey] = "00FF88"
            prefs[spotifyDcKey] = "legacy_spotify_dc_value"
            prefs[discordTokenKey] = "identical_discord_token"
        }
        secureStore.edit { prefs ->
            prefs[spotifyDcKey] = "secure_spotify_dc_value"
            prefs[discordTokenKey] = "identical_discord_token"
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)
        compositeStore.runMigration()
        assertTrue(compositeStore.isMigrationDone)

        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()
        val compositeData = compositeStore.data.first()

        // 1. Normal store must have ALL sensitive keys purged (safe for backup)
        assertNull("spotify_sp_dc must be purged from normalStore", normalData[spotifyDcKey])
        assertNull("discordToken must be purged from normalStore", normalData[discordTokenKey])
        assertEquals("00FF88", normalData[themeColorKey])

        // 2. Conflict policy:
        // - Primary active key in secureStore retains secureStore's value (no silent overwrite)
        assertEquals("secure_spotify_dc_value", secureData[spotifyDcKey])
        assertEquals("secure_spotify_dc_value", compositeData[spotifyDcKey])

        // - Divergent legacy value is archived under typed conflict key in secureStore (zero data loss)
        val conflictKey = stringPreferencesKey("spotify_sp_dc__conflict_legacy")
        assertEquals("legacy_spotify_dc_value", secureData[conflictKey])
        assertEquals("legacy_spotify_dc_value", compositeData[conflictKey])

        // - Identical value (discordToken) had no conflict, so no conflict key created
        val discordConflictKey = stringPreferencesKey("discordToken__conflict_legacy")
        assertNull(secureData[discordConflictKey])
        assertEquals("identical_discord_token", secureData[discordTokenKey])

        // 3. Idempotency check: Re-running migration produces identical state without duplicates or overwrites
        compositeStore.runMigration()
        val secureDataRerun = secureStore.data.first()
        assertEquals("secure_spotify_dc_value", secureDataRerun[spotifyDcKey])
        assertEquals("legacy_spotify_dc_value", secureDataRerun[conflictKey])
    }

    @Test
    fun testPublicDataFlow_doesNotExposePreferencesUntilMigrationSucceeds() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        normalStore.edit { prefs ->
            prefs[themeColorKey] = "FFAA00"
            prefs[spotifyDcKey] = "sensitive_cookie_99"
        }

        // Fail secureStore update so migration cannot complete
        secureStore.failOnUpdate = IOException("Secure storage unavailable")

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)

        var collected = false
        try {
            compositeStore.data.first()
            collected = true
            fail("Data flow must not emit preferences before migration completes successfully")
        } catch (e: IOException) {
            assertEquals("Secure storage unavailable", e.message)
        }
        assertFalse(collected)
        assertFalse(compositeStore.isMigrationDone)

        // Recover secure store
        secureStore.failOnUpdate = null

        // Now collecting data flow succeeds and emits migrated preferences
        val emitted = compositeStore.data.first()
        assertEquals("FFAA00", emitted[themeColorKey])
        assertEquals("sensitive_cookie_99", emitted[spotifyDcKey])
        assertTrue(compositeStore.isMigrationDone)
    }

    @Test
    fun testRepeatedMigration_idempotentWithoutLossOrDuplicates() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        normalStore.edit { prefs ->
            prefs[themeColorKey] = "AABBCC"
            prefs[discordTokenKey] = "discord_123"
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)

        repeat(5) {
            compositeStore.runMigration()
            assertTrue(compositeStore.isMigrationDone)
        }

        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()
        val compositeData = compositeStore.data.first()

        assertEquals("AABBCC", normalData[themeColorKey])
        assertNull(normalData[discordTokenKey])

        assertEquals("discord_123", secureData[discordTokenKey])
        assertNull(secureData[themeColorKey])

        assertEquals("AABBCC", compositeData[themeColorKey])
        assertEquals("discord_123", compositeData[discordTokenKey])
    }

    @Test
    fun testConcurrentUpdates_threadSafeRouting() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()
        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)

        val jobCount = 10
        val iterationsPerJob = 5

        coroutineScope {
            for (jobId in 0 until jobCount) {
                launch(Dispatchers.Default) {
                    for (iter in 0 until iterationsPerJob) {
                        val isSensitive = (jobId % 2 == 0)
                        if (isSensitive) {
                            val key = stringPreferencesKey("spotify_access_token")
                            compositeStore.edit { prefs ->
                                prefs[key] = "token_${jobId}_$iter"
                            }
                        } else {
                            val key = stringPreferencesKey("customThemeColor")
                            compositeStore.edit { prefs ->
                                prefs[key] = "color_${jobId}_$iter"
                            }
                        }
                    }
                }
            }
        }

        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()

        assertNull("Normal store must NOT contain sensitive key", normalData[stringPreferencesKey("spotify_access_token")])
        assertNotNull("Normal store must contain non-sensitive UI key", normalData[stringPreferencesKey("customThemeColor")])

        assertNull("Secure store must NOT contain UI key", secureData[stringPreferencesKey("customThemeColor")])
        assertNotNull("Secure store must contain sensitive token", secureData[stringPreferencesKey("spotify_access_token")])
    }

    @Test
    fun testEveryKeyInSensitivePreferences_properlyPartitionedAndPurged() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        val allKeys = SensitivePreferences.KEYS + setOf("flac_kv_custom_dynamic_key")

        // 1. Populate normalStore with every sensitive key plus UI preference (legacy state)
        normalStore.edit { prefs ->
            prefs[themeColorKey] = "ORIGINAL_THEME"
            for (keyName in allKeys) {
                val key = stringPreferencesKey(keyName)
                prefs[key] = "legacy_val_$keyName"
            }
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)
        compositeStore.runMigration()
        assertTrue(compositeStore.isMigrationDone)

        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()
        val compositeData = compositeStore.data.first()

        assertEquals("ORIGINAL_THEME", normalData[themeColorKey])

        for (keyName in allKeys) {
            val key = stringPreferencesKey(keyName)
            assertNull(
                "Key '$keyName' must be completely purged from normalStore",
                normalData[key]
            )
            assertEquals(
                "Key '$keyName' must be safely present in secureStore",
                "legacy_val_$keyName",
                secureData[key]
            )
            assertEquals(
                "Key '$keyName' must be accessible through compositeStore",
                "legacy_val_$keyName",
                compositeData[key]
            )
        }

        // 2. Direct write of each key through compositeStore.edit
        compositeStore.edit { prefs ->
            for (keyName in allKeys) {
                val key = stringPreferencesKey(keyName)
                prefs[key] = "fresh_val_$keyName"
            }
        }

        val normalDataFresh = normalStore.data.first()
        val secureDataFresh = secureStore.data.first()
        for (keyName in allKeys) {
            val key = stringPreferencesKey(keyName)
            assertNull(
                "Fresh write of '$keyName' must NOT touch normalStore",
                normalDataFresh[key]
            )
            assertEquals(
                "Fresh write of '$keyName' must land in secureStore",
                "fresh_val_$keyName",
                secureDataFresh[key]
            )
        }
    }

    private fun resolveResFile(path: String): File {
        val direct = File(path)
        if (direct.exists()) return direct
        val withApp = File("app", path)
        if (withApp.exists()) return withApp
        val stripped = path.removePrefix("app/")
        val strippedFile = File(stripped)
        if (strippedFile.exists()) return strippedFile
        return direct
    }

    @Test
    fun testBackupAndDataExtractionRules_onlyExcludeSecureSettings() {
        val dataExtractionFile = resolveResFile("src/main/res/xml/data_extraction_rules.xml")
        val backupRulesFile = resolveResFile("src/main/res/xml/backup_rules.xml")

        assertTrue("data_extraction_rules.xml must exist: ${dataExtractionFile.absolutePath}", dataExtractionFile.exists())
        assertTrue("backup_rules.xml must exist: ${backupRulesFile.absolutePath}", backupRulesFile.exists())

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

    @Test
    fun testOzoneBackupAgent_manifestRegistration() {
        val manifestFile = resolveResFile("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist: ${manifestFile.absolutePath}", manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val applicationNode = doc.getElementsByTagName("application").item(0)
        assertNotNull("<application> node must exist in AndroidManifest.xml", applicationNode)

        val backupAgentAttr = applicationNode.attributes.getNamedItem("android:backupAgent")
        assertNotNull("android:backupAgent must be declared", backupAgentAttr)
        assertEquals(".utils.OzoneBackupAgent", backupAgentAttr.nodeValue)

        val allowBackupAttr = applicationNode.attributes.getNamedItem("android:allowBackup")
        assertNotNull("android:allowBackup must be declared", allowBackupAttr)
        assertEquals("true", allowBackupAttr.nodeValue)
    }
}
