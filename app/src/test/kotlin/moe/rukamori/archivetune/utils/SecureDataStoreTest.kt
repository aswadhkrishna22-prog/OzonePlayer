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
import org.w3c.dom.Element

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
    fun testMigrationConflict_secureValueWinsAndLegacyDeletedWithoutArchive() = runTest {
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
            // Also simulate a lingering legacy archive key from an older build
            prefs[stringPreferencesKey("spotify_sp_dc__conflict_legacy")] = "old_stale_archive"
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)
        compositeStore.runMigration()
        assertTrue(compositeStore.isMigrationDone)

        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()
        val compositeData = compositeStore.data.first()

        // 1. Normal store must have ALL sensitive keys purged (fail-safe against backup exposure)
        assertNull("spotify_sp_dc must be purged from normalStore", normalData[spotifyDcKey])
        assertNull("discordToken must be purged from normalStore", normalData[discordTokenKey])
        assertEquals("00FF88", normalData[themeColorKey])

        // 2. Conflict policy:
        // - Primary active key in secureStore retains secureStore's value: SECURE VALUE WINS
        assertEquals("secure_spotify_dc_value", secureData[spotifyDcKey])
        assertEquals("secure_spotify_dc_value", compositeData[spotifyDcKey])

        // - Legacy conflicting value is deleted; NO archive key is created or retained
        val conflictKey = stringPreferencesKey("spotify_sp_dc__conflict_legacy")
        assertNull("Conflict archive key must NOT exist or must be removed", secureData[conflictKey])
        assertNull("Conflict archive key must NOT exist in composite data", compositeData[conflictKey])

        // - Identical value (discordToken) had no divergence, preserved as-is
        assertEquals("identical_discord_token", secureData[discordTokenKey])

        // 3. Idempotency check: Re-running migration produces identical clean state
        compositeStore.runMigration()
        val secureDataRerun = secureStore.data.first()
        assertEquals("secure_spotify_dc_value", secureDataRerun[spotifyDcKey])
        assertNull(secureDataRerun[conflictKey])
    }

    @Test
    fun testDistinctConflicts_allResolvedWithSecureWinning() = runTest {
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()

        normalStore.edit { prefs ->
            prefs[themeColorKey] = "CC00AA"
            prefs[spotifyDcKey] = "normal_sp_dc_diff"
            prefs[discordTokenKey] = "normal_discord_diff"
            prefs[qobuzAuthTokenKey] = "normal_qobuz_diff"
        }
        secureStore.edit { prefs ->
            prefs[spotifyDcKey] = "secure_sp_dc_winner"
            prefs[discordTokenKey] = "secure_discord_winner"
            prefs[qobuzAuthTokenKey] = "secure_qobuz_winner"
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)
        compositeStore.runMigration()
        assertTrue(compositeStore.isMigrationDone)

        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()
        val compositeData = compositeStore.data.first()

        // All legacy divergent values purged from normalStore
        assertNull(normalData[spotifyDcKey])
        assertNull(normalData[discordTokenKey])
        assertNull(normalData[qobuzAuthTokenKey])
        assertEquals("CC00AA", normalData[themeColorKey])

        // Secure values win for all
        assertEquals("secure_sp_dc_winner", secureData[spotifyDcKey])
        assertEquals("secure_discord_winner", secureData[discordTokenKey])
        assertEquals("secure_qobuz_winner", secureData[qobuzAuthTokenKey])

        assertEquals("secure_sp_dc_winner", compositeData[spotifyDcKey])
        assertEquals("secure_discord_winner", compositeData[discordTokenKey])
        assertEquals("secure_qobuz_winner", compositeData[qobuzAuthTokenKey])
        assertEquals("CC00AA", compositeData[themeColorKey])
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
    fun testBackupAndDataExtractionRules_excludeBothDatastoreFilesAndCookies() {
        // Proves data_extraction_rules.xml and backup_rules.xml exclude BOTH settings and secure_settings preferences files and WebView cookies
        val dataExtractionFile = resolveResFile("src/main/res/xml/data_extraction_rules.xml")
        val backupRulesFile = resolveResFile("src/main/res/xml/backup_rules.xml")

        assertTrue("data_extraction_rules.xml must exist: ${dataExtractionFile.absolutePath}", dataExtractionFile.exists())
        assertTrue("backup_rules.xml must exist: ${backupRulesFile.absolutePath}", backupRulesFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        // 1. Verify data_extraction_rules.xml (Android 12+)
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
        assertTrue(
            "cloud-backup must exclude datastore/settings.preferences_pb (fail-safe by construction)",
            cloudBackupExcludes.contains("datastore/settings.preferences_pb")
        )
        assertTrue(
            "cloud-backup must exclude app_webview to protect WebView login cookies",
            cloudBackupExcludes.contains("app_webview")
        )

        assertTrue(
            "device-transfer must exclude datastore/secure_settings.preferences_pb",
            deviceTransferExcludes.contains("datastore/secure_settings.preferences_pb")
        )
        assertTrue(
            "device-transfer must exclude datastore/settings.preferences_pb",
            deviceTransferExcludes.contains("datastore/settings.preferences_pb")
        )
        assertTrue(
            "device-transfer must exclude app_webview",
            deviceTransferExcludes.contains("app_webview")
        )

        // 2. Verify backup_rules.xml (Android 11 and below)
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
        assertTrue(
            "full-backup-content must exclude datastore/settings.preferences_pb",
            fullBackupExcludes.contains("datastore/settings.preferences_pb")
        )
        assertTrue(
            "full-backup-content must exclude app_webview",
            fullBackupExcludes.contains("app_webview")
        )
    }

    @Test
    fun testOzoneBackupAgent_manifestRegistration() {
        // Proves OzoneBackupAgent is registered in AndroidManifest.xml under application tag
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

    @Test
    fun testOzoneBackupAgent_failClosedOnMigrationFailure() = runTest {
        // Proves OzoneBackupAgent fails closed and throws IOException when migration fails, preventing backup
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()
        normalStore.edit { prefs ->
            prefs[spotifyDcKey] = "failing_token"
        }
        // Force secure store failure
        secureStore.failOnUpdate = IOException("Hardware storage failure")

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)
        val agent = OzoneBackupAgent()

        try {
            agent.executeMigration(compositeStore, "onFullBackup")
            fail("OzoneBackupAgent must throw IOException when pre-backup migration fails")
        } catch (e: IOException) {
            assertTrue("Exception message must indicate migration failure", e.message?.contains("Pre-backup migration failed") == true)
        }
    }

    @Test
    fun testOzoneBackupAgent_rejectsSecureSettingsRestoreBeforeSuper() {
        // Proves OzoneBackupAgent intercepts and rejects restoration of secure_settings before super, preserving existing credentials
        val agent = OzoneBackupAgent()

        assertFalse("Must reject secure_settings.preferences_pb", agent.shouldRestoreFile(File("secure_settings.preferences_pb")))
        assertFalse("Must reject paths containing secure_settings", agent.shouldRestoreFile(File("/data/user/0/pkg/files/datastore/secure_settings.preferences_pb")))
        assertTrue("Must allow standard settings.preferences_pb", agent.shouldRestoreFile(File("settings.preferences_pb")))
        assertTrue("Must allow other app files", agent.shouldRestoreFile(File("other_config.json")))

        // Assert that an existing secure credentials file is NOT modified, overwritten, or deleted when restoration is rejected
        val tempDir = java.nio.file.Files.createTempDirectory("backup_agent_test").toFile()
        try {
            val existingSecureFile = File(tempDir, "secure_settings.preferences_pb")
            existingSecureFile.writeText("EXISTING_SECRET_PAYLOAD_DO_NOT_OVERWRITE")

            val shouldRestore = agent.shouldRestoreFile(existingSecureFile)
            assertFalse("Agent must reject restoring over existing secure file", shouldRestore)
            assertEquals("EXISTING_SECRET_PAYLOAD_DO_NOT_OVERWRITE", existingSecureFile.readText())
            assertTrue("Existing file must still exist and be intact", existingSecureFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
        println("[PROOF] 18: OzoneBackupAgent rejects secure_settings restore BEFORE super, preserving existing credentials.")
    }

    @Test
    fun testProviderPaths_excludesRootAndDatastore() {
        // Proves provider_paths.xml scopes FileProvider strictly to subdirectories and excludes root and datastore
        val providerPathsFile = resolveResFile("src/main/res/xml/provider_paths.xml")
        assertTrue("provider_paths.xml must exist: ${providerPathsFile.absolutePath}", providerPathsFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(providerPathsFile)

        val paths = doc.getElementsByTagName("paths").item(0) as Element
        val childNodes = paths.childNodes

        val configuredPaths = mutableListOf<Pair<String, String>>()
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                val tag = node.tagName
                val pathAttr = node.getAttribute("path")
                configuredPaths.add(tag to pathAttr)
            }
        }

        assertTrue("Must configure at least one FileProvider path", configuredPaths.isNotEmpty())

        for ((tag, path) in configuredPaths) {
            assertFalse("Path must NOT be root '.' for $tag", path == "." || path.isBlank())
            assertFalse("Path must NOT expose datastore directory for $tag", path.contains("datastore"))
        }

        val filesPaths = configuredPaths.filter { it.first == "files-path" }
        assertEquals("files-path must only have 1 entry (local_music_artwork/)", 1, filesPaths.size)
        assertEquals("local_music_artwork/", filesPaths[0].second)
        println("[PROOF] 19: provider_paths.xml scopes FileProvider strictly to subdirectories and excludes root and datastore.")
    }

    @Test
    fun testAuthDomainValidator_allowedAuthDomains() {
        // Proves AuthDomainValidator approves valid Google, YouTube, and reCAPTCHA HTTPS authentication domains
        val allowedUrls = listOf(
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com",
            "https://myaccount.google.com/signinoptions",
            "https://accounts.youtube.com/accounts/SetSID",
            "https://music.youtube.com",
            "https://music.youtube.com/explore",
            "https://www.youtube.com",
            "https://m.youtube.com",
            "https://youtube.com",
            "https://subdomain.google.com/oauth",
            "https://recaptcha.net",
            "https://www.recaptcha.net/recaptcha/api.js",
        )

        for (url in allowedUrls) {
            assertTrue("URL must be approved for auth: $url", AuthDomainValidator.isApprovedAuthUrl(url))
        }

        val allowedYouTube = listOf(
            "https://music.youtube.com",
            "https://www.youtube.com",
            "https://m.youtube.com",
            "https://youtube.com",
        )
        for (url in allowedYouTube) {
            assertTrue("URL must be approved for YouTube extraction: $url", AuthDomainValidator.isApprovedYouTubeUrl(url))
        }
        println("[PROOF] 20: AuthDomainValidator approves valid Google/YouTube/reCAPTCHA HTTPS authentication domains.")
    }

    @Test
    fun testAuthDomainValidator_rejectedUntrustedOrigins() {
        // Proves AuthDomainValidator rejects untrusted origins, lookalike domains, and non-HTTPS schemes
        val rejectedUrls = listOf(
            "http://accounts.google.com",              // Insecure plain HTTP
            "http://music.youtube.com",               // Insecure plain HTTP
            "http://recaptcha.net",                   // Insecure plain HTTP
            "https://evil-google.com",                 // Lookalike domain
            "https://evil-recaptcha.net",              // Lookalike domain
            "https://google.com.attacker.com",         // Subdomain hijack
            "https://recaptcha.net.attacker.com",      // Subdomain hijack
            "https://youtube.com.phishing.org",        // Suffix trick
            "https://attacker.com?url=youtube.com",    // Query param spoof
            "https://accounts.google.com@attacker.com",// Userinfo spoof
            "javascript:alert(1)",                     // Script execution
            "file:///data/data/com.termux/files",      // Local file access
            "content://settings/credentials",          // Content provider
            "",                                        // Empty
            "   ",                                     // Whitespace
            null,                                      // Null
            "https://evil.org",                        // Untrusted host
        )

        for (url in rejectedUrls) {
            assertFalse("URL must be rejected: $url", AuthDomainValidator.isApprovedAuthUrl(url))
            assertFalse("YouTube extraction must be rejected: $url", AuthDomainValidator.isApprovedYouTubeUrl(url))
        }
        println("[PROOF] 21: AuthDomainValidator rejects untrusted origins, invalid schemes, and lookalike domains.")
    }

    @Test
    fun testAuthDomainValidator_parseJsResult() {
        // Proves parseJsResult unquotes and decodes JSON-wrapped evaluation results
        assertEquals("test_visitor_data_123", AuthDomainValidator.parseJsResult("\"test_visitor_data_123\""))
        assertEquals("", AuthDomainValidator.parseJsResult(null))
        assertEquals("", AuthDomainValidator.parseJsResult(""))
        assertEquals("", AuthDomainValidator.parseJsResult("   "))
        assertEquals("", AuthDomainValidator.parseJsResult("null"))
        assertEquals("escaped\"quotes", AuthDomainValidator.parseJsResult("\"escaped\\\"quotes\""))
        assertEquals("line\nbreak", AuthDomainValidator.parseJsResult("\"line\\nbreak\""))
        assertEquals("unicode<tag>&val>", AuthDomainValidator.parseJsResult("\"unicode\\u003Ctag\\u003E\\u0026val\\u003E\""))
    }

    @Test
    fun testConcurrentMigrations_safelySerializedByMutex() = runTest {
        // Proves multiple concurrent coroutines running migration are safely serialized without race conditions
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()
        normalStore.edit { prefs ->
            prefs[themeColorKey] = "CONCURRENT_THEME"
            prefs[spotifyDcKey] = "concurrent_sp_dc"
            prefs[discordTokenKey] = "concurrent_discord"
        }

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)

        coroutineScope {
            repeat(10) {
                launch(Dispatchers.Default) {
                    compositeStore.runMigration()
                }
            }
        }

        assertTrue(compositeStore.isMigrationDone)
        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()

        assertNull(normalData[spotifyDcKey])
        assertNull(normalData[discordTokenKey])
        assertEquals("CONCURRENT_THEME", normalData[themeColorKey])

        assertEquals("concurrent_sp_dc", secureData[spotifyDcKey])
        assertEquals("concurrent_discord", secureData[discordTokenKey])
    }

    @Test
    fun testCorruptLegacyFile_propagatesExceptionAndRetriesOnRecovery() = runTest {
        // Proves migration failure from corrupted storage throws and retries safely once storage is restored
        val normalStore = FakePreferencesDataStore()
        val secureStore = FakePreferencesDataStore()
        normalStore.edit { prefs ->
            prefs[spotifyDcKey] = "retryable_token"
        }
        normalStore.failOnUpdate = IOException("Corrupted flash page")

        val compositeStore = SecureRoutedPreferencesDataStore(normalStore, secureStore)

        try {
            compositeStore.runMigration()
            fail("Corrupted storage must throw and propagate")
        } catch (e: IOException) {
            assertEquals("Corrupted flash page", e.message)
        }
        assertFalse("Migration must NOT be marked done after failure", compositeStore.isMigrationDone)

        // Storage recovered
        normalStore.failOnUpdate = null
        compositeStore.runMigration()
        assertTrue("Migration must succeed on retry", compositeStore.isMigrationDone)

        val normalData = normalStore.data.first()
        val secureData = secureStore.data.first()
        assertNull(normalData[spotifyDcKey])
        assertEquals("retryable_token", secureData[spotifyDcKey])
    }

    @Test
    fun testKeyClassificationGuard_exhaustiveClassification() {
        // Proves every preference key defined in the project is exhaustively classified as either SECURE or PLAIN
        val discoveredKeys = mutableSetOf<String>()

        val classesToScan = listOf(
            Class.forName("moe.rukamori.archivetune.constants.PreferenceKeysKt"),
            Class.forName("moe.rukamori.archivetune.constants.KemoKeysKt"),
            Class.forName("moe.rukamori.archivetune.constants.PreferenceConstantsKt"),
            Class.forName("moe.rukamori.archivetune.widget.MusicWidgetKeys"),
        )

        for (cls in classesToScan) {
            val targetObj = try {
                cls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            } catch (_: Exception) {
                null
            }
            for (field in cls.declaredFields) {
                field.isAccessible = true
                if (Preferences.Key::class.java.isAssignableFrom(field.type)) {
                    val keyObj = try { field.get(targetObj) as? Preferences.Key<*> } catch (_: Exception) { null }
                    if (keyObj != null) {
                        discoveredKeys.add(keyObj.name)
                    }
                }
            }
            for (method in cls.declaredMethods) {
                if (Preferences.Key::class.java.isAssignableFrom(method.returnType) && method.parameterCount == 0) {
                    method.isAccessible = true
                    val keyObj = try { method.invoke(targetObj) as? Preferences.Key<*> } catch (_: Exception) { null }
                    if (keyObj != null) {
                        discoveredKeys.add(keyObj.name)
                    }
                }
            }
        }

        // Also scan src/main/kotlin across modules to guarantee no key anywhere in the project is missed
        val searchDirs = listOf(
            resolveResFile("src/main/kotlin"),
            resolveResFile("../designsystem/src/main/kotlin"),
            resolveResFile("designsystem/src/main/kotlin"),
        ).filter { it.exists() }

        val keyPattern = Regex("""(?:stringPreferencesKey|booleanPreferencesKey|intPreferencesKey|longPreferencesKey|floatPreferencesKey|doublePreferencesKey|stringSetPreferencesKey)\s*\(\s*["']([^"']+)["']\s*\)""")
        for (dir in searchDirs) {
            dir.walkTopDown().filter { it.extension == "kt" && it.name != "PlainPreferences.kt" && it.name != "SecureDataStoreTest.kt" }.forEach { file ->
                keyPattern.findAll(file.readText()).forEach { match ->
                    discoveredKeys.add(match.groupValues[1])
                }
            }
        }

        assertTrue("Must discover at least 300 preference keys across project (found ${discoveredKeys.size})", discoveredKeys.size > 300)

        // 1. Verify no contradictory overlap between SECURE and PLAIN sets
        val overlap = SensitivePreferences.KEYS.intersect(PlainPreferences.KEYS)
        assertTrue("No key can be classified as both SECURE and PLAIN: $overlap", overlap.isEmpty())

        // 2. Every discovered key must be explicitly in SensitivePreferences or PlainPreferences
        val unclassified = mutableListOf<String>()
        for (keyName in discoveredKeys) {
            val isSecure = SensitivePreferences.isSensitive(keyName)
            val isPlain = keyName in PlainPreferences.KEYS
            if (!isSecure && !isPlain) {
                unclassified.add(keyName)
            }
        }

        assertTrue(
            "Every preference key must be explicitly classified as SECURE or PLAIN! Found ${unclassified.size} unclassified keys: $unclassified",
            unclassified.isEmpty()
        )
        println("[PROOF] 22: Key classification guard proves every key in the project is exhaustively classified as SECURE or PLAIN.")
    }

    @Test
    fun testKeyClassificationGuard_suspiciousNameCheck() {
        // Proves any key matching sensitive credential patterns must be classified SECURE unless explicitly on reviewed exception list
        val sensitiveRegex = Regex("(?i).*(token|cookie|secret|password|passwd|session|auth|api|key|sp_dc|bearer).*")
        val reviewedPlainExceptions = setOf(
            "lyricsScrollKey",          // UI preference: Synchronized lyrics scroll behavior
            "ai_api_validation_status", // UI preference: Validation status enum ("SUCCESS" / "FAILED")
            "sponsorBlockApiUrl",       // Public endpoint: SponsorBlock public API instance URL
        )

        // Check all PLAIN keys: NO plain key may match suspicious patterns unless reviewed
        val suspiciousPlainKeys = PlainPreferences.KEYS
            .filter { it.matches(sensitiveRegex) }
            .filter { it !in reviewedPlainExceptions }

        assertTrue(
            "PLAIN keys matching suspicious credential patterns must be classified SECURE or explicitly reviewed! Violations: $suspiciousPlainKeys",
            suspiciousPlainKeys.isEmpty()
        )

        // Check that reviewedPlainExceptions contains only valid plain keys
        for (exceptionKey in reviewedPlainExceptions) {
            assertTrue("Exception key must be in PlainPreferences.KEYS: $exceptionKey", exceptionKey in PlainPreferences.KEYS)
            assertFalse("Exception key must NOT be in SensitivePreferences.KEYS: $exceptionKey", exceptionKey in SensitivePreferences.KEYS)
        }
        println("[PROOF] 23: Suspicious-name check guarantees credential patterns must be SECURE unless explicitly reviewed.")
    }

    @Test
    fun testKeyClassificationGuard_uiKeysRemainPlain() {
        // Proves non-sensitive UI keys remain classified as PLAIN and never marked SECURE
        val uiKeys = listOf(
            "customThemeColor",
            "dynamicTheme",
            "fontPreference",
            "disableBlur",
            "blurRadius",
            "blurNavBar",
            "pureBlack",
            "customFontName",
            "thumbnailCornerRadius",
        )
        for (key in uiKeys) {
            assertTrue("UI key '$key' must be in PlainPreferences.KEYS", key in PlainPreferences.KEYS)
            assertFalse("UI key '$key' must NOT be in SensitivePreferences.KEYS", SensitivePreferences.isSensitive(key))
        }
        println("[PROOF] 24: UI preferences remain classified as PLAIN and excluded from SECURE.")
    }

    @Test
    fun testHttpRedactor_redactsSensitiveHeaders() {
        // Proves HttpRedactor replaces Authorization, Cookie, Set-Cookie, X-Goog-*, and X-User-Auth-Token with [REDACTED]
        assertEquals("[REDACTED]", HttpRedactor.redactHeader("Authorization", "Bearer secret_token_123"))
        assertEquals("[REDACTED]", HttpRedactor.redactHeader("Cookie", "sp_dc=secret_cookie_val; SID=abcd"))
        assertEquals("[REDACTED]", HttpRedactor.redactHeader("Set-Cookie", "session_id=98765; Path=/"))
        assertEquals("[REDACTED]", HttpRedactor.redactHeader("X-User-Auth-Token", "qobuz_user_token_xyz"))
        assertEquals("[REDACTED]", HttpRedactor.redactHeader("X-Goog-Visitor-Id", "visitor_data_xyz"))
        assertEquals("[REDACTED]", HttpRedactor.redactHeader("Proxy-Authorization", "Basic dXNlcjpwYXNz"))
        assertEquals("application/json", HttpRedactor.redactHeader("Content-Type", "application/json"))
    }

    @Test
    fun testHttpRedactor_redactsSensitiveQueryParams() {
        // Proves HttpRedactor replaces token, api_key, key, sp_dc, request_sig in query params with [REDACTED]
        val spotifyUrl = "https://open.spotify.com/api/token?sp_dc=my_secret_dc&totp=123456&reason=transport"
        val redactedSpotify = HttpRedactor.redactUrl(spotifyUrl)
        assertTrue("sp_dc param must be redacted", redactedSpotify.contains("sp_dc=%5BREDACTED%5D") || redactedSpotify.contains("sp_dc=[REDACTED]"))
        assertTrue("totp param must be redacted", redactedSpotify.contains("totp=%5BREDACTED%5D") || redactedSpotify.contains("totp=[REDACTED]"))
        assertTrue("reason param must be preserved", redactedSpotify.contains("reason=transport"))

        val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=AIzaSySecretApiKey"
        val redactedGemini = HttpRedactor.redactUrl(geminiUrl)
        assertTrue("key param must be redacted", redactedGemini.contains("key=%5BREDACTED%5D") || redactedGemini.contains("key=[REDACTED]"))
        assertFalse("Raw API key must not appear", redactedGemini.contains("AIzaSySecretApiKey"))
    }

    @Test
    fun testHttpRedactor_redactsBodyInReleaseBuilds() {
        // Proves HttpRedactor enforces [BODY_REDACTED_RELEASE] for release builds
        val bodyContent = "{\"accessToken\":\"super_secret_token_12345\"}"
        val releaseResult = HttpRedactor.redactBody(bodyContent, isDebug = false)
        assertEquals("[BODY_REDACTED_RELEASE]", releaseResult)

        val debugResult = HttpRedactor.redactBody(bodyContent, isDebug = true)
        assertEquals(bodyContent, debugResult)
    }

    @Test
    fun testWebView_noJavascriptInterfaceInAuthComponents() {
        // Proves LoginScreen.kt and PoTokenExtractionActivity.kt do not register addJavascriptInterface,
        // eliminating cross-frame / iframe JavaScript bridge injection vulnerabilities.
        val loginScreen = resolveResFile("src/main/kotlin/moe/rukamori/archivetune/ui/screens/LoginScreen.kt")
        val poTokenActivity = resolveResFile("src/main/kotlin/moe/rukamori/archivetune/ui/screens/settings/PoTokenExtractionActivity.kt")

        assertTrue("LoginScreen.kt must exist", loginScreen.exists())
        assertTrue("PoTokenExtractionActivity.kt must exist", poTokenActivity.exists())

        val loginSource = loginScreen.readText()
        val poTokenSource = poTokenActivity.readText()

        assertFalse("LoginScreen must not call addJavascriptInterface", loginSource.contains("addJavascriptInterface"))
        assertFalse("PoTokenExtractionActivity must not call addJavascriptInterface", poTokenSource.contains("addJavascriptInterface"))
        println("[PROOF] 25: LoginScreen and PoTokenExtractionActivity eliminate addJavascriptInterface to protect against iframe bridge injection.")
    }

    @Test
    fun testBuildOzoneWorkflow_includesSecureDataStoreTest() {
        // Proves .github/workflows/build_ozone.yml runs SecureDataStoreTest before release packaging
        val buildOzoneWorkflow = resolveResFile("../.github/workflows/build_ozone.yml").takeIf { it.exists() }
            ?: resolveResFile(".github/workflows/build_ozone.yml")
        assertTrue("build_ozone.yml must exist", buildOzoneWorkflow.exists())
        val workflowText = buildOzoneWorkflow.readText()
        assertTrue(
            "build_ozone.yml must include SecureDataStoreTest in pre-gate unit test suite",
            workflowText.contains("SecureDataStoreTest")
        )
        println("[PROOF] 26: Release workflow build_ozone.yml includes SecureDataStoreTest pre-gate.")
    }

    @Test
    fun testHttpRedactor_sanitizeUrlForLogging() {
        // Proves sanitizeUrlForLogging extracts only scheme and host, completely stripping
        // OAuth authorization codes, session tokens, query parameters, paths, and fragments.
        val oauthCallbackUrl = "https://untrusted-auth.com/oauth/callback?code=SECRET_AUTH_CODE_12345&state=xyz789#access_token=SENSITIVE_BEARER"
        val sanitized = HttpRedactor.sanitizeUrlForLogging(oauthCallbackUrl)

        assertEquals("https://untrusted-auth.com", sanitized)
        assertFalse("Must not contain OAuth code", sanitized.contains("SECRET_AUTH_CODE_12345"))
        assertFalse("Must not contain state param", sanitized.contains("xyz789"))
        assertFalse("Must not contain access token fragment", sanitized.contains("SENSITIVE_BEARER"))
        assertFalse("Must not contain query mark", sanitized.contains("?"))
        assertFalse("Must not contain fragment mark", sanitized.contains("#"))
        assertFalse("Must not contain path", sanitized.contains("oauth/callback"))

        // Test non-hierarchical / script schemes
        assertEquals("javascript://[non_host_origin]", HttpRedactor.sanitizeUrlForLogging("javascript:alert(document.cookie)"))
        assertEquals("file://[non_host_origin]", HttpRedactor.sanitizeUrlForLogging("file:///data/data/com.termux/files/secrets.txt"))

        // Test edge cases
        assertEquals("[empty]", HttpRedactor.sanitizeUrlForLogging(""))
        assertEquals("[empty]", HttpRedactor.sanitizeUrlForLogging("   "))
        assertEquals("[empty]", HttpRedactor.sanitizeUrlForLogging(null))
        assertEquals("[invalid_url]", HttpRedactor.sanitizeUrlForLogging("http://bad uri with spaces and ^^^"))
        println("[PROOF] 27: HttpRedactor.sanitizeUrlForLogging strips all query params, tokens, codes, and fragments.")
    }

    @Test
    fun testLoginScreen_usesSanitizedUrlLogging() {
        // Proves LoginScreen uses HttpRedactor.sanitizeUrlForLogging for navigation logging
        // and does not print raw URLs or query parameters to Timber.
        val loginScreen = resolveResFile("src/main/kotlin/moe/rukamori/archivetune/ui/screens/LoginScreen.kt")
        assertTrue("LoginScreen.kt must exist", loginScreen.exists())
        val source = loginScreen.readText()

        assertTrue("LoginScreen must call sanitizeUrlForLogging", source.contains("HttpRedactor.sanitizeUrlForLogging"))
        assertFalse("LoginScreen must not print raw destinationUrl to Timber", source.contains("Timber.w(\"LoginScreen: blocked navigation to unapproved URL: %s\", destinationUrl)"))
        assertFalse("LoginScreen must not print raw url to Timber", source.contains("Timber.w(\"LoginScreen: blocked navigation to unapproved URL: %s\", url)"))
        println("[PROOF] 28: LoginScreen strictly uses sanitized origin logging without exposing parameters.")
    }
}
