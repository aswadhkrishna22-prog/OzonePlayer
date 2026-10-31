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
import moe.rukamori.archivetune.playback.applyEqSettingsToEffects
import moe.rukamori.archivetune.playback.cancelCrossfade
import moe.rukamori.archivetune.playback.createMediaSourceFactory
import moe.rukamori.archivetune.playback.playbackAudioAttributes
import moe.rukamori.archivetune.playback.sendCloseAudioEffectSessionBroadcast
import moe.rukamori.archivetune.playback.sendOpenAudioEffectSessionBroadcast
import moe.rukamori.archivetune.playback.PlaybackConstants
import moe.rukamori.archivetune.playback.processor.SpatialProcessor
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.extensions.toMediaItem
import timber.log.Timber
import java.util.concurrent.TimeUnit
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.Timeline
import moe.rukamori.archivetune.extensions.getQueueWindows
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaMetadata
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

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
    val playerBRepeatMode: Int = Player.REPEAT_MODE_OFF,
    val playerBShuffleModeEnabled: Boolean = false,
    val playerBIsLiked: Boolean = false,
    val playerBQueueIndex: Int = -1,
    val playerBQueueWindows: List<Timeline.Window> = emptyList(),
    val playerBQueueDurationMs: Long = 0L,
    val playerBQueueSongCount: Int = 0,
    val playerBDuration: Long = 0L,
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

    private val _playerBPosition = MutableStateFlow(0L)
    val playerBPosition: StateFlow<Long> = _playerBPosition.asStateFlow()

    private var playerBPositionJob: Job? = null

    val isEnabled: Boolean get() = _state.value.enabled

    // ── Player B ───────────────────────────────────────────────────────────────

    private var playerB: ExoPlayer? = null
    private var currentQueueB: Queue? = null

    /**
     * RIGHT-channel processor owned by Player B's sink.
     * Created once and reused for the lifetime of this engine.
     */
    private val rightProcessor = ChannelSplitProcessor(ProcessorSide.RIGHT)

    // ── Saved settings (restored on disable) ──────────────────────────────────

    private var savedSpatialEnabled   = false
    private var savedVirtualizerEnabled = false
    private var savedEqualizerEnabled = false
    private var savedBassBoostEnabled = false
    private var savedLoudnessEnhancerEnabled = false
    private var savedAudioNormalizationEnabled = false
    private var savedAudioAttributesA: AudioAttributes? = null
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

        // ── 3. Disable all AudioEffects & normalization (leak prevention) ─────
        savedVirtualizerEnabled = service.virtualizer?.let {
            runCatching { it.enabled }.getOrDefault(false)
        } ?: false
        service.virtualizer?.let { runCatching { it.enabled = false } }

        savedEqualizerEnabled = service.equalizer?.let {
            runCatching { it.enabled }.getOrDefault(false)
        } ?: false
        service.equalizer?.let { runCatching { it.enabled = false } }

        savedBassBoostEnabled = service.bassBoost?.let {
            runCatching { it.enabled }.getOrDefault(false)
        } ?: false
        service.bassBoost?.let { runCatching { it.enabled = false } }

        savedLoudnessEnhancerEnabled = service.loudnessEnhancer?.let {
            runCatching { it.enabled }.getOrDefault(false)
        } ?: false
        service.loudnessEnhancer?.let { runCatching { it.enabled = false } }

        savedAudioNormalizationEnabled = service.audioNormalizationEnabled
        service.audioNormalizationEnabled = false

        // Detach any system-level OEM audio effect sessions (e.g. Dolby Atmos / Dirac) from Player A
        runCatching { service.sendCloseAudioEffectSessionBroadcast(service.localPlayer.audioSessionId) }

        // ── 4. Opt out of platform spatialization for Player A ────────────────
        savedAudioAttributesA = service.localPlayer.audioAttributes
        val dualPlayAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_NEVER)
            .build()
        service.localPlayer.setAudioAttributes(dualPlayAttrs, false)

        // ── 5. Activate channel-split mode ────────────────────────────────────
        DualPlayChannelState.mode  = ChannelMode.ACTIVE
        DualPlayChannelState.modeA = EarMode.STEREO   // Normal stereo until user picks L/R/M
        DualPlayChannelState.modeB = EarMode.STEREO

        // ── 6. Build Player B (with SPATIALIZATION_BEHAVIOR_NEVER) ────────────
        createAndConfigurePlayerB()

        // ── 7. Register becoming-noisy receiver ───────────────────────────────
        registerNoisyReceiver()

        // ── 8. Start format polling (for debug screen) ────────────────────────
        startFormatPolling()

        // ── 9. Disable audio offload so processors are never bypassed ──────────
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

        // Restore Player A AudioAttributes
        val originalAttrs = savedAudioAttributesA ?: service.playbackAudioAttributes()
        service.localPlayer.setAudioAttributes(originalAttrs, false)
        savedAudioAttributesA = null

        // Restore SpatialProcessor
        SpatialProcessor.instance.enabled = savedSpatialEnabled

        // Restore audio effects & normalization
        service.equalizer?.let { runCatching { it.enabled = savedEqualizerEnabled } }
        service.bassBoost?.let { runCatching { it.enabled = savedBassBoostEnabled } }
        service.virtualizer?.let { runCatching { it.enabled = savedVirtualizerEnabled } }
        service.loudnessEnhancer?.let { runCatching { it.enabled = savedLoudnessEnhancerEnabled } }
        service.audioNormalizationEnabled = savedAudioNormalizationEnabled
        runCatching { service.applyEqSettingsToEffects(service.desiredEqSettings.value) }
        runCatching { service.sendOpenAudioEffectSessionBroadcast(service.localPlayer.audioSessionId) }

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

    fun getPlayerB(): ExoPlayer? = playerB

    fun playA()  { service.player.play() }
    fun pauseA() { service.player.pause() }
    fun seekA(posMs: Long) { service.player.seekTo(posMs) }
    fun setVolumeA(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        service.localPlayer.volume = clamped
        _state.value = _state.value.copy(volumeA = clamped)
    }

    fun playB() {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: run {
                Timber.tag(TAG).w("playB: playerB is null")
                return@launch
            }
            Timber.tag(TAG).i("playB: state=${b.playbackState}, playWhenReady=${b.playWhenReady}")
            if (b.playbackState == Player.STATE_ENDED) {
                b.seekTo(0, 0L)
            }
            b.play()
        }
    }

    fun pauseB() {
        scope.launch(Dispatchers.Main) {
            playerB?.pause()
        }
    }

    fun seekB(posMs: Long) {
        scope.launch(Dispatchers.Main) {
            playerB?.seekTo(posMs)
            _playerBPosition.value = posMs
        }
    }

    fun getPlayerBPosition(): Long = playerB?.currentPosition ?: 0L
    fun getPlayerBDuration(): Long = playerB?.duration?.takeIf { it > 0 } ?: 0L

    fun nextB() {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: return@launch
            if (b.hasNextMediaItem()) {
                b.seekToNextMediaItem()
            }
        }
    }

    fun prevB() {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: return@launch
            if (b.currentPosition > 3000L) {
                b.seekTo(0L)
                _playerBPosition.value = 0L
            } else if (b.hasPreviousMediaItem()) {
                b.seekToPreviousMediaItem()
            } else {
                b.seekTo(0L)
                _playerBPosition.value = 0L
            }
        }
    }

    fun toggleRepeatB() {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: return@launch
            val nextMode = when (b.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            b.repeatMode = nextMode
            _state.value = _state.value.copy(playerBRepeatMode = nextMode)
        }
    }

    fun toggleShuffleB() {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: return@launch
            val nextShuffle = !b.shuffleModeEnabled
            b.shuffleModeEnabled = nextShuffle
            _state.value = _state.value.copy(playerBShuffleModeEnabled = nextShuffle)
            updatePlayerBQueueWindows()
        }
    }

    fun playQueueItemB(index: Int) {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: return@launch
            if (index in 0 until b.mediaItemCount) {
                b.seekToDefaultPosition(index)
                b.play()
            }
        }
    }

    fun removeQueueItemB(index: Int) {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: return@launch
            if (index in 0 until b.mediaItemCount) {
                b.removeMediaItem(index)
                updatePlayerBQueueWindows()
            }
        }
    }

    fun moveQueueItemB(fromIndex: Int, toIndex: Int) {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: return@launch
            if (fromIndex in 0 until b.mediaItemCount && toIndex in 0 until b.mediaItemCount) {
                b.moveMediaItem(fromIndex, toIndex)
                updatePlayerBQueueWindows()
            }
        }
    }

    fun findWindowIndexByUidB(uid: Any?): Int {
        val b = playerB ?: return -1
        val timeline = b.currentTimeline
        if (timeline.isEmpty || uid == null) return -1
        val window = Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            if (window.uid == uid) return i
        }
        return -1
    }

    private fun updatePlayerBQueueWindows() {
        val b = playerB ?: return
        val windows = b.getQueueWindows()
        val totalDur = windows.sumOf { (it.mediaItem.metadata?.duration ?: 0).toLong() } * 1000L
        _state.value = _state.value.copy(
            playerBQueueWindows = windows,
            playerBQueueIndex = b.currentMediaItemIndex,
            playerBQueueSongCount = windows.size,
            playerBQueueDurationMs = totalDur,
        )
    }

    private var playerBLikeJob: Job? = null
    private val _playerBIsLiked = MutableStateFlow(false)
    val playerBIsLiked: StateFlow<Boolean> = _playerBIsLiked.asStateFlow()

    private fun observePlayerBLike(mediaId: String?) {
        playerBLikeJob?.cancel()
        if (mediaId.isNullOrBlank()) {
            _playerBIsLiked.value = false
            _state.value = _state.value.copy(playerBIsLiked = false)
            return
        }
        playerBLikeJob = scope.launch(Dispatchers.IO) {
            service.database.song(mediaId).collect { song ->
                val liked = song?.song?.liked == true
                withContext(Dispatchers.Main) {
                    _playerBIsLiked.value = liked
                    _state.value = _state.value.copy(playerBIsLiked = liked)
                }
            }
        }
    }

    fun toggleLikeB() {
        val item = _state.value.playerBMediaItem ?: return
        val mediaId = item.mediaId
        scope.launch(Dispatchers.IO) {
            try {
                val metadata = item.toMediaMetadata()
                val songEntity = service.database.getSongById(mediaId) ?: run {
                    service.database.insert(metadata) { it.copy(isLocal = mediaId.isLocalMediaId()) }
                    service.database.getSongById(mediaId)
                } ?: return@launch

                val source = LikeSourceResolver.resolve(
                    mediaId = mediaId,
                    spotifyTrackId = metadata.spotifyTrackId,
                    queue = null,
                    isLocal = songEntity.song.isLocal,
                )
                val updated = songEntity.song.localToggleLike(source)
                service.database.query {
                    update(updated)
                }
                Timber.tag(TAG).i("toggleLikeB: song=$mediaId liked=${updated.liked}")
                val spotifyId = if (!metadata.spotifyTrackId.isNullOrBlank()) {
                    metadata.spotifyTrackId
                } else if (LikeSourceResolver.isSpotifyId(updated.id, updated.isLocal)) {
                    updated.id
                } else null

                service.syncUtils.likeSong(updated, source, spotifyId)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "toggleLikeB failed for mediaId=$mediaId")
            }
        }
    }

    fun setVolumeB(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        scope.launch(Dispatchers.Main) {
            playerB?.volume = clamped
            _state.value = _state.value.copy(volumeB = clamped)
        }
    }

    private fun updatePlayerBPositionPolling(isPlaying: Boolean) {
        playerBPositionJob?.cancel()
        if (isPlaying) {
            playerBPositionJob = scope.launch {
                while (isActive) {
                    val b = playerB
                    if (b != null) {
                        _playerBPosition.value = b.currentPosition
                    }
                    delay(300L)
                }
            }
        } else {
            val b = playerB
            if (b != null) {
                _playerBPosition.value = b.currentPosition
            }
        }
    }

    /** Set the full queue for Player B and prepare (starting at startIndex). */
    fun setQueueB(
        items: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        queue: Queue? = null,
    ) {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: run {
                Timber.tag(TAG).w("setQueueB: playerB is null")
                return@launch
            }
            currentQueueB = queue
            val validIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            Timber.tag(TAG).i("setQueueB: count=${items.size}, startIndex=$validIndex")
            b.setMediaItems(items, validIndex, startPositionMs)
            b.prepare()
            val currentItem = items.getOrNull(validIndex)
            _state.value = _state.value.copy(
                playerBMediaItem = currentItem,
                playerBError = null,
            )
            if (currentItem != null) {
                observePlayerBLike(currentItem.mediaId)
            }
            updatePlayerBQueueWindows()
        }
    }

    /** Append multiple media items to Player B's playlist without interrupting playback. */
    fun addMediaItemsB(items: List<MediaItem>) {
        if (items.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: run {
                setQueueB(items, 0, 0L)
                playB()
                return@launch
            }
            b.addMediaItems(items)
            updatePlayerBQueueWindows()
        }
    }

    /** Set the media item for Player B and prepare (splitViewActive will be activated once ready/playing). */
    fun setMediaItemB(item: MediaItem) {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: run {
                Timber.tag(TAG).w("setMediaItemB: playerB is null")
                return@launch
            }
            val uri = item.localConfiguration?.uri
            Timber.tag(TAG).i("setMediaItemB: mediaId=${item.mediaId}, uri=$uri, hasUri=${uri != null}, state=${b.playbackState}")
            b.setMediaItem(item)
            b.prepare()
            _state.value = _state.value.copy(
                playerBMediaItem = item,
                playerBError = null,
            )
            observePlayerBLike(item.mediaId)
            updatePlayerBQueueWindows()
        }
    }

    /** Append a media item to Player B's playlist without interrupting current playback. */
    fun addMediaItemB(item: MediaItem) {
        scope.launch(Dispatchers.Main) {
            val b = playerB ?: run {
                setMediaItemB(item)
                playB()
                return@launch
            }
            b.addMediaItem(item)
            updatePlayerBQueueWindows()
        }
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

    /** User chose "Add to Dual Play" (when B does not exist) or "Replace Song B" (when B exists). */
    fun acceptInterceptAsDualPlay() {
        val pending = _state.value.pendingInterceptQueue ?: run {
            Timber.tag(TAG).w("acceptInterceptAsDualPlay: no pending intercept queue")
            return
        }
        _state.value = _state.value.copy(pendingInterceptQueue = null)

        scope.launch(Dispatchers.Main) {
            val hideExplicit = service.dataStore.get(HideExplicitKey, false)
            val hideVideo = service.dataStore.get(HideVideoKey, false)
            val queue = pending.queue

            // Fast-path: if preloadItem exists, start immediately
            val preload = queue.preloadItem
            if (preload != null) {
                val fastItem = pending.resolvedMediaItem ?: preload.toMediaItem()
                Timber.tag(TAG).i("acceptInterceptAsDualPlay [fast-path]: setting Player B with mediaId=${fastItem.mediaId}")
                setMediaItemB(fastItem)
                playB()
            }

            val initialStatus = withContext(Dispatchers.IO) {
                try {
                    queue.getInitialStatus().filterExplicit(hideExplicit).filterVideo(hideVideo)
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "acceptInterceptAsDualPlay: failed to resolve initial status")
                    null
                }
            }

            if (initialStatus == null || initialStatus.items.isEmpty()) {
                if (preload == null) {
                    Timber.tag(TAG).w("acceptInterceptAsDualPlay: unable to obtain MediaItem")
                    Toast.makeText(service, "Failed to load song for Dual Play", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val items = initialStatus.items
            val targetIndex = initialStatus.mediaItemIndex.coerceIn(0, items.lastIndex)

            if (preload != null && playerB != null) {
                val preloadMediaId = preload.id
                val preloadIndexInItems = items.indexOfFirst { it.mediaId == preloadMediaId }.takeIf { it >= 0 } ?: targetIndex
                val before = items.subList(0, preloadIndexInItems)
                val after = items.subList(preloadIndexInItems + 1, items.size)
                val b = playerB!!
                if (before.isNotEmpty()) {
                    b.addMediaItems(0, before)
                }
                if (after.isNotEmpty()) {
                    b.addMediaItems(after)
                }
                currentQueueB = queue
                updatePlayerBQueueWindows()
            } else {
                Timber.tag(TAG).i("acceptInterceptAsDualPlay: setting full queue of ${items.size} items at index $targetIndex")
                setQueueB(
                    items = items,
                    startIndex = targetIndex,
                    startPositionMs = initialStatus.position,
                    queue = queue
                )
                playB()
            }
        }
    }

    /** User chose "Add to Song B queue": append item(s) to Player B's playlist without interrupting playback. */
    fun addInterceptToDualPlayQueue() {
        val pending = _state.value.pendingInterceptQueue ?: return
        _state.value = _state.value.copy(pendingInterceptQueue = null)

        scope.launch(Dispatchers.Main) {
            val hideExplicit = service.dataStore.get(HideExplicitKey, false)
            val hideVideo = service.dataStore.get(HideVideoKey, false)
            val initialStatus = withContext(Dispatchers.IO) {
                try {
                    pending.queue.getInitialStatus().filterExplicit(hideExplicit).filterVideo(hideVideo)
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "addInterceptToDualPlayQueue: failed to resolve initial status")
                    null
                }
            }

            val items = initialStatus?.items?.takeIf { it.isNotEmpty() }
                ?: pending.resolvedMediaItem?.let { listOf(it) }

            if (items.isNullOrEmpty()) {
                Toast.makeText(service, "Failed to load song", Toast.LENGTH_SHORT).show()
                return@launch
            }

            addMediaItemsB(items)
            Toast.makeText(service, "Added to Song B queue", Toast.LENGTH_SHORT).show()
        }
    }

    /** User chose "Replace Song A" (when B exists): replaces only A's song, split stays, B keeps playing. */
    fun replaceSongA() {
        val pending = _state.value.pendingInterceptQueue ?: return
        _state.value = _state.value.copy(pendingInterceptQueue = null)
        Timber.tag(TAG).i("replaceSongA: playing new queue on Player A while keeping Player B playing in split view")
        service.playQueue(pending.queue)
    }

    /** User chose "Play instead" (when B does not exist): normal playback on A, no split. */
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
                    .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
                    .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_NEVER)
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
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Timber.tag(TAG).d("Player B onMediaItemTransition: mediaId=${mediaItem?.mediaId}, reason=$reason")
            val dur = playerB?.duration?.takeIf { it > 0 } ?: 0L
            _state.value = _state.value.copy(
                playerBMediaItem = mediaItem,
                playerBDuration = dur,
                playerBQueueIndex = playerB?.currentMediaItemIndex ?: -1,
            )
            observePlayerBLike(mediaItem?.mediaId)
            updatePlayerBQueueWindows()

            // Pagination continuation for Player B
            val b = playerB
            val q = currentQueueB
            if (b != null && q != null && (b.mediaItemCount - b.currentMediaItemIndex) <= 5 && q.hasNextPage()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val nextItems = q.nextPage()
                        if (nextItems.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                playerB?.addMediaItems(nextItems)
                                updatePlayerBQueueWindows()
                            }
                        }
                    } catch (t: Throwable) {
                        Timber.tag(TAG).w(t, "Failed to load next page for Player B queue")
                    }
                }
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            Timber.tag(TAG).d("Player B onTimelineChanged: windowCount=${timeline.windowCount}, reason=$reason")
            updatePlayerBQueueWindows()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            Timber.tag(TAG).d("Player B onShuffleModeEnabledChanged: $shuffleModeEnabled")
            _state.value = _state.value.copy(playerBShuffleModeEnabled = shuffleModeEnabled)
            updatePlayerBQueueWindows()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val isPlayingNow = playerB?.let { it.playWhenReady && it.playbackState != Player.STATE_ENDED } ?: playWhenReady
            _state.value = _state.value.copy(playerBIsPlaying = isPlayingNow)
            Timber.tag(TAG).d("Player B playWhenReady changed: $playWhenReady, isPlayingNow=$isPlayingNow (reason=$reason)")
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
            val dur = playerB?.duration?.takeIf { it > 0 } ?: 0L
            val isPlayingNow = playerB?.let { it.playWhenReady && playbackState != Player.STATE_ENDED } ?: false
            _state.value = _state.value.copy(
                playerBPlaybackState = playbackState,
                playerBDuration = dur,
                playerBIsPlaying = isPlayingNow,
            )
            Timber.tag(TAG).i("Player B playbackState: $playbackState (READY=${Player.STATE_READY}, ENDED=${Player.STATE_ENDED})")
            if (playbackState == Player.STATE_ENDED) {
                Timber.tag(TAG).i("Player B queue ended. Stopping B and remaining paused at last song.")
                playerB?.playWhenReady = false
                _state.value = _state.value.copy(playerBIsPlaying = false)
            } else if (playbackState == Player.STATE_READY && playerB?.playWhenReady == true) {
                if (!_state.value.splitViewActive) {
                    Timber.tag(TAG).i("Player B is READY and playing -> entering split view state")
                    _state.value = _state.value.copy(splitViewActive = true)
                    onSongBAdded()
                }
            }
            updatePlayerBQueueWindows()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Timber.tag(TAG).d("Player B isPlaying changed: $isPlaying")
            updatePlayerBPositionPolling(isPlaying)
            if (isPlaying && !_state.value.splitViewActive) {
                Timber.tag(TAG).i("Player B started playing -> entering split view state")
                _state.value = _state.value.copy(splitViewActive = true)
                onSongBAdded()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.value = _state.value.copy(playerBRepeatMode = repeatMode)
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
        playerBPositionJob?.cancel()
        playerBPositionJob = null
        playerBLikeJob?.cancel()
        playerBLikeJob = null
        currentQueueB = null
        _playerBPosition.value = 0L
        _playerBIsLiked.value = false
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
