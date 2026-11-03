@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rukamori.archivetune.kemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.PlayerDockGeometry
import moe.rukamori.archivetune.constants.KemoDisplayMode
import moe.rukamori.archivetune.constants.KemoDisplayModeKey
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.playback.dualplay.DualPlayState
import moe.rukamori.archivetune.ui.utils.LocalPlayerIsExpanded
import moe.rukamori.archivetune.utils.rememberEnumPreference
import kotlin.math.roundToInt
import kotlin.random.Random

private class KemoParticle(
    val cellIndex: Int,
    val spawnTimeEpochMs: Long,
    val startXPx: Float,
    var startYPx: Float = -1f,
    val durationMs: Long,
    val floatDistancePx: Float,
)

@Composable
fun KemoOverlay(
    bottomInset: Dp,
    floatingToolbarBottomPadding: Dp,
    navVisibleHeight: Dp,
    shouldShowNav: Boolean,
    isMiniPlayerVisible: Boolean,
    useRail: Boolean,
    scrollVisibilityFactor: Float,
    playerExpansionFraction: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val (kemoMode, _) =
        rememberEnumPreference(
            KemoDisplayModeKey,
            defaultValue = KemoDisplayMode.WHILE_MUSIC_PLAYS,
        )

    // Mode OFF: absolutely nothing is composed or rendered.
    if (kemoMode == KemoDisplayMode.OFF) return

    val assets = rememberKemoAssets() ?: return
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── 1. Hide Rules (Full player expanded, keyboard open) ──────────────────────────
    val isPlayerExpanded = LocalPlayerIsExpanded.current || playerExpansionFraction() > 0.05f
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val isSuppressed = isPlayerExpanded || isKeyboardOpen

    // ── 2. Playback State (Player A or Dual Play Player B) ─────────────────────────
    val playerConnection = LocalPlayerConnection.current
    val isPlayerAPlaying by (playerConnection?.isPlaying ?: flowOf(false)).collectAsStateWithLifecycle(false)
    val dualPlayState by (playerConnection?.dualPlayEngine?.state ?: flowOf(DualPlayState())).collectAsStateWithLifecycle(DualPlayState())
    val isMusicPlaying = isPlayerAPlaying || (dualPlayState.enabled && dualPlayState.playerBIsPlaying)

    // ── 3. System Animation Scale & Battery Saver Observers ────────────────────────
    val animationsDisabled = LocalAnimationsDisabled.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var isPowerSaveMode by remember { mutableStateOf(powerManager?.isPowerSaveMode == true) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                isPowerSaveMode = powerManager?.isPowerSaveMode == true
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    var animatorScale by remember {
        mutableFloatStateOf(
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ),
        )
    }

    DisposableEffect(context) {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorScale = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    var isAppResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isAppResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_RESUME) {
                animatorScale = Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
                isPowerSaveMode = powerManager?.isPowerSaveMode == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val canAnimate = isAppResumed && !isPowerSaveMode && animatorScale > 0f && !animationsDisabled

    // ── 4. Integer Pixel Scaling ───────────────────────────────────────────────────
    val pixelScale = remember(density) {
        maxOf(1, density.toInt())
    }
    val frameWidth = assets.spec.frameWidth
    val frameHeight = assets.spec.frameHeight
    val spriteWidthPx = frameWidth * pixelScale
    val spriteHeightPx = frameHeight * pixelScale
    val spriteWidthDp = with(LocalDensity.current) { spriteWidthPx.toDp() }
    val spriteHeightDp = with(LocalDensity.current) { spriteHeightPx.toDp() }

    // ── 5. Lane Vertical Positioning & Smooth Transition ───────────────────────────
    val navDistance = if (shouldShowNav && !useRail) {
        floatingToolbarBottomPadding + navVisibleHeight
    } else {
        bottomInset
    }
    val navContribution = navDistance * scrollVisibilityFactor.coerceIn(0f, 1f)
    val miniPlayerContribution = if (isMiniPlayerVisible) {
        PlayerDockGeometry.miniPlayerHeight + MiniPlayerBottomSpacing
    } else {
        0.dp
    }
    val targetDockHeightDp = maxOf(bottomInset, navContribution) + miniPlayerContribution + KemoConfig.LANE_CLEARANCE_DP

    val animatedDockBottomDp by animateDpAsState(
        targetValue = targetDockHeightDp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "KemoDockSpring",
    )

    // ── 6. State Machine & Timers ──────────────────────────────────────────────────
    var currentState by remember { mutableStateOf(if (isMusicPlaying) KemoState.DANCE else KemoState.IDLE) }
    var previousPlaybackState by remember { mutableStateOf(isMusicPlaying) }
    var noPlaybackStartEpochMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var isWakingUp by remember { mutableStateOf(false) }

    // Movement & Hop state
    var xPosPx by remember { mutableFloatStateOf(64f * density) }
    var facingLeft by remember { mutableStateOf(false) }
    var isMovingHop by remember { mutableStateOf(false) }
    var isTapReaction by remember { mutableStateOf(false) }
    var hopsRemainingInWalk by remember { mutableIntStateOf(0) }

    // Timers
    var idleTimeMs by remember { mutableLongStateOf(0L) }
    var nextBlinkTimeMs by remember { mutableLongStateOf(Random.nextLong(KemoConfig.BLINK_MIN_INTERVAL_MS, KemoConfig.BLINK_MAX_INTERVAL_MS)) }
    var nextMoveTimeMs by remember { mutableLongStateOf(Random.nextLong(KemoConfig.IDLE_MIN_INTERVAL_MS, KemoConfig.IDLE_MAX_INTERVAL_MS)) }
    var animElapsedTimeMs by remember { mutableLongStateOf(0L) }

    // Particles
    val activeParticles = remember { mutableStateListOf<KemoParticle>() }
    var danceParticleTimerMs by remember { mutableLongStateOf(0L) }
    var danceNoteToggle by remember { mutableStateOf(false) }
    var sleepParticleTimerMs by remember { mutableLongStateOf(0L) }

    // Frame attributes for draw phase
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var currentDySpritePx by remember { mutableIntStateOf(0) }
    var currentStepFlip by remember { mutableStateOf(false) }

    // Track playback changes
    LaunchedEffect(isMusicPlaying) {
        val now = SystemClock.uptimeMillis()
        if (isMusicPlaying && !previousPlaybackState) {
            // Music started: wake-up hop, then dance!
            isWakingUp = true
            isMovingHop = false
            hopsRemainingInWalk = 0
            isTapReaction = false
            currentState = KemoState.HOP
            animElapsedTimeMs = 0L
        } else if (!isMusicPlaying && previousPlaybackState) {
            noPlaybackStartEpochMs = now
            if (kemoMode == KemoDisplayMode.WHILE_MUSIC_PLAYS) {
                currentState = KemoState.SLEEP
                animElapsedTimeMs = 0L
            } else {
                currentState = KemoState.IDLE
                animElapsedTimeMs = 0L
            }
        }
        previousPlaybackState = isMusicPlaying
    }

    // Target Alpha for Sleep / Fade
    val targetAlpha = remember(kemoMode, isMusicPlaying, isSuppressed, noPlaybackStartEpochMs, currentState) {
        if (isSuppressed) {
            0f
        } else if (kemoMode == KemoDisplayMode.WHILE_MUSIC_PLAYS) {
            if (isMusicPlaying || isWakingUp) {
                1f
            } else {
                val elapsedSinceStop = SystemClock.uptimeMillis() - noPlaybackStartEpochMs
                if (elapsedSinceStop >= KemoConfig.FADE_OUT_DURATION_WHILE_PLAYING_MS) 0f else 1f
            }
        } else {
            1f // ALWAYS mode: stays visible
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = KemoConfig.FADE_TRANSITION_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "KemoAlpha",
    )

    // Helper to spawn particles
    val spawnParticle: (Int) -> Unit = { cellIndex ->
        if (assets.particlesBitmap != null) {
            val mascotCenterX = xPosPx + (spriteWidthPx / 2f)
            val pCellSize = assets.spec.particles.cell
            val particleWidthPx = pCellSize * pixelScale
            val jitterSpritePx = if (cellIndex == 0) 0 else Random.nextInt(-4, 5)
            val startX = mascotCenterX - (particleWidthPx / 2f) + (jitterSpritePx * pixelScale)

            activeParticles.add(
                KemoParticle(
                    cellIndex = cellIndex,
                    spawnTimeEpochMs = SystemClock.uptimeMillis(),
                    startXPx = startX,
                    startYPx = -1f,
                    durationMs = KemoConfig.PARTICLE_DURATION_MS,
                    floatDistancePx = (KemoConfig.PARTICLE_FLOAT_SPRITE_PX * pixelScale).toFloat(),
                ),
            )
        }
    }

    // ── 7. Single Ticker Loop (Only while resumed & active) ─────────────────────────
    LaunchedEffect(canAnimate, isSuppressed, kemoMode) {
        if (!canAnimate || isSuppressed) return@LaunchedEffect

        var lastFrameTime = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = (now - lastFrameTime).coerceIn(1L, 100L)
            lastFrameTime = now

            val nowUptime = SystemClock.uptimeMillis()

            // 1. Clean up expired particles
            if (activeParticles.isNotEmpty()) {
                activeParticles.removeAll { nowUptime - it.spawnTimeEpochMs >= it.durationMs }
            }

            // 2. State Machine Transitions & Particle Generation
            if (isMusicPlaying) {
                if (currentState != KemoState.DANCE && currentState != KemoState.HOP && currentState != KemoState.HAPPY) {
                    currentState = KemoState.DANCE
                    animElapsedTimeMs = 0L
                }
                if (currentState == KemoState.DANCE) {
                    danceParticleTimerMs += dt
                    if (danceParticleTimerMs >= KemoConfig.DANCE_NOTE_INTERVAL_MS) {
                        danceParticleTimerMs = 0L
                        danceNoteToggle = !danceNoteToggle
                        spawnParticle(if (danceNoteToggle) 1 else 2) // alternate note and note2
                    }
                }
            } else {
                val timeWithoutMusic = nowUptime - noPlaybackStartEpochMs
                if (kemoMode == KemoDisplayMode.WHILE_MUSIC_PLAYS) {
                    if (currentState != KemoState.SLEEP && currentState != KemoState.HOP && currentState != KemoState.HAPPY) {
                        currentState = KemoState.SLEEP
                        animElapsedTimeMs = 0L
                    }
                } else if (kemoMode == KemoDisplayMode.ALWAYS) {
                    if (timeWithoutMusic >= KemoConfig.SLEEP_TIMEOUT_ALWAYS_MS) {
                        if (currentState != KemoState.SLEEP && currentState != KemoState.HOP && currentState != KemoState.HAPPY) {
                            currentState = KemoState.SLEEP
                            animElapsedTimeMs = 0L
                        }
                    } else if (currentState == KemoState.SLEEP) {
                        currentState = KemoState.IDLE
                        animElapsedTimeMs = 0L
                    }
                }

                if (currentState == KemoState.SLEEP) {
                    sleepParticleTimerMs += dt
                    if (sleepParticleTimerMs >= KemoConfig.SLEEP_ZZZ_INTERVAL_MS) {
                        sleepParticleTimerMs = 0L
                        spawnParticle(3) // zzz
                    }
                }
            }

            // Handle IDLE state timers (Blink & Move-Hops)
            if (currentState == KemoState.IDLE) {
                idleTimeMs += dt
                if (idleTimeMs >= nextBlinkTimeMs && hopsRemainingInWalk == 0) {
                    currentState = KemoState.BLINK
                    animElapsedTimeMs = 0L
                    nextBlinkTimeMs = idleTimeMs + Random.nextLong(KemoConfig.BLINK_MIN_INTERVAL_MS, KemoConfig.BLINK_MAX_INTERVAL_MS)
                } else if (idleTimeMs >= nextMoveTimeMs && hopsRemainingInWalk == 0) {
                    // Start hop-walk sequence
                    hopsRemainingInWalk = Random.nextInt(2, 5) // 2 to 4 hops
                    facingLeft = Random.nextBoolean()
                    isMovingHop = true
                    currentState = KemoState.HOP
                    animElapsedTimeMs = 0L
                    nextMoveTimeMs = idleTimeMs + Random.nextLong(KemoConfig.IDLE_MIN_INTERVAL_MS, KemoConfig.IDLE_MAX_INTERVAL_MS)
                }
            }

            // 3. Step Through Current Animation Spec
            val animKey = when (currentState) {
                KemoState.IDLE -> "idle"
                KemoState.BLINK -> "blink"
                KemoState.HOP -> "hop"
                KemoState.DANCE -> "dance"
                KemoState.SLEEP -> "sleep"
                KemoState.HAPPY -> "happy"
            }
            val animSpec = assets.spec.animations[animKey] ?: assets.spec.animations["idle"]
            if (animSpec != null && animSpec.steps.isNotEmpty()) {
                val totalAnimDurationMs = animSpec.steps.sumOf { it.ms }
                animElapsedTimeMs += dt

                if (!animSpec.loop && animElapsedTimeMs >= totalAnimDurationMs) {
                    // Non-looping animation completed
                    when (currentState) {
                        KemoState.BLINK -> {
                            currentState = KemoState.IDLE
                            animElapsedTimeMs = 0L
                        }
                        KemoState.HOP -> {
                            if (isMovingHop) {
                                hopsRemainingInWalk--
                                if (hopsRemainingInWalk > 0) {
                                    // Continue next hop in walk series
                                    animElapsedTimeMs = 0L
                                } else {
                                    isMovingHop = false
                                    currentState = if (isMusicPlaying) KemoState.DANCE else KemoState.IDLE
                                    animElapsedTimeMs = 0L
                                }
                            } else if (isTapReaction) {
                                isTapReaction = false
                                currentState = KemoState.HAPPY
                                animElapsedTimeMs = 0L
                            } else {
                                isWakingUp = false
                                currentState = if (isMusicPlaying) KemoState.DANCE else KemoState.IDLE
                                animElapsedTimeMs = 0L
                            }
                        }
                        KemoState.HAPPY -> {
                            currentState = if (isMusicPlaying) KemoState.DANCE else KemoState.IDLE
                            animElapsedTimeMs = 0L
                        }
                        else -> {
                            animElapsedTimeMs = 0L
                        }
                    }
                }

                // Determine active step
                val effectiveTimeMs = if (animSpec.loop && totalAnimDurationMs > 0) {
                    animElapsedTimeMs % totalAnimDurationMs
                } else {
                    animElapsedTimeMs.coerceAtMost(totalAnimDurationMs - 1)
                }

                var accumulated = 0L
                var activeStep = animSpec.steps.first()
                for (step in animSpec.steps) {
                    accumulated += step.ms
                    if (effectiveTimeMs < accumulated) {
                        activeStep = step
                        break
                    }
                }

                currentFrameIndex = activeStep.f
                currentDySpritePx = activeStep.dy
                currentStepFlip = activeStep.flip

                // 4. Horizontal movement during moving hop
                if (currentState == KemoState.HOP && isMovingHop && totalAnimDurationMs > 0) {
                    val hopDistancePx = (KemoConfig.HOP_WALK_DISTANCE_SPRITE_PX * pixelScale).toFloat()
                    val dx = (hopDistancePx / totalAnimDurationMs) * dt
                    if (facingLeft) {
                        xPosPx -= dx
                    } else {
                        xPosPx += dx
                    }
                }
            }
        }
    }

    // Static frame when animation disabled (battery saver, animator duration scale 0)
    val effectiveFrameIndex = if (canAnimate) currentFrameIndex else 0
    val effectiveDySpritePx = if (canAnimate) currentDySpritePx else 0
    val effectiveStepFlip = if (canAnimate) currentStepFlip else false

    // ── 8. Layout & Draw Phase ─────────────────────────────────────────────────────
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val parentWidthPx = constraints.maxWidth.toFloat()
        val parentHeightPx = constraints.maxHeight.toFloat()
        val minXPx = with(LocalDensity.current) { KemoConfig.HORIZONTAL_PADDING_DP.toPx() }
        val maxXPx = (parentWidthPx - spriteWidthPx - minXPx).coerceAtLeast(minXPx)

        // Boundary reflection: change direction if hitting edges
        if (xPosPx > maxXPx) {
            xPosPx = maxXPx
            facingLeft = true
            hopsRemainingInWalk = 0
        } else if (xPosPx < minXPx) {
            xPosPx = minXPx
            facingLeft = false
            hopsRemainingInWalk = 0
        }

        val triggerTap: () -> Unit = {
            if (animatedAlpha > 0.05f) {
                // Heart particle on tap
                spawnParticle(0)
                isMovingHop = false
                hopsRemainingInWalk = 0
                isTapReaction = true
                isWakingUp = false
                currentState = KemoState.HOP
                animElapsedTimeMs = 0L
            }
        }

        // When faded out, completely skip composition and touch testing
        if (animatedAlpha <= 0.001f) {
            return@BoxWithConstraints
        }

        val dockBottomPx = animatedDockBottomDp.toPx()
        val mascotTopY = parentHeightPx - dockBottomPx - spriteHeightPx + (effectiveDySpritePx * pixelScale)

        // 1. Floating Particles Layer (never consumes touches, drawn in parent coordinate space)
        val pBitmap = assets.particlesBitmap
        if (pBitmap != null && activeParticles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pCellSize = assets.spec.particles.cell
                val pDstSize = IntSize(pCellSize * pixelScale, pCellSize * pixelScale)
                val pSrcSize = IntSize(pCellSize, pCellSize)
                val nowUptime = SystemClock.uptimeMillis()

                for (i in 0 until activeParticles.size) {
                    val p = activeParticles.getOrNull(i) ?: continue
                    val elapsed = nowUptime - p.spawnTimeEpochMs
                    if (elapsed in 0..p.durationMs) {
                        val progress = elapsed.toFloat() / p.durationMs
                        val pAlpha = (1f - progress) * animatedAlpha
                        if (p.startYPx < 0f) {
                            p.startYPx = mascotTopY - pDstSize.height + (2 * pixelScale)
                        }
                        val curY = p.startYPx - (p.floatDistancePx * progress)
                        val curX = p.startXPx

                        drawImage(
                            image = pBitmap,
                            srcOffset = IntOffset(p.cellIndex * pCellSize, 0),
                            srcSize = pSrcSize,
                            dstOffset = IntOffset(curX.roundToInt(), curY.roundToInt()),
                            dstSize = pDstSize,
                            alpha = pAlpha,
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
        }

        // 2. Mascot Hit-Box Box: exact sprite dimensions, pointerInput detects taps without ripple
        Box(
            modifier = Modifier
                .size(spriteWidthDp, spriteHeightDp)
                .graphicsLayer {
                    translationX = xPosPx
                    translationY = parentHeightPx - dockBottomPx - spriteHeightPx
                    alpha = animatedAlpha
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        triggerTap()
                    }
                }
                .semantics { invisibleToUser() },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fWidth = assets.spec.frameWidth
                val fHeight = assets.spec.frameHeight
                val dyOffsetPx = effectiveDySpritePx * pixelScale
                val shouldMirror = facingLeft xor effectiveStepFlip

                if (shouldMirror) {
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                        drawImage(
                            image = assets.spriteSheet,
                            srcOffset = IntOffset(effectiveFrameIndex * fWidth, 0),
                            srcSize = IntSize(fWidth, fHeight),
                            dstOffset = IntOffset(0, dyOffsetPx),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.None,
                        )
                    }
                } else {
                    drawImage(
                        image = assets.spriteSheet,
                        srcOffset = IntOffset(effectiveFrameIndex * fWidth, 0),
                        srcSize = IntSize(fWidth, fHeight),
                        dstOffset = IntOffset(0, dyOffsetPx),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
    }
}
