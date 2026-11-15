/*
 * Ozone Player (2026)
 * AutoEq Headphone Correction Index & Profile Parser
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.autoeq

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale

object AutoEqParser {
    private const val BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
    private val INDEX_LINE = Regex("""^-\s*\[(.*?)\]\((\./.*?)\)\s+by\s+(.*?)$""")
    private val PREAMP = Regex("""(?m)^Preamp:\s*(-?[\d.]+)\s*dB""")
    private val FILTER = Regex("""(?m)^Filter\s+\d+:\s+ON\s+([A-Z]+)\s+Fc\s+([\d.]+)\s+Hz\s+Gain\s+(-?[\d.]+)\s+dB\s+Q\s+([\d.]+)""")

    /**
     * Parses markdown index entries from AutoEq's results INDEX.md.
     * Line format: `- [Sony WH-1000XM4](./oratory1990/harman_over-ear_2018/Sony%20WH-1000XM4) by oratory1990 on Harman over-ear 2018`
     */
    fun parseIndex(text: String): List<AutoEqEntry> {
        return text.lineSequence().mapNotNull { line ->
            val match = INDEX_LINE.find(line.trim()) ?: return@mapNotNull null
            val modelName = match.groupValues[1].trim()
            val path = match.groupValues[2].trim()
            val source = match.groupValues[3].substringBefore(" on ").trim()
            if (modelName.isNotEmpty() && path.isNotEmpty()) {
                AutoEqEntry(name = modelName, source = source, path = path)
            } else {
                null
            }
        }.toList()
    }

    /**
     * Builds the raw GitHub URL for downloading a model's ParametricEQ.txt file.
     */
    fun profileUrl(entry: AutoEqEntry): String {
        val segments = entry.path.removePrefix("./").split('/').map {
            URLDecoder.decode(it.replace("+", "%2B"), "UTF-8")
        }
        val builder = BASE.toHttpUrl().newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        val modelFileName = "${segments.last()} ParametricEQ.txt"
        builder.addPathSegment(modelFileName)
        return builder.build().toString()
    }

    /**
     * Parses AutoEq's ParametricEQ.txt content into an AutoEqProfile.
     * Contains `Preamp: -6.1 dB` followed by lines like:
     * `Filter 1: ON PK Fc 105 Hz Gain 6.4 dB Q 0.70`
     * `Filter 2: ON LSC Fc 105 Hz Gain 5.2 dB Q 0.70`
     */
    fun parseParametric(text: String, name: String, source: String): AutoEqProfile {
        val preampMatch = PREAMP.find(text)
        val preampDb = preampMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

        val filters = FILTER.findAll(text).mapNotNull { match ->
            val typeStr = match.groupValues[1].uppercase(Locale.ROOT)
            val type = when (typeStr) {
                "PK" -> AutoEqFilterType.PEAK
                "LSC", "LS" -> AutoEqFilterType.LOW_SHELF
                "HSC", "HS" -> AutoEqFilterType.HIGH_SHELF
                else -> return@mapNotNull null
            }
            val freq = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            val gain = match.groupValues[3].toFloatOrNull() ?: return@mapNotNull null
            val q = match.groupValues[4].toFloatOrNull() ?: 0.7071f
            AutoEqFilter(type = type, freqHz = freq, gainDb = gain, q = q)
        }.toList()

        return AutoEqProfile(
            name = name,
            source = source,
            preampDb = preampDb,
            filters = filters,
        )
    }

    /**
     * Case-insensitive multi-token search over a list of AutoEqEntry objects.
     * E.g. "sony 1000xm4" matches "Sony WH-1000XM4".
     */
    fun search(entries: List<AutoEqEntry>, query: String, limit: Int = 60): List<AutoEqEntry> {
        val tokens = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { entry ->
                val lower = entry.name.lowercase(Locale.ROOT)
                tokens.all { token -> lower.contains(token) }
            }
            .sortedBy { it.name.length }
            .take(limit)
            .toList()
    }
}
