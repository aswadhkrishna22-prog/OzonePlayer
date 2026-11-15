/*
 * Ozone Player (2026)
 * Track & Artist Name Sanitizer for Catalogue Matching
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.covers

import java.util.Locale

object TrackCleaner {
    private val PARENTHETICAL_JUNK = Regex(
        """(?i)\s*[\(\[\{]\s*(?:official\s*(?:video|audio|music\s*video|lyric\s*video|visualizer|hd|4k|mv)?|lyrics?|audio|video|visualizer|remastered(?:\s*\d{4})?|mono|stereo|bonus\s*track|live(?:\s*at\s*[^)\]}]+)?)\s*[\)\]\}]"""
    )

    private val FEATURING_REGEX = Regex(
        """(?i)\s+(?:feat\.?|ft\.?|featuring)\s+[^(\[\{]+"""
    )

    data class CleanTrack(
        val title: String,
        val artist: String,
    )

    /**
     * Cleans titles and artist strings by stripping common YouTube/Vevo video decorations,
     * such as "(Official Music Video)", "[4K]", "feat. XYZ", "(Live)", etc.
     */
    fun clean(rawTitle: String, rawArtist: String): CleanTrack {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()

        // Strip featuring artist from title if present
        title = FEATURING_REGEX.replace(title, "")

        // Strip parenthetical/bracketed junk
        title = PARENTHETICAL_JUNK.replace(title, "").trim()

        // Strip "Topic" suffix added by YouTube automated artist channels
        if (artist.endsWith(" - Topic", ignoreCase = true)) {
            artist = artist.substringBeforeLast(" - Topic").trim()
        }

        // If title is "Artist - Title", split and resolve
        if (title.contains(" - ") && !title.startsWith("-")) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                val candidateArtist = parts[0].trim()
                val candidateTitle = PARENTHETICAL_JUNK.replace(parts[1].trim(), "").trim()

                // If rawArtist is generic or contained in candidateArtist, adopt candidateArtist
                if (artist.isBlank() || candidateArtist.contains(artist, ignoreCase = true) || artist.contains(candidateArtist, ignoreCase = true)) {
                    artist = candidateArtist
                    title = candidateTitle
                }
            }
        }

        return CleanTrack(
            title = title.ifEmpty { rawTitle.trim() },
            artist = artist.ifEmpty { rawArtist.trim() },
        )
    }

    /**
     * Extracts the primary artist name if multi-artist ("A, B & C" -> "A")
     */
    fun primaryArtist(artist: String): String {
        val delimiters = listOf(",", " & ", " feat ", " ft ", " x ", " / ")
        var first = artist
        for (d in delimiters) {
            if (first.contains(d, ignoreCase = true)) {
                first = first.split(Regex("(?i)" + Regex.escape(d)), limit = 2)[0]
            }
        }
        return first.trim()
    }

    /**
     * Normalizes string for strict comparison: lowercases and removes non-alphanumeric characters.
     */
    fun normalize(s: String): String {
        return s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }
}
