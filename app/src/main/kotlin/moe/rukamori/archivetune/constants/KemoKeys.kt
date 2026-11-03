package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.stringPreferencesKey

enum class KemoDisplayMode {
    OFF,
    ALWAYS,
    WHILE_MUSIC_PLAYS,
}

val KemoDisplayModeKey = stringPreferencesKey("kemo_display_mode")
