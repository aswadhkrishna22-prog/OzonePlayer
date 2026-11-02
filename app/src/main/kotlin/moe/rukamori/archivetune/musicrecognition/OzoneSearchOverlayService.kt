/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.musicrecognition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ACTION_SEARCH
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class OzoneSearchOverlayService : Service() {

    @Inject
    lateinit var repository: MusicRecognitionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognitionJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    private val overlayState = MutableStateFlow<OverlayState>(OverlayState.Idle)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SEARCH) {
            dismissOverlay()
            return START_NOT_STICKY
        }

        if (!startForegroundSilently()) {
            dismissOverlay()
            return START_NOT_STICKY
        }

        showFloatingOverlay()
        startRecognition()
        return START_NOT_STICKY
    }

    private fun startForegroundSilently(): Boolean {
        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.search)
                .setContentTitle(getString(R.string.ozone_search))
                .setContentText(getString(R.string.ozone_search_listening))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasMicPerm =
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val foregroundServiceType =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasMicPerm) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Throwable) {
            Timber.e(e, "Failed to start foreground service silently")
            false
        }
    }

    private fun showFloatingOverlay() {
        if (overlayComposeView != null) return

        val owner = OverlayLifecycleOwner()
        overlayLifecycleOwner = owner

        val composeView =
            ComposeView(this).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool,
                )
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)

                setContent {
                    ArchiveTuneTheme {
                        val state by overlayState.collectAsStateWithLifecycle()
                        OzoneSearchOverlayContent(
                            state = state,
                            onDismiss = { dismissOverlay() },
                            onRetry = { startRecognition() },
                            onPlayTrack = { track ->
                                val playIntent =
                                    Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                                        component =
                                            ComponentName(
                                                this@OzoneSearchOverlayService,
                                                MainActivity::class.java,
                                            )
                                        putExtra("query", track.searchQuery)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                startActivity(playIntent)
                                dismissOverlay()
                            },
                            onOpenInOzone = { track ->
                                val searchIntent =
                                    Intent(this@OzoneSearchOverlayService, MainActivity::class.java).apply {
                                        action = ACTION_SEARCH
                                        putExtra("search_query", track.searchQuery)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                startActivity(searchIntent)
                                dismissOverlay()
                            },
                        )
                    }
                }
            }

        val layoutParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }

        try {
            windowManager?.addView(composeView, layoutParams)
            overlayComposeView = composeView
        } catch (e: Exception) {
            Timber.e(e, "Failed to add floating overlay view to WindowManager")
            dismissOverlay()
        }
    }

    private fun startRecognition() {
        recognitionJob?.cancel()
        recognitionJob =
            serviceScope.launch {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this@OzoneSearchOverlayService,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    overlayState.value =
                        OverlayState.Error(
                            message = getString(R.string.ozone_search_mic_permission_required),
                            canRetry = false,
                        )
                    return@launch
                }
                overlayState.value = OverlayState.Listening
                try {
                    val samples = repository.captureAudio()
                    if (samples.isEmpty()) {
                        overlayState.value = OverlayState.Error("Could not capture audio", canRetry = true)
                        return@launch
                    }

                    overlayState.value = OverlayState.Processing
                    val result = repository.recognize(samples)
                    result.fold(
                        onSuccess = { track ->
                            overlayState.value = OverlayState.Success(track)
                            try {
                                repository.saveToHistory(track)
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to save recognition history")
                            }
                        },
                        onFailure = { throwable ->
                            val msg =
                                if (
                                    throwable.message?.contains("No match", ignoreCase = true) == true ||
                                    throwable.message?.contains("404") == true
                                ) {
                                    getString(R.string.ozone_search_no_match)
                                } else {
                                    getString(R.string.ozone_search_no_match)
                                }
                            overlayState.value = OverlayState.Error(msg, canRetry = true)
                        },
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "Overlay audio recognition failed")
                    overlayState.value = OverlayState.Error(getString(R.string.ozone_search_no_match), canRetry = true)
                }
            }
    }

    private fun dismissOverlay() {
        recognitionJob?.cancel()
        recognitionJob = null

        overlayComposeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Timber.w(e, "Error removing overlay view")
            }
            overlayComposeView = null
        }

        overlayLifecycleOwner?.destroy()
        overlayLifecycleOwner = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissOverlay()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.ozone_search),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Ozone Search Floating Recognition Service"
                    setShowBadge(false)
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START_SEARCH = "moe.rukamori.archivetune.action.START_OZONE_SEARCH"
        const val ACTION_STOP_SEARCH = "moe.rukamori.archivetune.action.STOP_OZONE_SEARCH"
        private const val NOTIFICATION_ID = 91823
        private const val CHANNEL_ID = "ozone_search_overlay"
    }
}
