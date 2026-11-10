/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.streaming

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Handles DES-ECB decryption of JioSaavn's encrypted_media_url strings to obtain
 * direct high-speed CDN audio URLs and upgrades them to pristine 320 kbps AAC.
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
}
