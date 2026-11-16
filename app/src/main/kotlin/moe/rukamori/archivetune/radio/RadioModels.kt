/*
 * Ozone Player (2026)
 * Internet Radio Models & Station Definitions
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.radio

import kotlinx.serialization.Serializable

@Serializable
data class RadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String? = null,
    val homepage: String? = null,
    val tags: List<String> = emptyList(),
    val country: String? = null,
    val countryCode: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
    val hls: Boolean = false,
    val state: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val id: String get() = "radio:$uuid"

    val isCustom: Boolean get() = uuid.startsWith("custom-")

    val frequency: String? get() = extractFrequency(name)

    val details: String
        get() = listOfNotNull(
            frequency,
            "Custom Station".takeIf { isCustom },
            codec?.takeIf { it.isNotBlank() && it != "UNKNOWN" },
            bitrate.takeIf { it > 0 }?.let { "$it kbps" },
            country?.takeIf { it.isNotBlank() },
            tags.take(3).joinToString(", ").takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    companion object {
        private val DECIMAL_FM = Regex("""(?<![\d.])(8[7-9]|9\d|10[0-8])[.,](\d)(?![\d.])""")
        private val WHOLE_FM = Regex("""(?<![\d.])(8[7-9]|9\d|10[0-8])\s?(?:FM|MHz)\b""", RegexOption.IGNORE_CASE)

        fun extractFrequency(name: String): String? =
            DECIMAL_FM.find(name)?.let { "${it.groupValues[1]}.${it.groupValues[2]} FM" }
                ?: WHOLE_FM.find(name)?.let { "${it.groupValues[1]} FM" }
    }
}

data class RadioCategory(
    val title: String,
    val tag: String,
)
