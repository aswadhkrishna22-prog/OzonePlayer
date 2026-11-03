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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
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
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.ui.utils.LocalPlayerIsExpanded
import moe.rukamori.archivetune.PlayerDockGeometry
import moe.rukamori.archivetune.constants.KemoDisplayMode
import moe.rukamori.archivetune.constants.KemoDisplayModeKey
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.playback.dualplay.DualPlayState
import moe.rukamori.archivetune.utils.rememberEnumPreference
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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
        maxOf(2, (density * 1.25f).roundToInt())
    }
    val spriteWidthPx = assets.spec.frameWidth * pixelScale
    val spriteHeightPx = assets.spec.frameHeight * pixelScale
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

    // ── 6. State Machine & Sleep/Fade Timers ────────────────────────────────────────
    var currentState by remember { mutableStateOf(if (isMusicPlaying) KemoState.DANCE else KemoState.IDLE) }
    var previousPlaybackState by remember { mutableStateOf(isMusicPlaying) }
    var noPlaybackStartEpochMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var isWakingUp by remember { mutableStateOf(false) }

    // Hop state
    var isHopping by remember { mutableStateOf(false) }
    var hopStartEpochMs by remember { mutableLongStateOf(0L) }
    var stateBeforeHop by remember { mutableStateOf(KemoState.IDLE) }

    // Position state
    var xPosPx by remember { mutableFloatStateOf(64f * density) }
    var facingDirection by remember { mutableFloatStateOf(1f) } // 1f = right, -1f = left
    var stateTimerRemainingMs by remember { mutableLongStateOf(KemoConfig.IDLE_MIN_DURATION_MS) }

    // Track playback changes
    LaunchedEffect(isMusicPlaying) {
        val now = SystemClock.uptimeMillis()
        if (isMusicPlaying && !previousPlaybackState) {
            // Wake up!
            isWakingUp = true
            isHopping = true
            hopStartEpochMs = now
            stateBeforeHop = KemoState.DANCE
            currentState = KemoState.HOP
        } else if (!isMusicPlaying && previousPlaybackState) {
            noPlaybackStartEpochMs = now
            if (kemoMode == KemoDisplayMode.WHILE_MUSIC_PLAYS) {
                currentState = KemoState.SLEEP
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

    // Current frame index
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var animFrameProgressMs by remember { mutableLongStateOf(0L) }

    // ── 7. Single Ticker Loop (Only while resumed & active) ─────────────────────────
    LaunchedEffect(canAnimate, isSuppressed, kemoMode) {
        if (!canAnimate || isSuppressed) return@LaunchedEffect

        var lastFrameTime = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = (now - lastFrameTime).coerceIn(1L, 100L)
            lastFrameTime = now

            // 1. Update State Transitions
            val nowUptime = SystemClock.uptimeMillis()
            if (isHopping) {
                val elapsedHop = nowUptime - hopStartEpochMs
                if (elapsedHop >= KemoConfig.HOP_DURATION_MS) {
                    isHopping = false
                    isWakingUp = false
                    currentState = if (isMusicPlaying) KemoState.DANCE else stateBeforeHop
                }
            } else if (isMusicPlaying) {
                if (currentState != KemoState.DANCE) {
                    currentState = KemoState.DANCE
                }
            } else {
                val timeWithoutMusic = nowUptime - noPlaybackStartEpochMs
                if (kemoMode == KemoDisplayMode.WHILE_MUSIC_PLAYS) {
                    currentState = KemoState.SLEEP
                } else if (kemoMode == KemoDisplayMode.ALWAYS) {
                    if (timeWithoutMusic >= KemoConfig.SLEEP_TIMEOUT_ALWAYS_MS) {
                        currentState = KemoState.SLEEP
                    } else if (currentState == KemoState.SLEEP) {
                        currentState = KemoState.IDLE
                    }
                }

                if (currentState != KemoState.SLEEP && !isHopping) {
                    stateTimerRemainingMs -= dt
                    if (stateTimerRemainingMs <= 0) {
                        if (currentState == KemoState.IDLE) {
                            currentState = KemoState.WALK
                            facingDirection = if (Random.nextBoolean()) 1f else -1f
                            stateTimerRemainingMs = Random.nextLong(
                                KemoConfig.WALK_MIN_DURATION_MS,
                                KemoConfig.WALK_MAX_DURATION_MS,
                            )
                        } else if (currentState == KemoState.WALK) {
                            currentState = KemoState.IDLE
                            stateTimerRemainingMs = Random.nextLong(
                                KemoConfig.IDLE_MIN_DURATION_MS,
                                KemoConfig.IDLE_MAX_DURATION_MS,
                            )
                        }
                    }
                }
            }

            // 2. Advance Sprite Animation Frame
            val animKey = when (currentState) {
                KemoState.IDLE -> "idle"
                KemoState.WALK -> "walk"
                KemoState.DANCE -> "dance"
                KemoState.SLEEP -> "sleep"
                KemoState.HOP -> "hop"
            }
            val animSpec = assets.spec.animations[animKey] ?: assets.spec.animations["idle"]
            if (animSpec != null && animSpec.frames.isNotEmpty()) {
                animFrameProgressMs += dt
                val frameDuration = (animSpec.durationMs / animSpec.frames.size).coerceAtLeast(40L)
                val frameStep = (animFrameProgressMs / frameDuration).toInt()
                val frameIdx = if (animSpec.loop) {
                    animSpec.frames[frameStep % animSpec.frames.size]
                } else {
                    animSpec.frames[minOf(frameStep, animSpec.frames.size - 1)]
                }
                currentFrameIndex = frameIdx
            }

            // 3. Move along lane when walking
            if (currentState == KemoState.WALK && !isHopping) {
                val speedPx = KemoConfig.WALK_SPEED_DP_PER_SEC * density
                xPosPx += facingDirection * speedPx * (dt / 1000f)
            }
        }
    }

    // If animations disabled / battery saver / 0 animator scale: freeze at idle frame 0
    val effectiveFrameIndex = if (canAnimate) currentFrameIndex else 0

    // ── 8. Layout & Draw Phase ─────────────────────────────────────────────────────
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val parentWidthPx = constraints.maxWidth.toFloat()
        val parentHeightPx = constraints.maxHeight.toFloat()
        val minXPx = with(LocalDensity.current) { KemoConfig.HORIZONTAL_PADDING_DP.toPx() }
        val maxXPx = (parentWidthPx - spriteWidthPx - minXPx).coerceAtLeast(minXPx)

        // Keep inside screen bounds
        if (xPosPx > maxXPx) {
            xPosPx = maxXPx
            facingDirection = -1f
        } else if (xPosPx < minXPx) {
            xPosPx = minXPx
            facingDirection = 1f
        }

        val hopProgress = if (isHopping) {
            val elapsed = SystemClock.uptimeMillis() - hopStartEpochMs
            (elapsed.toFloat() / KemoConfig.HOP_DURATION_MS).coerceIn(0f, 1f)
        } else 0f
        val hopOffsetPx = if (isHopping) {
            val hopHeightPx = with(LocalDensity.current) { KemoConfig.HOP_HEIGHT_DP.toPx() }
            sin(hopProgress * PI).toFloat() * hopHeightPx
        } else 0f

        val triggerHop: () -> Unit = {
            if (!isHopping && animatedAlpha > 0.05f) {
                isHopping = true
                hopStartEpochMs = SystemClock.uptimeMillis()
                stateBeforeHop = currentState
                currentState = KemoState.HOP
                animFrameProgressMs = 0L
            }
        }

        // When faded out, completely skip composition and touch testing
        if (animatedAlpha <= 0.001f) {
            return@BoxWithConstraints
        }

        // Mascot Hit-Box Box: Only sized to exact sprite, pointerInput detects taps without ripple
        Box(
            modifier = Modifier
                .size(spriteWidthDp, spriteHeightDp)
                .graphicsLayer {
                    val dockBottomPx = animatedDockBottomDp.toPx()
                    translationX = xPosPx
                    translationY = parentHeightPx - dockBottomPx - spriteHeightPx - hopOffsetPx
                    scaleX = facingDirection
                    alpha = animatedAlpha
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        triggerHop()
                    }
                }
                .semantics { invisibleToUser() },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fWidth = assets.spec.frameWidth
                val fHeight = assets.spec.frameHeight
                val sheetCols = assets.bitmap.width / fWidth
                val safeSheetCols = if (sheetCols > 0) sheetCols else 1
                val srcX = (effectiveFrameIndex % safeSheetCols) * fWidth
                val srcY = (effectiveFrameIndex / safeSheetCols) * fHeight

                drawImage(
                    image = assets.bitmap,
                    srcOffset = IntOffset(srcX, srcY),
                    srcSize = IntSize(fWidth, fHeight),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}
