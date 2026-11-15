/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import java.net.URI

/**
 * Validates URLs for WebView navigation and JavaScript bridge injection.
 * Restricts navigation strictly to approved Google and YouTube authentication endpoints
 * over HTTPS, preventing malicious origins from accessing native JavaScript interfaces
 * or capturing session credentials.
 */
object AuthDomainValidator {

    private val APPROVED_AUTH_HOST_SUFFIXES = listOf(
        "google.com",
        "youtube.com",
    )

    private val APPROVED_YOUTUBE_HOST_SUFFIXES = listOf(
        "youtube.com",
    )

    /**
     * Verifies that [url] is a valid HTTPS URL destined for an explicitly approved
     * Google or YouTube authentication/service host.
     * Rejects non-HTTPS schemes (http, javascript, file, content), null/blank URLs,
     * and unapproved or lookalike domains.
     */
    fun isApprovedAuthUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try {
            URI.create(url)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme ?: return false
        if (!scheme.equals("https", ignoreCase = true)) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        return isHostApproved(host, APPROVED_AUTH_HOST_SUFFIXES)
    }

    /**
     * Verifies that [url] is an approved HTTPS YouTube domain (e.g. music.youtube.com, www.youtube.com)
     * suitable for YouTube credential extraction script injection.
     */
    fun isApprovedYouTubeUrl(url: String?): Boolean {
        if (!isApprovedAuthUrl(url)) return false
        val host = try {
            URI.create(url).host?.lowercase() ?: return false
        } catch (_: Exception) {
            return false
        }
        return isHostApproved(host, APPROVED_YOUTUBE_HOST_SUFFIXES)
    }

    private fun isHostApproved(host: String, approvedSuffixes: List<String>): Boolean {
        return approvedSuffixes.any { approved ->
            host == approved || host.endsWith(".$approved")
        }
    }
}
