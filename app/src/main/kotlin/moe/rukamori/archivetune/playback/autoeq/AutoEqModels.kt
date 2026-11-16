/*
 * Ozone Player (2026)
 * AutoEq Headphone Correction Models
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.autoeq

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AutoEqFilterType {
    @SerialName("PK")
    PEAK,
    @SerialName("LSC")
    LOW_SHELF,
    @SerialName("HSC")
    HIGH_SHELF,
}

@Serializable
data class AutoEqFilter(
    @SerialName("type")
    val type: AutoEqFilterType,
    @SerialName("freqHz")
    val freqHz: Float,
    @SerialName("gainDb")
    val gainDb: Float,
    @SerialName("q")
    val q: Float = 0.7071f,
)

@Serializable
data class AutoEqProfile(
    @SerialName("name")
    val name: String,
    @SerialName("source")
    val source: String,
    @SerialName("preampDb")
    val preampDb: Float,
    @SerialName("filters")
    val filters: List<AutoEqFilter> = emptyList(),
)

@Serializable
data class AutoEqEntry(
    @SerialName("name")
    val name: String,
    @SerialName("source")
    val source: String,
    @SerialName("path")
    val path: String,
)
