/*
 * Ozone Player (2026) | Dual Play — Channel Mode
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.playback.dualplay

/**
 * Shared-state flag for the channel-split processors.
 *
 * Both processors are ALWAYS active in the sink chain simultaneously.
 * When [mode] is [OFF] they pass audio through unchanged.
 *
 * [ACTIVE]  → Normal assignment: LEFT processor → left ear only; RIGHT processor → right ear only.
 * [SWAPPED] → Swapped assignment: LEFT processor → right ear only; RIGHT processor → left ear only.
 * [OFF]     → Both processors passthrough unchanged (Dual Play disabled).
 *
 * The [assignedSide] field on each [ChannelSplitProcessor] determines which side it handles.
 * This enum only controls whether splitting is on and which assignment direction.
 */
enum class ChannelMode { OFF, ACTIVE, SWAPPED }

/** Identifies which physical channel a [ChannelSplitProcessor] instance manages. */
enum class ProcessorSide { LEFT, RIGHT }

object DualPlayChannelState {
    /** Written on the main thread; read on the audio thread. @Volatile is sufficient. */
    @Volatile
    var mode: ChannelMode = ChannelMode.OFF

    /**
     * True if Android system-level "Mono Audio" is ON (Accessibility setting).
     * When true, the OS merges L+R before output — channel separation WON'T work.
     */
    @Volatile
    var isSystemMonoAudioEnabled: Boolean = false

    /**
     * Human-readable description of the last format that was configured on
     * either processor, for the debug screen's "channel split not applied" banner.
     * null = applied; non-null = reason it was not applied.
     */
    @Volatile
    var unsupportedFormatDescription: String? = null
}
