/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Shared HTTP redaction helper to prevent sensitive authentication tokens,
 * cookies, and credentials from reaching system logs, logcat, or error reports.
 */
object HttpRedactor {

    private val SENSITIVE_HEADERS = setOf(
        "authorization",
        "cookie",
        "set-cookie",
        "proxy-authorization",
        "x-user-auth-token",
        "x-api-key",
        "x-goog-api-key",
    )

    private val SENSITIVE_QUERY_PARAM_REGEX = Regex(
        "(?i)^(token|cookie|secret|password|passwd|session|auth|api_key|apikey|key|sp_dc|bearer|request_sig|totp|totpServer)$"
    )

    /**
     * Redacts sensitive HTTP header values.
     */
    fun redactHeader(name: String, value: String): String {
        val lowerName = name.lowercase(Locale.ROOT)
        if (lowerName in SENSITIVE_HEADERS ||
            lowerName.startsWith("x-goog-") ||
            lowerName.startsWith("x-spotify-")
        ) {
            return "[REDACTED]"
        }
        return value
    }

    /**
     * Redacts query parameters matching sensitive credential patterns in URLs.
     */
    fun redactUrl(urlString: String): String {
        val httpUrl = urlString.toHttpUrlOrNull() ?: return redactUrlFallback(urlString)
        val queryNames = httpUrl.queryParameterNames
        if (queryNames.none { it.matches(SENSITIVE_QUERY_PARAM_REGEX) }) {
            return urlString
        }

        val builder = httpUrl.newBuilder()
        for (name in queryNames) {
            if (name.matches(SENSITIVE_QUERY_PARAM_REGEX)) {
                builder.removeAllQueryParameters(name)
                builder.addQueryParameter(name, "[REDACTED]")
            }
        }
        return builder.build().toString()
    }

    private fun redactUrlFallback(rawUrl: String): String {
        val parts = rawUrl.split("?", limit = 2)
        if (parts.size < 2) return rawUrl
        val baseUrl = parts[0]
        val query = parts[1]
        val redactedQuery = query.split("&").joinToString("&") { param ->
            val kv = param.split("=", limit = 2)
            if (kv.isNotEmpty() && kv[0].matches(SENSITIVE_QUERY_PARAM_REGEX)) {
                "${kv[0]}=[REDACTED]"
            } else {
                param
            }
        }
        return "$baseUrl?$redactedQuery"
    }

    /**
     * Sanitizes HTTP response or request bodies for logging.
     * Enforces that release builds NEVER log HTTP bodies.
     */
    fun redactBody(body: String?, isDebug: Boolean = BuildConfig.DEBUG): String {
        if (!isDebug) {
            return "[BODY_REDACTED_RELEASE]"
        }
        if (body.isNullOrBlank()) {
            return "[EMPTY_BODY]"
        }
        // In debug builds, truncate large payloads to prevent log spam
        return if (body.length > 500) {
            body.take(500) + "... [TRUNCATED]"
        } else {
            body
        }
    }

    /**
     * Sanitizes a URL for secure logging by extracting only the scheme and host,
     * completely stripping OAuth authorization codes, session tokens, query parameters,
     * user credentials, paths, and URL fragments.
     * Prevents private authentication state and sensitive redirect parameters from reaching logs.
     */
    fun sanitizeUrlForLogging(urlString: String?): String {
        if (urlString.isNullOrBlank()) return "[empty]"
        val uri = try {
            java.net.URI.create(urlString.trim())
        } catch (_: Exception) {
            return "[invalid_url]"
        }
        val scheme = uri.scheme ?: "unknown"
        val host = uri.host
        return if (!host.isNullOrBlank()) {
            "$scheme://$host"
        } else {
            // For non-hierarchical or custom schemes (e.g. javascript:, file:, about:)
            "$scheme://[non_host_origin]"
        }
    }
}
