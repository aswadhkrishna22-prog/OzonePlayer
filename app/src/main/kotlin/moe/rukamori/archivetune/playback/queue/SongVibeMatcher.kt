/*
 * Ozone Player (2026) | Smart Vibe & Energy Queue Engine
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.playback.queue

import android.content.Context
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.SmartVibeQueueKey
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import java.util.Locale

enum class SongVibe {
    POWER_DANCE,
    LOVE_ROMANTIC,
    EMOTIONAL_SAD,
    NEUTRAL
}

object SongVibeMatcher {

    @Volatile
    var isEnabled: Boolean = true

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext
        scope.launch {
            appContext.dataStore.data
                .map { it[SmartVibeQueueKey] ?: true }
                .distinctUntilChanged()
                .collect { enabled ->
                    isEnabled = enabled
                }
        }
    }

    private val POWER_KEYWORDS = listOf(
        "remix", "beat", "beats", "dance", "bass", "trap", "drill", "phonk", "party", "club",
        "kuthu", "dhol", "mass", "power", "anthem", "electronic", "hype", "energy", "pump", "fast",
        "bgm", "rap", "hip hop", "rock", "metal", "dj", "hardstyle", "dubstep", "psytrance",
        "bass boosted", "speed up", "edits", "theme", "badass", "hukum", "vaathi", "naa ready",
        "kadharalz", "arabic kuthu", "aluma doluma", "aaluma doluma", "thee thalapathy", "whistle podu",
        "bloody sweet", "ordinary person", "lokiverse", "vikram", "leo", "jailer", "goat", "devara",
        "daavudi", "chuttamalle", "illuminati", "jaada", "scene mone", "galatta", "arm", "manjummel",
        "neela nilave", "kudukku", "jimikki", "pala palli", "thallumaala", "ole melody", "tupathu",
        "daku", "katchi sera", "aasa kooda", "tauba tauba", "jalabulajangu", "dippam", "chellama",
        "private party", "rowdy", "marana", "chaleya", "jawan", "zinda banda", "naatu naatu", "dosti",
        "komuram", "bheemudo", "raaghavan", "shoorveer", "aarambham", "fighter", "animal", "arjan vailly",
    )

    private val LOVE_KEYWORDS = listOf(
        "love", "romantic", "romance", "melody", "duet", "acoustic", "lofi", "slowed", "sweet",
        "premam", "kadhal", "kaadhal", "ishq", "pyar", "dil", "mohabbat", "unplugged", "vaseegara",
        "nee paartha", "idhazhin oram", "maruvaarthai", "thalli pogathey", "hosanna", "enodu nee irundhal",
        "kurumugil", "darshana", "aaradhike", "kannazhaga", "anbil avan", "uyire", "kaathalae",
        "ennodu nee", "kesariya", "ranjha", "raataan lambiyan", "pehla nasha", "tum hi ho", "hawayein",
        "apna bana le", "teree sang", "samjhawan", "kaun tujhe", "jeena jeena", "sun saathiya", "gerua",
        "khairiyat", "chal ghar chalen", "malare", "aaromale", "anuragathin", "kiliye", "mazhaye",
        "valibam", "ishqam", "ishqan",
    )

    private val SAD_KEYWORDS = listOf(
        "sad", "pain", "heartbreak", "breakup", "crying", "tears", "alone", "dard", "judaai", "alvida",
        "sorrow", "death", "goodbye", "melancholy", "depressed", "porkanda singam", "kanave kanave",
        "po nee po", "channa mereya", "marakkuma nenjam", "agar tum saath ho", "rathamaarey",
        "mother song", "amma", "appaa", "aararo", "lullaby", "tear", "lonely", "broken", "grief",
        "hamari adhuri kahani", "bekhayali", "tujhe kitna chahne lage", "phir bhi tumko chaahunga",
        "bikhra", "qaafirana", "luka chuppi", "vida", "mourning",
    )

    fun detectVibe(title: String?, artist: String? = null): SongVibe {
        if (title.isNullOrBlank()) return SongVibe.NEUTRAL
        val text = "$title ${artist.orEmpty()}".lowercase(Locale.ROOT)

        var powerScore = 0
        var loveScore = 0
        var sadScore = 0

        for (kw in POWER_KEYWORDS) {
            if (text.contains(kw)) {
                powerScore += if (kw.length > 5) 2 else 1
            }
        }
        for (kw in LOVE_KEYWORDS) {
            if (text.contains(kw)) {
                loveScore += if (kw.length > 5) 2 else 1
            }
        }
        for (kw in SAD_KEYWORDS) {
            if (text.contains(kw)) {
                sadScore += if (kw.length > 5) 2 else 1
            }
        }

        val maxScore = maxOf(powerScore, loveScore, sadScore)
        if (maxScore == 0) return SongVibe.NEUTRAL

        return when {
            powerScore == maxScore -> SongVibe.POWER_DANCE
            loveScore == maxScore -> SongVibe.LOVE_ROMANTIC
            sadScore == maxScore -> SongVibe.EMOTIONAL_SAD
            else -> SongVibe.NEUTRAL
        }
    }

    fun detectVibe(metadata: MediaMetadata?): SongVibe {
        if (metadata == null) return SongVibe.NEUTRAL
        val artists = metadata.artists.joinToString(separator = " ") { it.name }
        return detectVibe(metadata.title, artists)
    }

    fun detectVibe(mediaItem: MediaItem): SongVibe {
        val meta = mediaItem.metadata
        if (meta != null) return detectVibe(meta)
        val title = mediaItem.mediaMetadata.title?.toString()
        val artist = mediaItem.mediaMetadata.artist?.toString()
        return detectVibe(title, artist)
    }

    fun getCompatibilityScore(vibe: SongVibe, targetVibe: SongVibe): Int =
        when (targetVibe) {
            SongVibe.POWER_DANCE -> when (vibe) {
                SongVibe.POWER_DANCE -> 100
                SongVibe.NEUTRAL -> 50
                SongVibe.LOVE_ROMANTIC -> 20
                SongVibe.EMOTIONAL_SAD -> 0
            }
            SongVibe.LOVE_ROMANTIC -> when (vibe) {
                SongVibe.LOVE_ROMANTIC -> 100
                SongVibe.NEUTRAL -> 50
                SongVibe.EMOTIONAL_SAD -> 30
                SongVibe.POWER_DANCE -> 0
            }
            SongVibe.EMOTIONAL_SAD -> when (vibe) {
                SongVibe.EMOTIONAL_SAD -> 100
                SongVibe.LOVE_ROMANTIC -> 60
                SongVibe.NEUTRAL -> 40
                SongVibe.POWER_DANCE -> 0
            }
            SongVibe.NEUTRAL -> 50
        }

    fun reorderQueue(
        items: List<MediaItem>,
        seed: MediaMetadata?,
        isBeastMode: Boolean = false,
        enabled: Boolean = isEnabled,
    ): List<MediaItem> {
        if (!enabled && !isBeastMode) return items
        if (items.size <= 1) return items

        val targetVibe = when {
            isBeastMode -> SongVibe.POWER_DANCE
            seed != null -> {
                val detected = detectVibe(seed)
                if (detected == SongVibe.NEUTRAL) return items
                detected
            }
            else -> return items
        }

        val seedId = seed?.id ?: items.firstOrNull()?.mediaId
        val pinnedItem = if (seedId != null) items.firstOrNull { it.mediaId == seedId } else null
        val remaining = if (pinnedItem != null) items.filter { it.mediaId != seedId } else items

        val sortedRemaining = remaining.sortedByDescending { item ->
            val vibe = detectVibe(item)
            getCompatibilityScore(vibe, targetVibe)
        }

        return if (pinnedItem != null) {
            listOf(pinnedItem) + sortedRemaining
        } else {
            sortedRemaining
        }
    }
}
