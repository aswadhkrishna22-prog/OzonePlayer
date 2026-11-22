/*
 * Ozone Player (2026)
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.betterlyrics

object TTMLParser {
    /**
     * Clearly named constant/flag to respect the lyricOffset value in TTML metadata.
     * OFF (false) by default so developers can test whether begin times already include it.
     */
    var RESPECT_TTML_LYRIC_OFFSET: Boolean = false

    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val words: List<ParsedWord>,
        val isBackground: Boolean = false,
        val agent: String? = null,
        val providerRomanizedText: String? = null,
        val providerRomanizedWords: List<String>? = null,
        val providerRomanizedLanguage: String? = null,
        val providerTranslationText: String? = null,
        val timingMode: TtmlTimingMode = TtmlTimingMode.WORD,
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val isBackground: Boolean = false,
    )

    fun parseDocument(ttml: String): Result<TtmlDocument> =
        runCatching {
            val source = ttml.removePrefix("\uFEFF").trimStart()
            if (UNSAFE_XML_REGEX.containsMatchIn(source)) {
                throw TtmlParseException(TtmlParseFailure.UNSAFE_XML)
            }

            val document = TtmlXmlUtils.parseXml(TtmlXmlUtils.declareMissingNamespaces(source))
            val root = document.documentElement ?: throw TtmlParseException(TtmlParseFailure.MALFORMED_XML)
            if (!root.hasLocalName("tt")) throw TtmlParseException(TtmlParseFailure.MALFORMED_XML)

            val timingContext = TtmlTimeParser.readTimingContext(root)
            val body = root.descendantElements().firstOrNull { it.hasLocalName("body") }
            val bodyStartMs = body?.attribute("begin")?.let { TtmlTimeParser.parseTime(it, timingContext) } ?: 0L
            val bodyEndMs =
                body?.attribute("end")?.let { TtmlTimeParser.parseTime(it, timingContext) }
                    ?: body?.attribute("dur")?.let { TtmlTimeParser.parseTime(it, timingContext) }?.let(bodyStartMs::plus)
            val declaredAgents = TtmlSpanHandler.parseAgents(root)
            val headRomanizations = TtmlSpanHandler.parseHeadTracks(root, "transliteration", timingContext)
            val headTranslations = TtmlSpanHandler.parseHeadTracks(root, "translation", timingContext)
            val rawLines = TtmlSpanHandler.parseRawLines(root, timingContext, headTranslations, headRomanizations)
            if (rawLines.isEmpty()) throw TtmlParseException(TtmlParseFailure.MISSING_LINES)

            val agents = TtmlSpanHandler.resolveUnknownAgents(declaredAgents, rawLines)
            val rawResolvedLines = TtmlSpanHandler.resolveLines(rawLines, bodyEndMs, agents.associateBy(TtmlAgent::id))
            if (rawResolvedLines.isEmpty()) throw TtmlParseException(TtmlParseFailure.INVALID_TIMING)

            val lyricOffsetMs = extractLyricOffset(root)
            val effectiveOffsetMs = if (RESPECT_TTML_LYRIC_OFFSET) (lyricOffsetMs ?: 0L) else 0L

            val lines = if (effectiveOffsetMs != 0L) {
                rawResolvedLines.map { line ->
                    line.copy(
                        timing = TtmlTimeRange(
                            startMs = line.timing.startMs + effectiveOffsetMs,
                            endMs = line.timing.endMs + effectiveOffsetMs,
                        )
                    )
                }
            } else {
                rawResolvedLines
            }

            val hasTimedSegments =
                lines.any { line ->
                    line.main.segments.any { it.timing != null } ||
                        line.backgrounds.any { track -> track.segments.any { it.timing != null } }
                }

            val declaredTiming = root.attribute("timing") ?: root.attribute("itunes:timing")
            val timingMode = when {
                declaredTiming?.equals("Word", ignoreCase = true) == true -> TtmlTimingMode.WORD
                declaredTiming?.equals("Line", ignoreCase = true) == true -> TtmlTimingMode.LINE
                hasTimedSegments -> TtmlTimingMode.WORD
                else -> TtmlTimingMode.LINE
            }

            TtmlDocument(
                language = root.attribute("lang"),
                timingMode = timingMode,
                agents = agents,
                lines = lines.sortedWith(compareBy<TtmlLine> { it.timing.startMs }.thenBy { it.sourceOrder }),
                lyricOffsetMs = lyricOffsetMs,
            )
        }.recoverCatching { throwable ->
            if (throwable is TtmlParseException) throw throwable
            throw TtmlParseException(TtmlParseFailure.MALFORMED_XML, throwable)
        }

    fun extractLyricOffset(root: org.w3c.dom.Element): Long? {
        root.descendantElements().firstOrNull { it.hasLocalName("audio") }?.let { audio ->
            val offsetStr = audio.attribute("lyricOffset") ?: audio.attribute("itunes:lyricOffset")
            if (offsetStr != null) {
                return TtmlTimeParser.parseTimeOffset(offsetStr)
            }
        }
        root.descendantElements().firstOrNull { it.hasLocalName("lyricOffset") }?.let { elem ->
            val text = elem.textContent?.trim()
            if (!text.isNullOrEmpty()) {
                return TtmlTimeParser.parseTimeOffset(text)
            }
        }
        root.descendantElements().firstOrNull { elem ->
            elem.hasLocalName("meta") && (
                elem.attribute("name")?.equals("lyricOffset", ignoreCase = true) == true ||
                elem.attribute("itunes:name")?.equals("lyricOffset", ignoreCase = true) == true
            )
        }?.let { meta ->
            meta.attribute("content")?.let { content ->
                return TtmlTimeParser.parseTimeOffset(content)
            }
        }
        val rootOffset = root.attribute("lyricOffset") ?: root.attribute("itunes:lyricOffset")
        if (rootOffset != null) {
            return TtmlTimeParser.parseTimeOffset(rootOffset)
        }
        root.descendantElements().firstOrNull { it.hasLocalName("metadata") || it.hasLocalName("head") }?.let { elem ->
            val offset = elem.attribute("lyricOffset") ?: elem.attribute("itunes:lyricOffset")
            if (offset != null) {
                return TtmlTimeParser.parseTimeOffset(offset)
            }
        }
        return null
    }

    fun parseTTML(ttml: String): List<ParsedLine> =
        parseDocument(ttml)
            .getOrNull()
            ?.let { doc ->
                val offsetSeconds = if (RESPECT_TTML_LYRIC_OFFSET) ((doc.lyricOffsetMs ?: 0L) / TtmlTimeParser.MILLIS_PER_SECOND) else 0.0
                doc.lines.map { line ->
                    val mainWords =
                        line.main.segments.mapNotNull { segment ->
                            segment.timing?.let { timing ->
                                ParsedWord(
                                    text = segment.text,
                                    startTime = (timing.startMs / TtmlTimeParser.MILLIS_PER_SECOND) + offsetSeconds,
                                    endTime = (timing.endMs / TtmlTimeParser.MILLIS_PER_SECOND) + offsetSeconds,
                                )
                            }
                        }
                    val backgroundWords =
                        line.backgrounds.flatMap { track ->
                            track.segments.mapNotNull { segment ->
                                segment.timing?.let { timing ->
                                    ParsedWord(
                                        text = segment.text,
                                        startTime = (timing.startMs / TtmlTimeParser.MILLIS_PER_SECOND) + offsetSeconds,
                                        endTime = (timing.endMs / TtmlTimeParser.MILLIS_PER_SECOND) + offsetSeconds,
                                        isBackground = true,
                                    )
                                }
                            }
                        }
                    val romanization = TtmlSpanHandler.chooseRomanization(line.romanizations)

                    ParsedLine(
                        text = line.text,
                        startTime = (line.timing.startMs / TtmlTimeParser.MILLIS_PER_SECOND),
                        endTime = (line.timing.endMs / TtmlTimeParser.MILLIS_PER_SECOND),
                        words = if (doc.timingMode == TtmlTimingMode.WORD) (mainWords + backgroundWords) else emptyList(),
                        isBackground = line.backgrounds.isNotEmpty() && line.main.text.isBlank(),
                        agent = line.agent?.id,
                        providerRomanizedText = romanization?.text?.normalizedText(),
                        providerRomanizedWords =
                            romanization
                                ?.segments
                                ?.map { it.text.normalizedText() }
                                ?.filter(String::isNotEmpty)
                                ?.takeIf { words -> words.isNotEmpty() },
                        providerRomanizedLanguage = romanization?.language,
                        providerTranslationText = line.translations.firstOrNull()?.text?.normalizedText(),
                        timingMode = doc.timingMode,
                    )
                }
            }.orEmpty()

    fun parse(ttml: String): List<ParsedLine> = parseTTML(ttml)
}
