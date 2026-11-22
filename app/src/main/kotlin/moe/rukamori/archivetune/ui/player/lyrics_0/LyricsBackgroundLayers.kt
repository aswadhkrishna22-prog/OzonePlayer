/*
 * Ozone Player (2026)
 * Rain-on-glass AGSL RuntimeShader effect & blurred backdrop
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.lyrics_0

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.LyricsRainEffectKey
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.utils.FastBlurTransformation
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * AGSL Port of Martijn Steinrucken's (BigWIngs) "Heartfelt" Rain on Glass Shader.
 * Features:
 * - Refracted drops: samples uContent with inverted normal offset inside each drop
 * - Large drops that slide with trails and realistic stick-slip gravity hesitation
 * - Small static droplets clinging to the windowpane with slow evaporation cycles
 * - Light atmospheric fog/condensation cleared by droplet trails
 * - Directional specular reflections on droplet crests
 */
private const val RAIN_GLASS_AGSL_SRC = """
    uniform shader uContent;
    uniform float2 uResolution;
    uniform float uTime;

    float N21(float2 p) {
        p = fract(p * float2(123.34, 345.45));
        p += dot(p, p + 34.345);
        return fract(p.x * p.y);
    }

    float3 N13(float p) {
        float3 p3 = fract(float3(p) * float3(0.1031, 0.11369, 0.13787));
        p3 += dot(p3, p3.yzx + 19.19);
        return fract(float3((p3.x + p3.y) * p3.z, (p3.x + p3.z) * p3.y, (p3.y + p3.z) * p3.x));
    }

    float Saw(float b, float t) {
        return smoothstep(0.0, b, t) * smoothstep(1.0, b, t);
    }

    float2 DropLayer(float2 uv, float t, float2 aspect, float scale) {
        float2 grid = aspect * scale;
        float2 id = floor(uv * grid);
        
        float colShift = N21(float2(id.x, 37.1));
        uv.y += colShift;
        
        id = floor(uv * grid);
        float3 n = N13(id.x * 35.2 + id.y * 2376.1);
        float2 st = fract(uv * grid) - float2(0.5, 0.0);
        
        float x = n.x - 0.5;
        
        float yWiggle = uv.y * 18.0;
        float wiggle = sin(yWiggle + sin(yWiggle));
        x += wiggle * (0.5 - abs(x)) * (n.z - 0.5);
        x *= 0.68;
        
        float ti = fract(t + n.z);
        float y = (Saw(0.85, ti) - 0.5) * 0.9 + 0.5;
        float2 p = float2(x, y);
        
        float2 dropOffset = (st - p) * float2(1.0, 1.35);
        float d = length(dropOffset);
        float mainDrop = smoothstep(0.30, 0.0, d);
        
        float r = sqrt(smoothstep(1.0, y, st.y));
        float cd = abs(st.x - x);
        float trail = smoothstep(0.22 * r, 0.10 * r * r, cd);
        float trailFront = smoothstep(-0.02, 0.02, st.y - y);
        trail *= trailFront * r * r;
        
        float trailY = uv.y * 14.0;
        float trail2 = smoothstep(0.18 * r, 0.0, cd);
        float dropletP = fract(trailY) + (st.y - 0.5);
        float dd = length(st - float2(x, dropletP));
        float droplets = smoothstep(0.26, 0.0, dd) * trail2 * trailFront * smoothstep(0.25, 0.75, n.y);
        
        float m = mainDrop + droplets * r * trailFront;
        return float2(m, trail);
    }

    float StaticDrops(float2 uv, float t, float scale) {
        float2 sUv = uv * scale;
        float2 id = floor(sUv);
        float2 st = fract(sUv) - 0.5;
        
        float3 n = N13(id.x * 107.45 + id.y * 3543.654);
        float2 p = (n.xy - 0.5) * 0.7;
        float d = length(st - p);
        
        float r = clamp(n.z * 0.35, 0.08, 0.30);
        float drop = smoothstep(r, r * 0.25, d);
        
        float fade = Saw(0.025, fract(t * 0.05 + n.z));
        return drop * fade;
    }

    float2 EvaluateDrops(float2 uv, float t) {
        float2 aspect = float2(2.2, 1.0);
        
        float2 d1 = DropLayer(uv, t * 0.22, aspect, 1.8);
        float2 d2 = DropLayer(uv * 1.6 + float2(17.4, 43.1), t * 0.16, aspect, 2.4);
        
        float s1 = StaticDrops(uv, t, 32.0);
        float s2 = StaticDrops(uv * 1.7 + float2(89.1, 14.7), t, 48.0);
        
        float totalDrops = d1.x * 1.25 + d2.x * 0.95 + s1 * 0.70 + s2 * 0.50;
        float totalTrail = max(d1.y, d2.y * 0.75);
        
        return float2(totalDrops, totalTrail);
    }

    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord - 0.5 * uResolution) / uResolution.y;
        
        float2 dropInfo = EvaluateDrops(uv, uTime);
        float h0 = dropInfo.x;
        float trail = dropInfo.y;
        
        float2 e = float2(0.003, 0.0);
        float hx = EvaluateDrops(uv + e, uTime).x;
        float hy = EvaluateDrops(uv + e.yx, uTime).x;
        float2 n = float2(hx - h0, hy - h0);
        
        float2 refractionOffset = -n * uResolution.y * 0.22;
        float2 sampleCoord = clamp(fragCoord + refractionOffset, float2(0.0), uResolution - float2(1.0));
        half4 col = uContent.eval(sampleCoord);
        
        float fogMask = clamp(1.0 - trail * 1.8 - h0 * 2.2, 0.0, 1.0);
        half3 fogColor = half3(0.07, 0.09, 0.12);
        col.rgb = mix(col.rgb, col.rgb * 0.82 + fogColor, half(fogMask * 0.30));
        
        if (h0 > 0.02) {
            float3 lightDir = normalize(float3(0.2, -0.75, 0.6));
            float3 norm3D = normalize(float3(n * 2.4, 0.04));
            float spec = max(0.0, dot(norm3D, lightDir));
            float highlight = pow(spec, 20.0) * smoothstep(0.04, 0.45, h0);
            
            float frontSpec = pow(max(0.0, norm3D.z), 28.0) * 0.2 * smoothstep(0.08, 0.6, h0);
            float edgeShadow = smoothstep(0.0, 0.12, h0) * (1.0 - smoothstep(0.12, 0.45, h0));
            
            col.rgb *= half(1.0 - edgeShadow * 0.18);
            col.rgb += half3((highlight + frontSpec) * 0.45);
        }
        
        return col;
    }
"""

@Composable
fun LyricsBackgroundLayers(
    state: PlayerUiState,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (rainEffectPref) = rememberPreference(LyricsRainEffectKey, defaultValue = true)
    val animationsDisabled = LocalAnimationsDisabled.current
    val isShaderSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val isRainActive = isShaderSupported && rainEffectPref && !animationsDisabled

    val lifecycleOwner = LocalLifecycleOwner.current
    var animTimeSeconds by remember { mutableFloatStateOf(0f) }

    // Performance: frame ticker capped at ~30fps (~33ms intervals).
    // Automatically pauses when app is in background or screen is off via Lifecycle.State.RESUMED,
    // or when lyrics sheet is not visible.
    LaunchedEffect(isRainActive, isVisible, lifecycleOwner) {
        if (!isRainActive || !isVisible) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastTimeMs = SystemClock.uptimeMillis()
            while (isActive) {
                delay(33L) // ~30 fps cap
                val nowMs = SystemClock.uptimeMillis()
                val dt = (nowMs - lastTimeMs).coerceIn(0L, 100L) / 1000f
                lastTimeMs = nowMs
                animTimeSeconds += dt
            }
        }
    }

    val rainShader = remember(isRainActive) {
        if (isRainActive) {
            try {
                RuntimeShader(RAIN_GLASS_AGSL_SRC)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    val blurImageRequest = remember(state.coverUrl) {
        ImageRequest.Builder(context)
            .data(state.coverUrl)
            .size(96)
            .transformations(FastBlurTransformation(radius = 25, sampling = 1f))
            .build()
    }

    // Performance: Render at reduced resolution (50% = 0.5x resolution) when shader is active,
    // upscaled 2x with bilinear filtering. Falls back to native 1x resolution for static blur.
    val downscale = if (isRainActive && rainShader != null) 2 else 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .layout { measurable, constraints ->
                val targetW = (constraints.maxWidth / downscale).coerceAtLeast(1)
                val targetH = (constraints.maxHeight / downscale).coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(targetW, targetH))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, 0)
                }
            }
            .graphicsLayer {
                if (downscale > 1) {
                    scaleX = downscale.toFloat()
                    scaleY = downscale.toFloat()
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                if (isRainActive && rainShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val w = size.width
                    val h = size.height
                    if (w > 0f && h > 0f) {
                        rainShader.setFloatUniform("uResolution", w, h)
                        rainShader.setFloatUniform("uTime", animTimeSeconds)
                        renderEffect = RenderEffect
                            .createRuntimeShaderEffect(rainShader, "uContent")
                            .asComposeRenderEffect()
                    } else {
                        renderEffect = null
                    }
                } else {
                    renderEffect = null
                }
            }
    ) {
        // Blurred album art background layer (or dark muted gradient if cover is not available)
        if (state.coverUrl.isNotEmpty()) {
            AsyncImage(
                model = blurImageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(state.darkMutedColor),
                                Color(0xFF121212)
                            )
                        )
                    )
            )
        }

        // Contrast scrim overlay: ensures lyrics text remains readable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
    }
}
