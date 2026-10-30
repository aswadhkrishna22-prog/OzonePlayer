/*
 * Ozone Player (2026) | Dual Play — Channel Mode
 * GNU General Public License v3.0
 */
package moe.rukamori.archivetune.playback.dualplay

/**
 * Shared-state flag for the channel-split processors.
 *
 * Both processors are always active in the sink chain.
 * When [mode] is [OFF] they pass audio through unchanged
 * (no seek / re-prepare needed for a toggle).
 *
 * [LEFT]  → Player A's processor keeps L = mono-mix, zeros R.
 * [RIGHT] → Player B's processor keeps R = mono-mix, zeros L.
 */
enum class ChannelMode { OFF, LEFT, RIGHT }

object DualPlayChannelState {
    /** Written on the main thread; read on the audio thread. @Volatile is sufficient. */
    @Volatile
    var mode: ChannelMode = ChannelMode.OFF

    /**
     * Human-readable description of the last format that was configured on
     * either processor, for the debug screen's "channel split not applied" banner.
     * null = applied; non-null = reason it was not applied.
     */
    @Volatile
    var unsupportedFormatDescription: String? = null
}
