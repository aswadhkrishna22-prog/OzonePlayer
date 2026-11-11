/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.matching

import moe.rukamori.archivetune.innertube.models.SongItem
import kotlin.math.abs

object SpotifyTrackScorer {
    const val MATCH_CACHE_VERSION = 2

    val KNOWN_LANGUAGES = listOf(
        "malayalam", "tamil", "telugu", "hindi",
        "kannada", "punjabi", "bengali", "marathi"
    )

    private val UNWANTED_TOKENS = listOf(
        "karaoke", "tribute", "instrumental", "backing track", "cover", "parody",
        "reaction", "slowed", "sped up", "nightcore", "8d audio", "ringtone", "bass boosted"
    )

    fun cacheKey(spotifyTrackId: String): String {
        val rawId = spotifyTrackId.removePrefix("spotify:track:").removePrefix("spotify:").trim()
        return "v${MATCH_CACHE_VERSION}:$rawId"
    }

    fun extractBaseTitle(title: String): String {
        val cleaned =
            title
                .replace(Regex("""(?i)\s*[\(\[]\s*(?:from\s+["“']?[^"”'\)\]]+["”']?|original\s+(?:motion\s+picture\s+)?soundtrack|soundtrack|ost)\s*[\)\]]"""), "")
                .replace(Regex("""(?i)\s*[-–—]\s*(?:from\s+["“']?[^"”']+["”']?|original\s+(?:motion\s+picture\s+)?soundtrack|soundtrack|ost).*$"""), "")
                .replace(Regex("""(?i)\s*[-–—]\s*from\s+.*$"""), "")
                .replace(Regex("""(?i)\s*[\(\[]\s*(?:feat\.|ft\.|featuring)\s+[^)]*[\)\]]"""), "")
                .trim()
                .trim('-', '–', '—', '"', '\'', ' ', '\t')

        return cleaned.ifBlank { title }
    }

    fun detectLanguage(text: String): String? {
        val sanitized =
            text
                .replace(Regex("""(?i)\benglish\s+sub(?:titles?)?\b"""), "")
                .replace(Regex("""(?i)\beng\s+sub\b"""), "")
        val lower = sanitized.lowercase()
        return KNOWN_LANGUAGES.firstOrNull { lang ->
            Regex("""\b${lang}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        }
    }

    private fun normalizeText(text: String): String =
        text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    data class ArtistComparison(
        val overlapRatio: Double,
        val matchedTargetCount: Int,
        val totalTargetCount: Int,
        val missingTargetArtists: List<String>,
        val foreignArtists: List<String>,
        val score: Double,
    )

    fun compareArtistSets(
        targetArtists: List<String>,
        candArtists: List<String>,
        candTitle: String = "",
    ): ArtistComparison {
        if (targetArtists.isEmpty()) {
            return ArtistComparison(1.0, 0, 0, emptyList(), emptyList(), 0.0)
        }

        val normTarget = targetArtists.map { normalizeText(it) }.filter { it.isNotBlank() }
        val normCand = candArtists.map { normalizeText(it) }.filter { it.isNotBlank() }
        val candAllText = normalizeText((candArtists + candTitle).joinToString(" "))

        val matchedTargets = mutableListOf<String>()
        val missingTargets = mutableListOf<String>()

        for (ta in normTarget) {
            val taTokens = ta.split(" ").filter { it.length >= 3 }
            val matched = normCand.any { ca ->
                ca == ta || ca.contains(ta) || ta.contains(ca) ||
                    (taTokens.isNotEmpty() && taTokens.all { ca.contains(it) })
            } || (taTokens.isNotEmpty() && taTokens.all { candAllText.contains(it) })

            if (matched) {
                matchedTargets.add(ta)
            } else {
                missingTargets.add(ta)
            }
        }

        val foreignArtists = normCand.filter { ca ->
            val caTokens = ca.split(" ").filter { it.length >= 3 }
            ca.length >= 4 && normTarget.none { ta ->
                ta == ca || ta.contains(ca) || ca.contains(ta) ||
                    (caTokens.isNotEmpty() && caTokens.all { ta.contains(it) })
            } && !candAllText.contains("music") && !candAllText.contains("records")
        }

        val overlapRatio = matchedTargets.size.toDouble() / normTarget.size

        var artistScore = 0.0
        if (overlapRatio == 1.0) {
            // Full match: every target artist present!
            artistScore += 45.0
        } else {
            artistScore += overlapRatio * 20.0
            // Heavy penalty for each missing performer (e.g. main singer)
            artistScore -= missingTargets.size * 35.0
        }

        // Heavy penalty for unrequested foreign main performer
        if (foreignArtists.isNotEmpty() && overlapRatio < 1.0) {
            artistScore -= foreignArtists.size * 30.0
        }

        return ArtistComparison(
            overlapRatio = overlapRatio,
            matchedTargetCount = matchedTargets.size,
            totalTargetCount = normTarget.size,
            missingTargetArtists = missingTargets,
            foreignArtists = foreignArtists,
            score = artistScore,
        )
    }

    data class ScoredCandidate(
        val candidate: SongItem,
        val totalScore: Double,
        val titleScore: Double,
        val artistScore: Double,
        val languageScore: Double,
        val officialScore: Double,
        val durationScore: Double,
        val isDisqualified: Boolean = false,
        val disqualificationReason: String? = null,
    )

    fun scoreCandidate(
        targetTitle: String,
        targetArtists: List<String>,
        targetAlbum: String? = null,
        targetDurationMs: Long = 0L,
        contextQuery: String? = null,
        candidate: SongItem,
        isOfficialSong: Boolean = false,
    ): ScoredCandidate {
        val targetBaseTitle = extractBaseTitle(targetTitle)
        val normTargetTitle = normalizeText(targetBaseTitle).ifBlank { normalizeText(targetTitle) }
        val candCleanTitle = extractBaseTitle(candidate.title)
        val normCandTitle = normalizeText(candCleanTitle).ifBlank { normalizeText(candidate.title) }

        // 1. Check Unwanted Tokens
        val hasUnwanted = UNWANTED_TOKENS.any { token ->
            candidate.title.contains(token, ignoreCase = true) && !targetTitle.contains(token, ignoreCase = true)
        }
        if (hasUnwanted) {
            return ScoredCandidate(
                candidate = candidate,
                totalScore = -100.0,
                titleScore = 0.0,
                artistScore = 0.0,
                languageScore = 0.0,
                officialScore = 0.0,
                durationScore = 0.0,
                isDisqualified = true,
                disqualificationReason = "Contains unwanted token",
            )
        }

        // 2. Title Scoring
        val titleTokens = normTargetTitle.split(" ").filter { it.length >= 3 }
        val candTitleTokens = normCandTitle.split(" ").filter { it.length >= 3 }
        val commonTitleWords = titleTokens.count { tw -> candTitleTokens.any { it.contains(tw) || tw.contains(it) } }
        val titleWordRatio = if (titleTokens.isNotEmpty()) commonTitleWords.toDouble() / titleTokens.size else 0.0

        val titleContained = normTargetTitle.isNotBlank() && (
            normCandTitle.contains(normTargetTitle) || normTargetTitle.contains(normCandTitle)
        )
        var titleScore = titleWordRatio * 30.0
        if (titleContained) titleScore += 15.0

        // 3. Artist Set Scoring
        val candArtistNames = candidate.artists.map { it.name }
        val artistComp = compareArtistSets(targetArtists, candArtistNames, candidate.title)

        // 4. Language Words Scoring
        val combinedTarget = "$targetTitle ${targetAlbum.orEmpty()} ${contextQuery.orEmpty()}"
        val combinedCand = "${candidate.title} ${candidate.artists.joinToString(" ") { it.name }}"
        val targetLang = detectLanguage(combinedTarget)
        val candLang = detectLanguage(combinedCand)

        var languageScore = 0.0
        var isLangDisqualified = false
        if (targetLang != null) {
            if (candLang == targetLang) {
                languageScore += 35.0 // Reward matching language!
            } else if (candLang != null && candLang != targetLang) {
                languageScore -= 65.0 // Severe penalty for conflicting language dub!
                isLangDisqualified = true
            }
        } else if (candLang != null) {
            languageScore -= 20.0
        }

        // 5. Official Song Preference
        var officialScore = 0.0
        if (isOfficialSong) {
            officialScore += 20.0
        } else if (candidate.title.contains("official audio", ignoreCase = true)) {
            officialScore += 12.0
        } else if (candidate.title.contains("lyric video", ignoreCase = true) || candidate.title.contains("video song", ignoreCase = true)) {
            officialScore += 2.0
        }

        // 6. Duration Delta (Small Weight)
        val targetDurSec = if (targetDurationMs > 0) targetDurationMs / 1000 else 0
        val candDurSec = (candidate.duration ?: 0).toLong()
        var durationScore = 0.0
        var isDurDisqualified = false

        if (targetDurSec > 0 && candDurSec > 0) {
            val delta = abs(targetDurSec - candDurSec)
            if (delta <= 2) {
                durationScore += 5.0
            } else if (delta <= 5) {
                durationScore += 3.0
            } else if (delta <= 15) {
                durationScore += 1.0
            } else if (delta > 60) {
                isDurDisqualified = true
            }
        }

        val isDisqualified = isLangDisqualified || isDurDisqualified
        val total = if (isDisqualified) -999.0 else (titleScore + artistComp.score + languageScore + officialScore + durationScore)

        return ScoredCandidate(
            candidate = candidate,
            totalScore = total,
            titleScore = titleScore,
            artistScore = artistComp.score,
            languageScore = languageScore,
            officialScore = officialScore,
            durationScore = durationScore,
            isDisqualified = isDisqualified,
            disqualificationReason = if (isLangDisqualified) "Language conflict" else if (isDurDisqualified) "Duration delta > 60s" else null,
        )
    }

    sealed interface AmbiguityResult {
        data class ClearWinner(val accepted: SongItem, val score: Double) : AmbiguityResult
        data class Ambiguous(val topCandidates: List<SongItem>, val best: SongItem) : AmbiguityResult
        data object NoCandidates : AmbiguityResult
    }

    fun evaluateAmbiguity(
        scored: List<ScoredCandidate>,
        targetArtists: List<String>,
        ambiguityDeltaThreshold: Double = 15.0,
    ): AmbiguityResult {
        val valid = scored.filter { !it.isDisqualified && it.totalScore > 0 }.sortedByDescending { it.totalScore }
        if (valid.isEmpty()) return AmbiguityResult.NoCandidates

        val first = valid[0]
        if (valid.size == 1) {
            return AmbiguityResult.ClearWinner(first.candidate, first.totalScore)
        }

        val second = valid[1]
        val scoreDiff = abs(first.totalScore - second.totalScore)

        // Check if top 2 candidates have different artist sets
        val firstArtists = first.candidate.artists.map { normalizeText(it.name) }.toSet()
        val secondArtists = second.candidate.artists.map { normalizeText(it.name) }.toSet()
        val artistSetsDiffer = firstArtists != secondArtists

        if (scoreDiff < ambiguityDeltaThreshold && artistSetsDiffer) {
            // Close in score and have different artist sets -> AMBIGUITY GUARD!
            return AmbiguityResult.Ambiguous(
                topCandidates = valid.take(8).map { it.candidate },
                best = first.candidate,
            )
        }

        return AmbiguityResult.ClearWinner(first.candidate, first.totalScore)
    }
}
