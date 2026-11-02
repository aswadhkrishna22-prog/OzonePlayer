/*
 * Ozone Player (2026) | Border Beam Search Bar Effect
 * GNU General Public License v3.0
 */

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── CONSTANTS ─────────────────────────────────────────────────────────────────
const val DEFAULT_BORDER_BEAM_DURATION_MILLIS: Int = 3000
const val DEFAULT_BORDER_BEAM_FRACTION: Float = 0.22f
val DEFAULT_BORDER_BEAM_STROKE_WIDTH: Dp = 2.dp
const val DEFAULT_BORDER_BEAM_SEGMENTS: Int = 24

fun Modifier.borderBeam(
    enabled: Boolean,
    shape: Shape,
    head: Color,
    tail: Color,
    strokeWidth: Dp = DEFAULT_BORDER_BEAM_STROKE_WIDTH,
    durationMillis: Int = DEFAULT_BORDER_BEAM_DURATION_MILLIS,
    beamFraction: Float = DEFAULT_BORDER_BEAM_FRACTION,
    segments: Int = DEFAULT_BORDER_BEAM_SEGMENTS,
): Modifier = composed {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val durationScale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
    val isPowerSave = powerManager?.isPowerSaveMode == true
    val effectivelyEnabled = enabled && durationScale != 0f && !isPowerSave

    val visible by animateFloatAsState(
        targetValue = if (effectivelyEnabled) 1f else 0f,
        animationSpec = tween(250),
        label = "beamAlpha",
    )
    if (!effectivelyEnabled && visible == 0f) return@composed this
    val pos = rememberInfiniteTransition(label = "beam").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "beamPos",
    )
    drawWithCache {
        val sw = strokeWidth.toPx()
        val outline = shape.createOutline(Size(size.width - sw, size.height - sw), layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val measure = PathMeasure().apply { setPath(path, true) }
        val total = measure.length
        val seg = Path()
        onDrawWithContent {
            drawContent()
            val beamLen = total * beamFraction
            val headPos = pos.value * total
            translate(sw / 2f, sw / 2f) {
                for (i in 0 until segments) {
                    val t1 = (i + 1f) / segments
                    var a = headPos - beamLen * (1f - i.toFloat() / segments)
                    var b = headPos - beamLen * (1f - t1)
                    if (a < 0f) {
                        a += total
                        b += total
                    }
                    seg.reset()
                    if (b <= total) {
                        measure.getSegment(a, b, seg, true)
                    } else {
                        measure.getSegment(a, total, seg, true)
                        measure.getSegment(0f, b - total, seg, true)
                    }
                    drawPath(seg, lerp(tail, head, t1).copy(alpha = t1 * visible), style = Stroke(sw))
                }
            }
        }
    }
}
