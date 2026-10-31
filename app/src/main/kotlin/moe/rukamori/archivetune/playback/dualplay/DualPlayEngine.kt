/*
 * Ozone Player (2026) | Dual Play Engine
 * GNU General Public License v3.0
 *
 * Plays two songs simultaneously: Song A in the LEFT ear only, Song B in the RIGHT ear only.
 *
 * Design rules (from spec):
 *  • One ChannelSplitProcessor instance per ExoPlayer sink; mode shared via @Volatile flag.
 *  • Crossfade disabled while Dual Play is on; user setting restored on disable.
 *  • Player B: handleAudioFocus = false, wakeMode = WAKE_MODE_LOCAL.
 *  • Headset button / becoming-noisy / audio-focus loss pause BOTH players.
 *  • Only in-app DualPlayController per-song controls are independent.
 *  • If stereo headphones disconnect → auto-disable and pause both.
 *  • Foreground service kept alive while Player B is playing.
 *  • Player B errors never crash or stop Player A.
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback.dualplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.setOffloadEnabled
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.cancelCrossfade
import moe.rukamori.archivetune.playback.createMediaSourceFactory
import moe.rukamori.archivetune.playback.PlaybackConstants
import moe.rukamori.archivetune.playback.processor.SpatialProcessor
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.extensions.toMediaItem
import timber.log.Timber
import java.util.concurrent.TimeUnit
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

// ── Public state model ─────────────────────────────────────────────────────────

/**
 * Holds a queue that the user tapped while Dual Play was active.
 * The UI shows "Add to Dual Play / Play instead / Cancel" when this is non-null.
 */
data class PendingDualPlayQueue(
    val queue: Queue,
    val songTitle: String,
    val songArtist: String? = null,
    val artworkUri: String? = null,
    val resolvedMediaItem: MediaItem? = null,
)

data class DualPlayState(
    val enabled: Boolean = false,
    val playerBMediaItem: MediaItem? = null,
    val playerBIsPlaying: Boolean = false,
    val playerBPlaybackState: Int = Player.STATE_IDLE,
    val playerBError: String? = null,
    val volumeA: Float = 1f,
    val volumeB: Float = 1f,
    /** Non-null when the channel split was NOT applied (unsupported format). */
    val channelSplitUnsupportedFormat: String? = null,
    /**
     * Ear selection for Pane A. None selected initially (null = normal stereo).
     */
    val earSelectionA: EarSelection? = null,
    /**
     * Ear selection for Pane B.
     */
    val earSelectionB: EarSelection? = null,
    /** True when Song B has been loaded and split view should be shown. */
    val splitViewActive: Boolean = false,
    /**
     * Pending song intercept — non-null when the user taps a song while Dual Play is on.
     * The UI observes this and shows the "Add to Dual Play / Play instead" dialog.
     */
    val pendingInterceptQueue: PendingDualPlayQueue? = null,
)

// ── Engine ─────────────────────────────────────────────────────────────────────

@UnstableApi
class DualPlayEngine(
    private val service: MusicService,
    private val scope: CoroutineScope,
) {

    // ── State ──────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(DualPlayState())
    val state: StateFlow<DualPlayState> = _state.asStateFlow()

    val isEnabled: Boolean get() = _state.value.enabled

    // ── Player B ───────────────────────────────────────────────────────────────

    private var playerB: ExoPlayer? = null

    /**
     * RIGHT-channel processor owned by Player B's sink.
     * Created once and reused for the lifetime of this engine.
     */
    private val rightProcessor = ChannelSplitProcessor(ProcessorSide.RIGHT)

    // ── Saved settings (restored on disable) ──────────────────────────────────

    private var savedSpatialEnabled   = false
    private var savedVirtualizerEnabled = false
    private var savedCrossfadeEnabled = false

    // ── Audio focus mirroring ──────────────────────────────────────────────────

    private var wasPlayerBPlayingBeforeFocusLoss = false

    // ── Jobs ───────────────────────────────────────────────────────────────────

    private var foregroundKeepAliveJob: Job? = null
    private var formatPollingJob: Job? = null

    // ── Broadcast receiver for becoming-noisy ──────────────────────────────────

    private var noisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            Timber.tag(TAG).d("Becoming noisy → pausing Player B")
            pausePlayerB()
        }
    }

    // ── Enable / disable ───────────────────────────────────────────────────────

    /**
     * Enable Dual Play.  Returns false (and does nothing) if headphones are
     * not connected or if already enabled.
     */
    fun enable(): Boolean {
        if (isEnabled) return true
        if (!isHeadphonesConnected()) {
            Timber.tag(TAG).w("Dual Play: no supported stereo headphones connected – ignoring enable()")
            return false
        }
        checkMonoAudioAccessibility()

        // ── 1. Disable crossfade ───────────────────────────────────────────────
        savedCrossfadeEnabled = service.crossfadeEnabled
        if (service.crossfadeEnabled) {
            service.crossfadeEnabled = false
            service.cancelPlayerCrossfade(resetVolume = true, resetPauseAtEnd = true)
            Timber.tag(TAG).d("Dual Play: crossfade disabled (was enabled)")
        }

        // ── 2. Disable SpatialProcessor (mixes channels → defeats split) ───────
        savedSpatialEnabled = SpatialProcessor.instance.enabled
        SpatialProcessor.instance.enabled = false

        // ── 3. Disable Virtualizer (creates stereo width by cross-mixing) ───────
        savedVirtualizerEnabled = service.virtualizer?.let {
            runCatching { it.enabled }.getOrDefault(false)
        } ?: false
        service.virtualizer?.let { runCatching { it.enabled = false } }

        // ── 4. Activate channel-split mode ────────────────────────────────────
        DualPlayChannelState.mode  = ChannelMode.ACTIVE
        DualPlayChannelState.modeA = EarMode.STEREO   // Normal stereo until user picks L/R/M
        DualPlayChannelState.modeB = EarMode.STEREO

        // ── 5. Build Player B ─────────────────────────────────────────────────
        createAndConfigurePlayerB()

        // ── 6. Register becoming-noisy receiver ───────────────────────────────
        registerNoisyReceiver()

        // ── 7. Start format polling (for debug screen) ────────────────────────
        startFormatPolling()

        // ── 8. Disable audio offload so processors are never bypassed ──────────
        service.localPlayer.setOffloadEnabled(false)
        playerB?.setOffloadEnabled(false)

        _state.value = _state.value.copy(
            enabled = true,
            earSelectionA = null,
            earSelectionB = null,
            splitViewActive = false,
            playerBMediaItem = null,
            playerBError = null,
        )
        Timber.tag(TAG).i("Dual Play enabled (earSelectionA=null, splitViewActive=false)")
        return true
    }

    /**
     * Disable Dual Play and restore all previous settings.
     */
    fun disable() {
        if (!isEnabled) return
        Timber.tag(TAG).i("Dual Play disabling…")

        // Order matters: stop audio first, then restore effects.
        DualPlayChannelState.mode  = ChannelMode.OFF
        DualPlayChannelState.modeA = EarMode.STEREO
        DualPlayChannelState.modeB = EarMode.STEREO

        releasePlayerB()
        unregisterNoisyReceiver()
        stopFormatPolling()
        stopForegroundKeepAlive()

        // Restore SpatialProcessor
        SpatialProcessor.instance.enabled = savedSpatialEnabled

        // Restore virtualizer
        service.virtualizer?.let { runCatching { it.enabled = savedVirtualizerEnabled } }

        // Restore crossfade
        if (savedCrossfadeEnabled) {
            service.crossfadeEnabled = true
            // Let the lifecycle's crossfade flow re-schedule via normal dataStore observation;
            // we just poke the flag so the next track transition picks it up.
            Timber.tag(TAG).d("Dual Play: crossfade restored")
        }

        _state.value = DualPlayState()   // full reset
        Timber.tag(TAG).i("Dual Play disabled")
    }

    // ── Audio focus mirroring (called from MusicServiceAudioFocusRoute) ────────

    /**
     * Mirror the same focus event that Player A received onto Player B.
     * Player B has handleAudioFocus = false, so it never manages focus itself.
     */
    fun onAudioFocusChange(focusChange: Int) {
        if (!isEnabled) return
        val b = playerB ?: return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                if (wasPlayerBPlayingBeforeFocusLoss) {
                    b.play()
                    wasPlayerBPlayingBeforeFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayerBPlayingBeforeFocusLoss = b.playWhenReady
                b.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                wasPlayerBPlayingBeforeFocusLoss = b.playWhenReady
                b.pause()
            }
        }
    }

    // ── Headphone disconnect (called from MusicServiceAudioFocusRoute) ─────────

    /**
     * Called whenever audio output devices change.
     * If no supported stereo headphones remain connected, Dual Play is stopped.
     */
    fun onAudioOutputDeviceChanged() {
        if (!isEnabled) return
        if (!isHeadphonesConnected()) {
            Timber.tag(TAG).w("Dual Play: headphones disconnected – disabling and pausing")
            pausePlayerB()
            pausePlayerA()
            disable()
        }
    }

    // ── In-app per-song controls (independent of focus) ───────────────────────

    fun playA()  { service.player.play() }
    fun pauseA() { service.player.pause() }
    fun seekA(posMs: Long) { service.player.seekTo(posMs) }
    fun setVolumeA(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        service.localPlayer.volume = clamped
        _state.value = _state.value.copy(volumeA = clamped)
    }

    fun playB() {
        val b = playerB ?: run {
            Timber.tag(TAG).w("playB: playerB is null")
            return
        }
        Timber.tag(TAG).i("playB: state=${b.playbackState}, playWhenReady=${b.playWhenReady}")
        b.play()
    }
    fun pauseB() { playerB?.pause() }
    fun seekB(posMs: Long) { playerB?.seekTo(posMs) }
    fun getPlayerBPosition(): Long = playerB?.currentPosition ?: 0L
    fun getPlayerBDuration(): Long = playerB?.duration?.takeIf { it > 0 } ?: 0L
    fun setVolumeB(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        playerB?.volume = clamped
        _state.value = _state.value.copy(volumeB = clamped)
    }

    /** Set the media item for Player B and prepare (splitViewActive will be activated once ready/playing). */
    fun setMediaItemB(item: MediaItem) {
        val b = playerB ?: run {
            Timber.tag(TAG).w("setMediaItemB: playerB is null")
            return
        }
        val uri = item.localConfiguration?.uri
        Timber.tag(TAG).i("setMediaItemB: mediaId=${item.mediaId}, uri=$uri, hasUri=${uri != null}, state=${b.playbackState}")
        b.setMediaItem(item)
        b.prepare()
        _state.value = _state.value.copy(
            playerBMediaItem = item,
            playerBError = null,
        )
    }

    // ── Ear selection ──────────────────────────────────────────────────────────

    /**
     * Returns true if stereo headphones are connected and Dual Play can be enabled.
     * Exposed so the UI can disable the toggle with a reason.
     */
    fun canEnable(): Boolean = isHeadphonesConnected()

    /**
     * Set the ear routing for Pane A (Song A).
     * Pane A only shows R and M buttons; L = default (no button highlighted).
     */
    fun setEarA(ear: EarSelection) {
        DualPlayChannelState.modeA = earSelectionToMode(ear)
        _state.value = _state.value.copy(earSelectionA = ear)
        Timber.tag(TAG).d("Ear A → $ear (modeA=${DualPlayChannelState.modeA})")
    }

    /**
     * Set the ear routing for Pane B (Song B).
     * Pane B only shows L and M buttons; R = default (no button highlighted).
     */
    fun setEarB(ear: EarSelection) {
        DualPlayChannelState.modeB = earSelectionToMode(ear)
        _state.value = _state.value.copy(earSelectionB = ear)
        Timber.tag(TAG).d("Ear B → $ear (modeB=${DualPlayChannelState.modeB})")
    }

    /** Swap ear assignments between Pane A and Pane B. Instant, no re-prepare. */
    fun swapEars() {
        val a = _state.value.earSelectionA
        val b = _state.value.earSelectionB
        // Atomic swap: write both before UI re-reads
        DualPlayChannelState.modeA = earSelectionToMode(b)
        DualPlayChannelState.modeB = earSelectionToMode(a)
        _state.value = _state.value.copy(earSelectionA = b, earSelectionB = a)
        Timber.tag(TAG).d("Ears swapped: A=$b B=$a")
    }

    // ── Song intercept ─────────────────────────────────────────────────────────

    /**
     * Called by [PlayerConnection.playQueue] when Dual Play is enabled.
     * Stores the pending queue so the UI can show the intercept dialog.
     * Returns true = intercepted (caller must NOT dispatch to service).
     */
    fun interceptQueue(queue: Queue): Boolean {
        if (!isEnabled) return false
        val preload = queue.preloadItem
        if (preload != null) {
            val mediaItem = preload.toMediaItem()
            val title = preload.title
            val artist = preload.artists.joinToString { it.name }
            val artwork = preload.thumbnailUrl
            Timber.tag(TAG).i("interceptQueue [PRELOAD]: mediaId=${preload.id}, title=\"$title\", artist=\"$artist\", hasUri=${mediaItem.localConfiguration?.uri != null}")
            _state.value = _state.value.copy(
                pendingInterceptQueue = PendingDualPlayQueue(
                    queue = queue,
                    songTitle = title,
                    songArtist = artist,
                    artworkUri = artwork,
                    resolvedMediaItem = mediaItem,
                )
            )
        } else {
            Timber.tag(TAG).i("interceptQueue [ASYNC]: preloadItem is null, resolving via queue.getInitialStatus()")
            _state.value = _state.value.copy(
                pendingInterceptQueue = PendingDualPlayQueue(
                    queue = queue,
                    songTitle = "Loading...",
                )
            )
            scope.launch(Dispatchers.IO) {
                try {
                    val status = queue.getInitialStatus()
                    val target = status.items.getOrNull(status.mediaItemIndex) ?: status.items.firstOrNull()
                    if (target != null) {
                        val meta = target.mediaMetadata
                        val title = meta.title?.toString() ?: status.title ?: "Unknown"
                        val artist = meta.artist?.toString() ?: meta.subtitle?.toString()
                        val artwork = meta.artworkUri?.toString()
                        Timber.tag(TAG).i("interceptQueue resolved: mediaId=${target.mediaId}, title=\"$title\", artist=\"$artist\", hasUri=${target.localConfiguration?.uri != null}")
                        withContext(Dispatchers.Main) {
                            if (_state.value.pendingInterceptQueue?.queue === queue) {
                                _state.value = _state.value.copy(
                                    pendingInterceptQueue = PendingDualPlayQueue(
                                        queue = queue,
                                        songTitle = title,
                                        songArtist = artist,
                                        artworkUri = artwork,
                                        resolvedMediaItem = target,
                                    )
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "interceptQueue: failed to resolve initial status for queue")
                }
            }
        }
        return true
    }

    /** User chose "Add to Dual Play": load queue's item into Player B. */
    fun acceptInterceptAsDualPlay() {
        val pending = _state.value.pendingInterceptQueue ?: run {
            Timber.tag(TAG).w("acceptInterceptAsDualPlay: no pending intercept queue")
            return
        }
        _state.value = _state.value.copy(pendingInterceptQueue = null)

        scope.launch(Dispatchers.Main) {
            val item = pending.resolvedMediaItem ?: withContext(Dispatchers.IO) {
                try {
                    val status = pending.queue.getInitialStatus()
                    status.items.getOrNull(status.mediaItemIndex) ?: status.items.firstOrNull()
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "acceptInterceptAsDualPlay: failed to resolve initial status")
                    null
                }
            }

            if (item == null) {
                Timber.tag(TAG).w("acceptInterceptAsDualPlay: unable to obtain MediaItem")
                Toast.makeText(service, "Failed to load song for Dual Play", Toast.LENGTH_SHORT).show()
                return@launch
            }

            Timber.tag(TAG).i("acceptInterceptAsDualPlay: starting Player B with mediaId=${item.mediaId}")
            setMediaItemB(item)
            playB()
        }
    }

    /** User chose "Play instead": let the service handle it normally. */
    fun rejectInterceptAndPlay() {
        val pending = _state.value.pendingInterceptQueue ?: return
        _state.value = _state.value.copy(pendingInterceptQueue = null)
        // Dispatch to service directly (bypasses our intercept)
        service.playQueue(pending.queue)
    }

    /** User chose "Cancel": dismiss without doing anything. */
    fun cancelIntercept() {
        _state.value = _state.value.copy(pendingInterceptQueue = null)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Call from MusicService.onDestroy(). */
    fun destroy() {
        if (isEnabled) disable() else releasePlayerB()
    }

    // ── Private: Player B creation ─────────────────────────────────────────────

    private fun createAndConfigurePlayerB() {
        val context = service.applicationContext

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                PlaybackConstants.CROSSFADE_MIN_BUFFER_MS,
                PlaybackConstants.CROSSFADE_MAX_BUFFER_MS,
                2000, 2000,
            )
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(rightProcessor))
                .build()
        }

        // Use the full playback media source factory (resolves YouTube streams, cache, FLAC, and local files)
        val mediaSourceFactory = service.createMediaSourceFactory()

        val b = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)   // Amendment 5: headset button / unplug
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,   // Amendment 5: focus managed by DualPlayEngine
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)       // Amendment 5
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                addListener(playerBListener)
                volume = _state.value.volumeB
                setOffloadEnabled(false)
            }

        playerB = b
        startForegroundKeepAlive()
        Timber.tag(TAG).d("Player B created (offload=false)")
    }

    // ── Private: Player B event listener ──────────────────────────────────────

    private val playerBListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _state.value = _state.value.copy(playerBIsPlaying = playWhenReady)
            Timber.tag(TAG).d("Player B playWhenReady changed: $playWhenReady (reason=$reason)")
            if (playWhenReady) {
                // Keep service alive while B is playing (Amendment 5).
                service.cancelIdleStop()
                service.promoteToStartedService()
                service.ensureStartedAsForeground()
            } else {
                service.scheduleStopIfIdle()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(playerBPlaybackState = playbackState)
            Timber.tag(TAG).i("Player B playbackState: $playbackState (READY=${Player.STATE_READY})")
            if (playbackState == Player.STATE_READY && playerB?.playWhenReady == true) {
                if (!_state.value.splitViewActive) {
                    Timber.tag(TAG).i("Player B is READY and playing -> entering split view state")
                    _state.value = _state.value.copy(splitViewActive = true)
                    onSongBAdded()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.tag(TAG).d("Player B isPlaying changed: $isPlaying")
            if (isPlaying && !_state.value.splitViewActive) {
                Timber.tag(TAG).i("Player B started playing -> entering split view state")
                _state.value = _state.value.copy(splitViewActive = true)
                onSongBAdded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val errorMsg = error.localizedMessage ?: error.message ?: error.errorCodeName
            // Player B errors NEVER crash or stop Player A (Amendment safety).
            Timber.tag(TAG).e(error, "Player B error: %s (Player A unaffected)", errorMsg)
            _state.value = _state.value.copy(
                playerBError = errorMsg,
                splitViewActive = false,
                playerBMediaItem = null,
            )
            scope.launch(Dispatchers.Main) {
                Toast.makeText(service, "Song B playback error: $errorMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Private: headphone detection ───────────────────────────────────────────

    /**
     * Returns true if at least one of the permitted stereo-headphone device
     * types is currently connected as a sink.
     * Permitted types (Amendment 4): A2DP, BLE_HEADSET, WIRED_HEADSET,
     * WIRED_HEADPHONES, USB_HEADSET, USB_DEVICE.
     * Excluded: BLUETOOTH_SCO.
     */
    private fun isHeadphonesConnected(): Boolean {
        val audioManager = service.audioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.isSink && isPermittedHeadphoneType(device.type)
        }
    }

    private fun isPermittedHeadphoneType(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET)   return true
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) return true
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)   return true
        if (type == AudioDeviceInfo.TYPE_USB_HEADSET)      return true
        if (type == AudioDeviceInfo.TYPE_USB_DEVICE)       return true
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("NewApi")
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET) return true
        }
        // TYPE_BLUETOOTH_SCO is intentionally excluded.
        return false
    }

    // ── Private: mono audio accessibility check ────────────────────────────────

    private fun checkMonoAudioAccessibility() {
        val mono = android.provider.Settings.System.getString(
            service.contentResolver,
            "master_mono",
        )
        val isMonoOn = (mono == "1")
        DualPlayChannelState.isSystemMonoAudioEnabled = isMonoOn
        if (isMonoOn) {
            Timber.tag(TAG).w(
                "Dual Play: Android 'mono audio' accessibility setting is ON — " +
                "both channels will be merged by the OS; left/right separation will NOT work on this device.",
            )
        }
    }

    // ── Private: noisy broadcast ───────────────────────────────────────────────

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        service.registerReceiver(noisyReceiver, filter)
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { service.unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    // ── Private: helpers ───────────────────────────────────────────────────────

    private fun pausePlayerB() { playerB?.pause() }
    private fun pausePlayerA() { runCatching { service.player.pause() } }

    private fun releasePlayerB() {
        val b = playerB ?: return
        playerB = null
        runCatching { b.removeListener(playerBListener) }
        runCatching { b.pause() }
        runCatching { b.release() }
        Timber.tag(TAG).d("Player B released")
    }

    // ── Private: foreground keep-alive ────────────────────────────────────────

    private fun startForegroundKeepAlive() {
        stopForegroundKeepAlive()
        foregroundKeepAliveJob = scope.launch {
            while (isActive) {
                val b = playerB
                if (b != null && b.playWhenReady && b.playbackState != Player.STATE_ENDED) {
                    service.cancelIdleStop()
                    service.promoteToStartedService()
                }
                delay(10_000L)
            }
        }
    }

    private fun stopForegroundKeepAlive() {
        foregroundKeepAliveJob?.cancel()
        foregroundKeepAliveJob = null
    }

    // ── Private: format polling (debug screen "not applied" banner) ───────────

    private fun startFormatPolling() {
        stopFormatPolling()
        formatPollingJob = scope.launch {
            while (isActive) {
                val desc = DualPlayChannelState.unsupportedFormatDescription
                if (_state.value.channelSplitUnsupportedFormat != desc) {
                    _state.value = _state.value.copy(channelSplitUnsupportedFormat = desc)
                }
                delay(2_000L)
            }
        }
    }

    private fun stopFormatPolling() {
        formatPollingJob?.cancel()
        formatPollingJob = null
    }

    companion object {
        private const val TAG = "DualPlayEngine"

        /**
         * When true, adding Song B auto-complements Song A's ear assignment.
         * e.g. if A = L (default), B = R (default).
         * If false, Song B defaults to M (both ears).
         */
        const val AUTO_COMPLEMENT_EAR = true
    }

    // ── Private: ear helpers ───────────────────────────────────────────────────

    private fun earSelectionToMode(ear: EarSelection?): EarMode = when (ear) {
        EarSelection.L -> EarMode.LEFT_ONLY
        EarSelection.R -> EarMode.RIGHT_ONLY
        EarSelection.M, null -> EarMode.STEREO
    }

    /**
     * Called immediately after Song B is loaded.
     * Rule D:
     * - If A has no selection at split: A = M and B = M.
     * - If A = L at split: A keeps L and B = R (auto). If A = R: B = L.
     */
    private fun onSongBAdded() {
        val aEar = _state.value.earSelectionA
        if (aEar == null || aEar == EarSelection.M) {
            setEarA(EarSelection.M)
            setEarB(EarSelection.M)
        } else if (AUTO_COMPLEMENT_EAR) {
            val bEar = when (aEar) {
                EarSelection.L -> EarSelection.R
                EarSelection.R -> EarSelection.L
                else -> EarSelection.M
            }
            setEarB(bEar)
        } else {
            setEarB(EarSelection.M)
        }
        Timber.tag(TAG).d("Song B added: earA=${_state.value.earSelectionA} → earB=${_state.value.earSelectionB}")
    }
}
