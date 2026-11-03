package moe.rukamori.archivetune.constants

import androidx.datastore.preferences.core.stringPreferencesKey

enum class KemoDisplayMode {
    OFF,
    ALWAYS,
    WHILE_MUSIC_PLAYS,
}

enum class KemoSize(val scale: Int) {
    SMALL(1),
    NORMAL(2),
    LARGE(3),
}

val KemoDisplayModeKey = stringPreferencesKey("kemo_display_mode")
val KemoSizeKey = stringPreferencesKey("kemo_size")
