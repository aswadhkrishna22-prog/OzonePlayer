/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.streaming

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache that remembers:
 * 1. The original decrypted CDN URL per song ID or track ID.
 * 2. The working quality tier per song so failing URLs (e.g. 320 kbps 404s)
 *    are never re-attempted on subsequent plays.
 */
object JioSaavnQualityCache {
    private val workingTiers = ConcurrentHashMap<String, JioSaavnQualityTier>()
    private val rawUrls = ConcurrentHashMap<String, String>()

    fun recordWorkingTier(songKey: String, tier: JioSaavnQualityTier) {
        val key = cleanKey(songKey)
        if (key.isNotBlank()) {
            workingTiers[key] = tier
        }
    }

    fun getWorkingTier(songKey: String): JioSaavnQualityTier {
        val key = cleanKey(songKey)
        return workingTiers[key] ?: JioSaavnQualityTier.KBPS_320
    }

    fun storeRawUrl(songKey: String, rawUrl: String) {
        val key = cleanKey(songKey)
        if (key.isNotBlank() && rawUrl.isNotBlank()) {
            rawUrls[key] = rawUrl
        }
    }

    fun getRawUrl(songKey: String): String? {
        val key = cleanKey(songKey)
        return rawUrls[key]
    }

    fun clear() {
        workingTiers.clear()
        rawUrls.clear()
    }

    fun cleanKey(key: String): String = key.removePrefix("jiosaavn_").trim()
}
