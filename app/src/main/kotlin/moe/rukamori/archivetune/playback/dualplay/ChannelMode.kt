/*
 * Ozone Player (2026) | Dual Play — Channel Mode
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.playback.dualplay

/**
 * High-level Dual Play on/off flag (legacy, kept for compatibility).
 * Actual per-ear routing is controlled by [DualPlayChannelState.modeA] / [DualPlayChannelState.modeB].
 */
enum class ChannelMode { OFF, ACTIVE, SWAPPED }

/** Identifies which physical channel a [ChannelSplitProcessor] instance manages. */
enum class ProcessorSide { LEFT, RIGHT }

/**
 * Per-processor ear routing mode.
 *  LEFT_ONLY  → output mono-mix in L channel only; R = 0
 *  RIGHT_ONLY → output mono-mix in R channel only; L = 0
 *  STEREO     → passthrough (both channels unchanged)
 */
enum class EarMode { LEFT_ONLY, RIGHT_ONLY, STEREO }

/**
 * UI-facing ear selection for each pane.
 *  Pane A shows: R, M   (L is the default / no-button-selected state)
 *  Pane B shows: L, M   (R is the default / no-button-selected state)
 */
enum class EarSelection { L, R, M }

object DualPlayChannelState {
    /** Master on/off. When OFF both processors passthrough. */
    @Volatile var mode: ChannelMode = ChannelMode.OFF

    /**
     * Per-processor ear modes, read on the audio thread.
     * Written on the main thread — @Volatile is sufficient.
     *
     * modeA → Player A (ProcessorSide.LEFT)
     * modeB → Player B (ProcessorSide.RIGHT)
     */
    @Volatile var modeA: EarMode = EarMode.STEREO
    @Volatile var modeB: EarMode = EarMode.STEREO

    /**
     * True if Android system-level "Mono Audio" is ON.
     * When true the OS merges L+R — channel separation won't work.
     */
    @Volatile var isSystemMonoAudioEnabled: Boolean = false

    /**
     * Non-null = unsupported audio format message for the debug screen banner.
     */
    @Volatile var unsupportedFormatDescription: String? = null
}
