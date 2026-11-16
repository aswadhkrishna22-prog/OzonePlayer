/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.streaming

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Quality tiers for JioSaavn audio streaming.
 */
enum class JioSaavnQualityTier(val nominalBitrateKbps: Int) {
    KBPS_320(320),
    KBPS_160(160),
    ORIGINAL(0);

    fun nextLower(): JioSaavnQualityTier? = when (this) {
        KBPS_320 -> KBPS_160
        KBPS_160 -> ORIGINAL
        ORIGINAL -> null
    }
}

/**
 * Handles DES-ECB decryption of JioSaavn's encrypted_media_url strings to obtain
 * direct high-speed CDN audio URLs and upgrades or downgrades them between 320, 160, and original bitrates.
 */
object JioSaavnDecryptor {
    private const val DES_KEY = "38346591"
    private const val TRANSFORMATION = "DES/ECB/PKCS5Padding"

    /**
     * Decrypts the Base64-encoded encrypted_media_url using standard DES-ECB.
     */
    fun decryptMediaUrl(encryptedMediaUrl: String): String? {
        if (encryptedMediaUrl.isBlank()) return null
        return try {
            val keyBytes = DES_KEY.toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.getDecoder().decode(encryptedMediaUrl.trim())
            val decryptedBytes = cipher.doFinal(decoded)
            val decryptedUrl = String(decryptedBytes, Charsets.UTF_8).trim()
            decryptedUrl.takeIf { it.startsWith("http") }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts a JioSaavn CDN audio URL from standard quality (_96.mp4 / _160.mp4)
     * to original full-bitrate 320 kbps AAC (_320.mp4 / _320.m4a).
     */
    fun to320KbpsUrl(rawUrl: String): String {
        return rawUrl
            .replace("_96.mp4", "_320.mp4")
            .replace("_160.mp4", "_320.mp4")
            .replace("_96.m4a", "_320.m4a")
            .replace("_160.m4a", "_320.m4a")
    }

    /**
     * Converts a JioSaavn CDN audio URL to standard 160 kbps AAC (_160.mp4 / _160.m4a).
     */
    fun to160KbpsUrl(rawUrl: String): String {
        return rawUrl
            .replace("_320.mp4", "_160.mp4")
            .replace("_96.mp4", "_160.mp4")
            .replace("_320.m4a", "_160.m4a")
            .replace("_96.m4a", "_160.m4a")
    }

    /**
     * Converts a JioSaavn CDN audio URL to 96 kbps AAC (_96.mp4 / _96.m4a).
     */
    fun to96KbpsUrl(rawUrl: String): String {
        return rawUrl
            .replace("_320.mp4", "_96.mp4")
            .replace("_160.mp4", "_96.mp4")
            .replace("_320.m4a", "_96.m4a")
            .replace("_160.m4a", "_96.m4a")
    }

    /**
     * Detects the nominal quality tier based on the URL suffix.
     */
    fun detectTierFromUrl(url: String): JioSaavnQualityTier {
        return when {
            url.contains("_320.") -> JioSaavnQualityTier.KBPS_320
            url.contains("_160.") -> JioSaavnQualityTier.KBPS_160
            else -> JioSaavnQualityTier.ORIGINAL
        }
    }

    /**
     * Resolves the stream URL for a given quality tier.
     */
    fun getUrlForTier(rawUrl: String, tier: JioSaavnQualityTier): String = when (tier) {
        JioSaavnQualityTier.KBPS_320 -> to320KbpsUrl(rawUrl)
        JioSaavnQualityTier.KBPS_160 -> to160KbpsUrl(rawUrl)
        JioSaavnQualityTier.ORIGINAL -> rawUrl
    }

    /**
     * Builds the ordered fallback sequence of distinct (Tier, URL) pairs.
     * Order: 320 kbps -> 160 kbps -> original decrypted URL.
     * If the original decrypted URL was already 160 kbps, also includes 96 kbps as last resort.
     */
    fun buildFallbackSequence(rawUrl: String): List<Pair<JioSaavnQualityTier, String>> {
        val result = mutableListOf<Pair<JioSaavnQualityTier, String>>()
        val seen = mutableSetOf<String>()

        val url320 = to320KbpsUrl(rawUrl)
        if (url320.isNotBlank() && seen.add(url320)) {
            result.add(JioSaavnQualityTier.KBPS_320 to url320)
        }

        val url160 = to160KbpsUrl(rawUrl)
        if (url160.isNotBlank() && seen.add(url160)) {
            result.add(JioSaavnQualityTier.KBPS_160 to url160)
        }

        if (rawUrl.isNotBlank() && seen.add(rawUrl)) {
            result.add(JioSaavnQualityTier.ORIGINAL to rawUrl)
        } else {
            // rawUrl was already 160 kbps, so add 96 kbps as original/fallback
            val url96 = to96KbpsUrl(rawUrl)
            if (url96.isNotBlank() && seen.add(url96)) {
                result.add(JioSaavnQualityTier.ORIGINAL to url96)
            }
        }

        return result
    }

    /**
     * Returns the next lower fallback quality and URL given the current URL and the original raw URL.
     * Returns null when all fallback options have been exhausted.
     */
    fun getNextFallback(currentUrl: String, rawUrl: String): Pair<JioSaavnQualityTier, String>? {
        val sequence = buildFallbackSequence(rawUrl)
        if (sequence.isEmpty()) return null

        val currentTier = detectTierFromUrl(currentUrl)
        val currentIndex = sequence.indexOfFirst { it.first == currentTier || it.second == currentUrl }

        return when {
            currentIndex in 0 until sequence.lastIndex -> sequence[currentIndex + 1]
            currentIndex == -1 && sequence.size > 1 -> sequence[1]
            else -> null
        }
    }
}
