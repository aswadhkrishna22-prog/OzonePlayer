/*
 * Ozone Player (2026)
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

@Immutable
data class HeroItem(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
)

sealed interface HeroDisplayItem {
    val key: String

    @Immutable
    data class Track(val item: HeroItem) : HeroDisplayItem {
        override val key: String get() = "hero_track_${item.id}"
    }

    @Immutable
    data object RollCard : HeroDisplayItem {
        override val key: String get() = "hero_roll_card"
    }
}

enum class HeroRollPhase {
    Idle,
    Rolling,
    Landed,
}

@Immutable
class HeroRollTint(
    val light: Color,
    val deep: Color,
)

@Stable
class HeroRollState {
    var candidates by mutableStateOf<List<HeroItem>>(emptyList())
    var phase by mutableStateOf(HeroRollPhase.Idle)
    var result by mutableStateOf<HeroItem?>(null)
    var completionToken by mutableLongStateOf(0L)

    val reel: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val pointerKick: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val landPop: Animatable<Float, AnimationVector1D> = Animatable(1f)
    val burst: Animatable<Float, AnimationVector1D> = Animatable(0f)

    val tints = mutableStateMapOf<String, HeroRollTint>()
}
