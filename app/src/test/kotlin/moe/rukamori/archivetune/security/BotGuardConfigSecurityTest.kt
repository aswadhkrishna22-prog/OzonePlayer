/*
 * Ozone Player (2026)
 * Security Remediation Regression Suite
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.security

import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BotGuardConfigSecurityTest {

    @Before
    fun setUp() {
        BotGuardTokenGenerator.customApiKey = null
    }

    @After
    fun tearDown() {
        BotGuardTokenGenerator.customApiKey = null
    }

    @Test
    fun apiKey_defaultsToBuildConfig() {
        assertNull(BotGuardTokenGenerator.customApiKey)
        assertEquals(BuildConfig.BOTGUARD_API_KEY, BotGuardTokenGenerator.apiKey)
    }

    @Test
    fun customApiKey_overridesBuildConfigWhenNonBlank() {
        val testKey = "test_custom_secure_key"
        BotGuardTokenGenerator.customApiKey = testKey
        assertEquals(testKey, BotGuardTokenGenerator.apiKey)
    }

    @Test
    fun customApiKey_blank_fallsBackToBuildConfig() {
        BotGuardTokenGenerator.customApiKey = "   "
        assertEquals(BuildConfig.BOTGUARD_API_KEY, BotGuardTokenGenerator.apiKey)
    }

    @Test
    fun mintToken_returnsNullWhenApiKeyIsBlank() = runBlocking {
        BotGuardTokenGenerator.customApiKey = ""
        val result = BotGuardTokenGenerator.mintToken("test_video_id", "test_session_id")
        assertNull(result)
    }

    @Test
    fun playbackPatchFile_doesNotExistInRepository() {
        val patchFile = File("../playback-full.patch")
        assertFalse("playback-full.patch must not exist in repository", patchFile.exists())
    }

    @Test
    fun botGuardTokenGeneratorClass_containsNoHardcodedGoogleApiKey() {
        val fields = BotGuardTokenGenerator::class.java.declaredFields
        val googleKeyRegex = Regex("AIza[0-9A-Za-z\\-_]{35}")
        for (field in fields) {
            field.isAccessible = true
            val value = try {
                field.get(BotGuardTokenGenerator)?.toString() ?: ""
            } catch (e: Exception) {
                ""
            }
            assertFalse(
                "Field ${field.name} contains hardcoded Google API key",
                googleKeyRegex.containsMatchIn(value)
            )
        }
    }
}
