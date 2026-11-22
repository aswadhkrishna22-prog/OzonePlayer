/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import android.text.format.DateUtils
import moe.rukamori.archivetune.betterlyrics.QRCParser
import moe.rukamori.archivetune.betterlyrics.TTMLParser
import moe.rukamori.archivetune.betterlyrics.TtmlTimingMode
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.ui.component.toLyricsWrappingUnits
import kotlin.math.roundToLong

@Suppress("RegExpRedundantEscape")
object LrcParser {
    /**
     * Clearly named constant/flag to respect the lyricOffset value in TTML metadata.
     * OFF (false) by default so developers can test whether begin times already include it.
     */
    const val RESPECT_TTML_LYRIC_OFFSET: Boolean = false
    val LINE_REGEX = Regex("""((\[\d{1,3}:\d{2}(?:[.:]\d+)?\]\s*)+)(.*)""")
    val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d+))?\]""")
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val ENHANCED_LRC_WORD_TIME_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")
    private val ENHANCED_LRC_WORD_TIME_CAPTURING_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?>""")
    private val INLINE_MILLISECONDS_TIME_REGEX = Regex("""<\d{1,8}(?:,\d{1,8})?>""")
    private val YRC_LINE_REGEX = Regex("""\[(\d{1,8}),\d{1,8}\](.*)""")
    private val YRC_WORD_TIME_REGEX = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val TTML_SPAN_REGEX =
        Regex(
            pattern = """<span\b[^>]*>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val TTML_BEGIN_ATTRIBUTE_REGEX = Regex("""\bbegin\s*=""", RegexOption.IGNORE_CASE)
    private val TTML_END_ATTRIBUTE_REGEX = Regex("""\b(?:end|dur)\s*=""", RegexOption.IGNORE_CASE)
    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
    private const val NBSP = '\u00A0'
    private const val INSTRUMENTAL_GAP_THRESHOLD_MS = 5000L
    private const val INSTRUMENTAL_INTRO_START_MS = 1000L
    private const val INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS = 2500L

    private val TTML_TIMING_ATTR_REGEX = Regex("""\b(?:itunes:)?timing\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TTML_LYRIC_OFFSET_REGEX = Regex("""\b(?:itunes:)?lyricOffset\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TTML_LYRIC_OFFSET_TAG_REGEX = Regex("""<(?:[A-Za-z_][\w.-]*:)?lyricOffset\b[^>]*>([^<]+)</""", RegexOption.IGNORE_CASE)
    private val TIME_SUFFIX_REGEX = Regex("""^([+-]?[0-9]+(?:\.[0-9]+)?)(ms|s|m|h)?$""", RegexOption.IGNORE_CASE)

    fun isLineInParentheses(text: String): Boolean {
        val trimmed = text.trim { it.isWhitespace() || it == NBSP }
        if (trimmed.length < 2) return false
        return (trimmed.startsWith('(') && trimmed.endsWith(')')) ||
            (trimmed.startsWith('（') && trimmed.endsWith('）'))
    }

    fun parseTimeMs(rawValue: String): Long? {
        val value = rawValue.trim().replace(',', '.')
        if (value.isEmpty()) return null

        TIME_SUFFIX_REGEX.matchEntire(value)?.let { match ->
            val amount = match.groupValues[1].toDoubleOrNull() ?: return null
            val unit = match.groupValues[2].lowercase()
            val seconds =
                when (unit) {
                    "h" -> amount * 3600.0
                    "m" -> amount * 60.0
                    "s" -> amount
                    "ms" -> amount / 1000.0
                    "" -> amount
                    else -> return null
                }
            return (seconds * 1000.0).roundToLong()
        }

        val parts = value.replace(';', ':').split(':')
        if (parts.size !in 1..4) return null
        val numeric = parts.map { it.toDoubleOrNull()?.takeIf { number -> number.isFinite() && number >= 0.0 } ?: return null }
        val seconds =
            when (numeric.size) {
                1 -> numeric[0]
                2 -> numeric[0] * 60.0 + numeric[1]
                3 -> numeric[0] * 3600.0 + numeric[1] * 60.0 + numeric[2]
                4 -> numeric[0] * 3600.0 + numeric[1] * 60.0 + numeric[2] + numeric[3] / 30.0
                else -> return null
            }
        return (seconds * 1000.0).roundToLong()
    }

    @JvmOverloads
    fun splitWords(
        start: Long,
        end: Long,
        text: String,
        isBackground: Boolean = false,
    ): List<WordTimestamp> {
        val cleanText = text.trim { it.isWhitespace() || it == NBSP }
        if (cleanText.isEmpty()) return emptyList()

        val isBg = isBackground || isLineInParentheses(cleanText)
        val words = cleanText.toLyricsWrappingUnits().ifEmpty { listOf(cleanText) }
        val totalDuration = (end - start).coerceAtLeast(0L)
        val totalWeight = words.sumOf { word ->
            val len = word.trim().length.let { if (it > 0) it else word.length }.coerceAtLeast(1)
            len + 2
        }.coerceAtLeast(1)

        var currentWordStartMs = start
        return words.mapIndexed { wordIdx, word ->
            val len = word.trim().length.let { if (it > 0) it else word.length }.coerceAtLeast(1)
            val weight = len + 2
            val wordEndMs = if (wordIdx == words.lastIndex) {
                end
            } else {
                currentWordStartMs + (totalDuration * weight / totalWeight)
            }
            val wordTimestamp = WordTimestamp(
                text = word,
                startTime = currentWordStartMs / 1000.0,
                endTime = wordEndMs / 1000.0,
                isBackground = isBg,
            )
            currentWordStartMs = wordEndMs
            wordTimestamp
        }
    }

    fun resolveTtmlTimingMode(ttml: String): TtmlTimingMode {
        val match = TTML_TIMING_ATTR_REGEX.find(ttml)
        if (match != null) {
            val timingVal = match.groupValues[1]
            if (timingVal.equals("Word", ignoreCase = true)) return TtmlTimingMode.WORD
            if (timingVal.equals("Line", ignoreCase = true)) return TtmlTimingMode.LINE
        }
        return if (hasWordSyncedLyrics(ttml)) TtmlTimingMode.WORD else TtmlTimingMode.LINE
    }

    fun parseTtmlLyricOffset(ttml: String): Long {
        if (!RESPECT_TTML_LYRIC_OFFSET) return 0L
        val attrMatch = TTML_LYRIC_OFFSET_REGEX.find(ttml)
        val tagMatch = TTML_LYRIC_OFFSET_TAG_REGEX.find(ttml)
        val raw = attrMatch?.groupValues?.get(1) ?: tagMatch?.groupValues?.get(1) ?: return 0L
        return parseTimeMs(raw) ?: 0L
    }

    fun resolveSyncType(lyrics: String, providerName: String? = null): LyricsSyncType {
        val normalized = normalizeLyricsText(lyrics)
        if (normalized.isBlank() || normalized == LyricsEntity.LYRICS_NOT_FOUND) {
            return LyricsSyncType.PLAIN
        }
        val isTtmlDoc = isTtml(normalized)
        val hasTimestamps = isLineSyncedLrc(normalized) || isTtmlDoc || hasWordSyncedLyrics(normalized)
        if (!hasTimestamps || providerName.equals("Unison", ignoreCase = true) || providerName.equals("Genius", ignoreCase = true)) {
            return LyricsSyncType.PLAIN
        }
        val ttmlTimingMode = if (isTtmlDoc) resolveTtmlTimingMode(normalized) else null
        val hasRealWordTiming = if (isTtmlDoc) {
            ttmlTimingMode == TtmlTimingMode.WORD
        } else {
            hasWordSyncedLyrics(normalized)
        }
        if (hasRealWordTiming && (providerName == null ||
                providerName.equals("BetterLyrics", ignoreCase = true) ||
                providerName.equals("YouLyPlus", ignoreCase = true) ||
                providerName.equals("Apple Music", ignoreCase = true) ||
                providerName.equals("Syllable-Sync (Bini)", ignoreCase = true) ||
                providerName.equals("lrc.red", ignoreCase = true))) {
            return LyricsSyncType.WORD
        }
        return LyricsSyncType.LINE
    }

    fun isTtml(lyrics: String): Boolean {
        val trimmed = normalizeLyricsText(lyrics)
        if (!trimmed.startsWith("<")) return false

        return trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    fun isLineSyncedLrc(lyrics: String): Boolean {
        return lyrics.lineSequence().any { line ->
            val trimmedLine = line.trim()
            LINE_REGEX.matches(trimmedLine) || YRC_LINE_REGEX.matches(trimmedLine)
        }
    }

    fun hasWordSyncedLyrics(lyrics: String): Boolean {
        val normalized = normalizeLyricsText(lyrics)
        if (QRCParser.isQrc(normalized)) return QRCParser.hasWordTimings(normalized)
        if (isTtml(normalized)) {
            return TTML_SPAN_REGEX.findAll(normalized).any { match ->
                TTML_BEGIN_ATTRIBUTE_REGEX.containsMatchIn(match.value) &&
                    TTML_END_ATTRIBUTE_REGEX.containsMatchIn(match.value)
            }
        }

        return normalized.lineSequence().any(::hasEnhancedLrcWordTimings)
    }

    private fun hasEnhancedLrcWordTimings(line: String): Boolean {
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return false
        val content = matchResult.groupValues[3]
        val timingMatches = ENHANCED_LRC_WORD_TIME_CAPTURING_REGEX.findAll(content).toList()
        return timingMatches.indices.any { index ->
            val timingMatch = timingMatches[index]
            val textStart = timingMatch.range.last + 1
            val textEnd = timingMatches.getOrNull(index + 1)?.range?.first ?: content.length
            textStart < textEnd && content.substring(textStart, textEnd).isNotEmpty()
        }
    }

    fun parseTtml(
        lyrics: String,
        durationSeconds: Int? = null,
    ): List<LyricsEntry> {
        val normalized = normalizeLyricsText(lyrics)
        val timingMode = resolveTtmlTimingMode(normalized)
        val parsedLines = TTMLParser.parseTTML(normalized)
        if (parsedLines.isEmpty()) {
            return emptyList()
        }
        val scale = 1.0

        val result = parsedLines
            .map { line ->
                val isLineBg = line.isBackground || isLineInParentheses(line.text)
                val lineStartMs = (line.startTime * scale * 1000.0).roundToLong()
                val lineEndMs = (line.endTime * scale * 1000.0).roundToLong()

                val words = if (timingMode == TtmlTimingMode.LINE || line.words.isEmpty()) {
                    // For TTML lines use the provided begin AND end time directly.
                    // Do NOT apply the 120 ms/char cap or next-line-start logic, only the word weighting (length + 2).
                    splitWords(
                        start = lineStartMs,
                        end = lineEndMs,
                        text = line.text,
                        isBackground = isLineBg,
                    )
                } else {
                    // "Word" timing mode: use <span begin end> word timing directly
                    line.words
                        .filter { it.text.isNotEmpty() }
                        .map { word ->
                            val wordStart = (word.startTime * scale * 1000.0).roundToLong()
                            val wordEnd = (word.endTime * scale * 1000.0).roundToLong()
                            WordTimestamp(
                                text = word.text,
                                startTime = wordStart / 1000.0,
                                endTime = wordEnd / 1000.0,
                                isBackground = isLineBg || word.isBackground,
                            )
                        }
                }.takeIf { it.isNotEmpty() }

                LyricsEntry(
                    time = lineStartMs,
                    text = line.text,
                    words = words,
                    agent = line.agent,
                    durationMs = (lineEndMs - lineStartMs).coerceAtLeast(0L),
                    providerRomanizedText = line.providerRomanizedText,
                    providerRomanizedWords = line.providerRomanizedWords,
                    providerRomanizedLanguage = line.providerRomanizedLanguage,
                    providerTranslationText = line.providerTranslationText,
                )
            }.sorted()
        return result
    }

    fun parseLyrics(
        lyrics: String,
        trackDurationMs: Long = 0L,
        estimateWordTimings: Boolean = true,
    ): List<LyricsEntry> {
        val lines = lyrics.lines()
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val entries = parseLineSyncedLrcLine(line) ?: parseMillisecondsSyncedLine(line)
            if (entries != null) {
                result.addAll(entries)
            }
        }
        val nonBlank = result.filter { it.text.trim().isNotEmpty() }
        val merged = mergeLineSyncedTranslations(nonBlank).sorted()

        if (!estimateWordTimings || merged.isEmpty()) {
            return merged
        }

        return estimateWordTimings(merged, trackDurationMs)
    }

    fun estimateWordTimings(
        entries: List<LyricsEntry>,
        trackDurationMs: Long = 0L,
    ): List<LyricsEntry> {
        if (entries.isEmpty()) return emptyList()

        val validEntries = entries
            .filter { it.time >= 0L && it.text.trim().isNotEmpty() }
            .sortedBy { it.time }

        if (validEntries.isEmpty()) return entries

        return validEntries.mapIndexed { index, entry ->
            if (entry.isInstrumental || entry.words != null) {
                return@mapIndexed entry
            }

            val start = entry.time
            val text = entry.text.trim()
            val maxSing = (text.length * 120L).coerceIn(800L, 8000L)
            val nextStart = validEntries.getOrNull(index + 1)?.time
                ?: if (trackDurationMs > start) trackDurationMs else (start + maxSing + 80L)

            val end = minOf(nextStart - 80L, start + maxSing).coerceAtLeast(start + 300L)
            val isBg = isLineInParentheses(text)
            val wordTimestamps = splitWords(start, end, text, isBackground = isBg)

            entry.copy(
                words = wordTimestamps,
                durationMs = end - start,
            )
        }
    }

    fun normalizeLyricsText(lyrics: String): String {
        val raw =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == NBSP }

        val unwrapped = stripCodeFence(raw)
        val normalized =
            if (isEscapedTtml(unwrapped)) {
                unwrapped
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
            } else {
                unwrapped
            }

        return normalized.trim { it.isWhitespace() || it == NBSP }
    }

    fun displayLyricsText(lyrics: String): String {
        val raw = normalizeLyricsText(lyrics)
        if (raw.isEmpty() || raw == LyricsEntity.LYRICS_NOT_FOUND) return ""

        val visibleLines =
            when {
                isTtml(raw) -> runCatching { parseTtml(raw).map { it.text } }.getOrElse { emptyList() }
                isLineSyncedLrc(raw) -> runCatching { parseLyrics(raw).map { it.text } }.getOrElse { emptyList() }
                raw.startsWith("<") -> emptyList()
                else -> raw.lines().map(::cleanInlineWordTimingText)
            }

        return visibleLines
            .map { line ->
                line
                    .replace(WHITESPACE_REGEX, " ")
                    .trim { it.isWhitespace() || it == NBSP }
            }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun hasMeaningfulLyricsContent(lyrics: String): Boolean = displayLyricsText(lyrics).isNotEmpty()

    fun lyricsOrNotFound(lyrics: String): String {
        val normalized = normalizeLyricsText(lyrics)
        return normalized.takeIf(::hasMeaningfulLyricsContent) ?: LyricsEntity.LYRICS_NOT_FOUND
    }

    private fun stripCodeFence(lyrics: String): String {
        if (!lyrics.startsWith("```")) return lyrics

        val lines = lyrics.lines()
        if (lines.size <= 1) return lyrics

        val bodyLines =
            lines.drop(1).let { remainingLines ->
                if (remainingLines.lastOrNull()?.trim() == "```") {
                    remainingLines.dropLast(1)
                } else {
                    remainingLines
                }
            }

        return bodyLines.joinToString("\n").trim { it.isWhitespace() || it == NBSP }
    }

    private fun isEscapedTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("&lt;tt", ignoreCase = true) ||
            trimmed.contains("&lt;tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true) &&
            trimmed.contains("&lt;", ignoreCase = true)
    }

    fun insertInstrumentalBreaks(
        entries: List<LyricsEntry>,
        songDurationMs: Long = 0L,
    ): List<LyricsEntry> {
        if (entries.isEmpty()) return entries
        val result = mutableListOf<LyricsEntry>()
        insertIntroInstrumentalIfNeeded(entries, result)
        result.addAll(entries)
        insertOutroInstrumentalIfNeeded(entries, songDurationMs, result)
        return result
    }

    private fun insertIntroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        result: MutableList<LyricsEntry>,
    ) {
        val firstTimedVocalEntry = entries.firstOrNull { it.time >= 0L && it.text.isNotBlank() } ?: return
        val introGapMs = firstTimedVocalEntry.time - INSTRUMENTAL_INTRO_START_MS
        if (introGapMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = INSTRUMENTAL_INTRO_START_MS,
                text = "",
                isInstrumental = true,
                durationMs = introGapMs,
            ),
        )
    }

    private fun insertOutroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        songDurationMs: Long,
        result: MutableList<LyricsEntry>,
    ) {
        if (songDurationMs <= 0L) return
        val lastVocalEntry = entries.lastOrNull { it.text.isNotBlank() } ?: return
        val outroStartMs = lastVocalEntry.time + INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS
        val outroDurationMs = songDurationMs - outroStartMs
        if (outroDurationMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = outroStartMs,
                text = "",
                isInstrumental = true,
                durationMs = outroDurationMs,
            ),
        )
    }

    private fun parseLineSyncedLrcLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = cleanInlineWordTimingText(matchResult.groupValues[3])
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                val mil = if (milString.isEmpty()) {
                    0L
                } else {
                    milString.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text)
            }.toList()
    }

    private fun parseMillisecondsSyncedLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = YRC_LINE_REGEX.matchEntire(line.trim()) ?: return null
        val time = matchResult.groupValues[1].toLongOrNull() ?: return null
        val text = cleanInlineWordTimingText(matchResult.groupValues[2])
        return listOf(LyricsEntry(time, text))
    }

    private fun mergeLineSyncedTranslations(entries: List<LyricsEntry>): List<LyricsEntry> {
        val mergedByTime = linkedMapOf<Long, LyricsEntry>()
        entries.forEach { entry ->
            val existing = mergedByTime[entry.time]
            if (existing == null) {
                mergedByTime[entry.time] = entry
                return@forEach
            }

            val translatedText =
                entry.text
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                    .takeIf { it.isNotEmpty() && !it.equals(existing.text.trim(), ignoreCase = true) }

            if (translatedText != null && existing.providerTranslationText == null) {
                mergedByTime[entry.time] = existing.copy(providerTranslationText = translatedText)
            }
        }
        return mergedByTime.values.toList()
    }

    private fun cleanInlineWordTimingText(text: String): String =
        text
            .replace(ENHANCED_LRC_WORD_TIME_REGEX, "")
            .replace(INLINE_MILLISECONDS_TIME_REGEX, "")
            .replace(YRC_WORD_TIME_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim { it.isWhitespace() || it == NBSP }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 300L,
    ): Int {
        if (lines.isEmpty()) return -1

        val target = position + leadMs
        var low = 0
        var high = lines.lastIndex

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midTime = lines[mid].time

            if (midTime < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return high.coerceIn(0, lines.lastIndex)
    }
}
