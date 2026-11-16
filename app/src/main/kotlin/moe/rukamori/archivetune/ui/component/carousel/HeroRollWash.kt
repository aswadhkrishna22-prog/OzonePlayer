/*
 * Ozone Player (2026)
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

@Composable
internal fun HeroRollWashBackground(
    state: HeroRollState,
    unit: Float,
    focusPx: Float,
    pageOffset: () -> Float,
    isCarouselActive: Boolean,
    animationsDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val light = remember { mutableStateOf(HeroCarouselTokens.GachaDefaultLight) }
    val deep = remember { mutableStateOf(HeroCarouselTokens.GachaDefaultDeep) }
    val clock = remember { mutableFloatStateOf(0f) }
    val speed = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state, state.candidates, isCarouselActive, animationsDisabled) {
        if (animationsDisabled || !isCarouselActive) return@LaunchedEffect
        var lastNanos = 0L
        var lastReel = state.reel.value
        var idleTime = 0f
        var wasBusy = false
        while (isActive) {
            if (abs(pageOffset()) >= 0.999f || !isCarouselActive) {
                lastNanos = 0L
                snapshotFlow { isCarouselActive && abs(pageOffset()) < 0.999f }.first { it }
                continue
            }
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
                lastNanos = now
                val reel = state.reel.value
                val instant = abs(reel - lastReel) / dt.coerceAtLeast(1e-3f)
                speed.floatValue += (instant - speed.floatValue) * (1f - exp(-dt / 0.12f))
                lastReel = reel

                val currentCandidates = state.candidates
                val count = currentCandidates.size
                val target = if (count == 0) {
                    HeroRollTint(HeroCarouselTokens.GachaDefaultLight, HeroCarouselTokens.GachaDefaultDeep)
                } else {
                    val base = floor(reel).toInt()
                    val between = reel - base
                    val from = state.tints[currentCandidates[base.mod(count)].id]
                        ?: HeroRollTint(HeroCarouselTokens.GachaDefaultLight, HeroCarouselTokens.GachaDefaultDeep)
                    val to = state.tints[currentCandidates[(base + 1).mod(count)].id] ?: from
                    HeroRollTint(lerp(from.light, to.light, between), lerp(from.deep, to.deep, between))
                }

                val busy = speed.floatValue > 0.05f || state.burst.value > 0f
                idleTime += dt
                if (busy || idleTime >= HeroCarouselTokens.IdleStepSeconds) {
                    val step = if (busy) dt else idleTime
                    idleTime = 0f
                    val follow = 1f - exp(-step / 0.22f)
                    light.value = lerp(light.value, target.light, follow)
                    deep.value = lerp(deep.value, target.deep, follow)
                    clock.floatValue += step
                }
                wasBusy = busy
            }
            if (!wasBusy) delay(HeroCarouselTokens.IdleWakeMillis)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val overhangPx = (unit * HeroCarouselTokens.GachaOverhang).dp.toPx()
                val glowX = w + overhangPx - focusPx / 2f
                val glowY = (unit * HeroCarouselTokens.GachaCenterY).dp.toPx()

                val washPaint = Paint().apply { isDither = true }
                val wanderPaint = Paint().apply { isDither = true }
                val glowPaint = Paint().apply { isDither = true }
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
                val matrix = Matrix()

                onDrawBehind {
                    val lit = light.value
                    val dark = deep.value
                    val t = clock.floatValue
                    val rush = (speed.floatValue / 30f).coerceIn(0f, 1f)
                    val win = state.burst.value

                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val sway = sin(t * 0.35f) * w * 0.12f
                        val x0 = w * 0.85f + sway
                        val dx = (w * 0.15f - sway) - x0
                        val dy = h
                        val wanderX = w * (0.18f + 0.08f * sin(t * 0.23f))
                        val wanderY = h * (0.55f + 0.1f * sin(t * 0.31f + 1.3f))
                        val wanderR = w * 0.7f

                        val breathe = 0.05f * sin(t * 1.4f)
                        val flare = if (win > 0f) sin(win * PI.toFloat()) * 0.55f else 0f
                        val glowR = focusPx * (1.05f + breathe + rush * 0.35f + flare)
                        val glowY2 = glowY + sin(t * 0.8f) * focusPx * 0.04f
                        val glowCore = (0.75f + rush * 0.2f).coerceAtMost(1f)

                        matrix.reset()
                        matrix.setValues(floatArrayOf(dx, -dy, x0, dy, dx, 0f, 0f, 0f, 1f))
                        washPaint.shader = android.graphics.LinearGradient(
                            0f, 0f, 1f, 0f,
                            intArrayOf(lerp(dark, lit, 0.55f).toArgb(), lerp(dark, lit, 0.22f).toArgb(), dark.toArgb()),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        ).apply { setLocalMatrix(matrix) }
                        native.drawRect(0f, 0f, w, h, washPaint)

                        matrix.reset()
                        matrix.setScale(wanderR, wanderR)
                        matrix.postTranslate(wanderX, wanderY)
                        wanderPaint.shader = android.graphics.RadialGradient(
                            0f, 0f, 1f,
                            intArrayOf(lit.copy(alpha = 0.32f).toArgb(), lit.copy(alpha = 0.1f).toArgb(), Color.Transparent.toArgb()),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        ).apply { setLocalMatrix(matrix) }
                        native.drawCircle(wanderX, wanderY, wanderR, wanderPaint)

                        matrix.reset()
                        matrix.setScale(glowR, glowR)
                        matrix.postTranslate(glowX, glowY2)
                        glowPaint.shader = android.graphics.RadialGradient(
                            0f, 0f, 1f,
                            intArrayOf(lit.copy(alpha = glowCore).toArgb(), lit.copy(alpha = 0.32f).toArgb(), Color.Transparent.toArgb()),
                            floatArrayOf(0f, 0.45f, 1f),
                            Shader.TileMode.CLAMP
                        ).apply { setLocalMatrix(matrix) }
                        native.drawCircle(glowX, glowY2, glowR, glowPaint)

                        if (win > 0f && win < 1f) {
                            ringPaint.color = lerp(lit, Color.White, 0.5f).copy(alpha = (1f - win) * 0.9f).toArgb()
                            ringPaint.strokeWidth = (1f - win) * 10.dp.toPx() + 1.dp.toPx()
                            native.drawCircle(glowX, glowY, focusPx * (0.55f + win * 1.1f), ringPaint)
                        }
                    }
                }
            }
    )
}
