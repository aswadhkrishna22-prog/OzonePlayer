/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.musicrecognition

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.ACTION_SEARCH
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme

@AndroidEntryPoint
class OzoneSearchOverlayActivity : ComponentActivity() {

    private val viewModel: OzoneSearchOverlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        overridePendingTransition(android.R.anim.fade_in, 0)

        setContent {
            ArchiveTuneTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                val permissionLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (granted) {
                            viewModel.onPermissionGranted()
                        } else {
                            viewModel.onPermissionDenied()
                        }
                    }

                LaunchedEffect(Unit) {
                    val hasPermission =
                        ContextCompat.checkSelfPermission(
                            this@OzoneSearchOverlayActivity,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.startRecognition()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                OzoneSearchOverlayContent(
                    state = state,
                    onDismiss = { finish() },
                    onRetry = { viewModel.startRecognition() },
                    onPlayTrack = { track ->
                        val playIntent =
                            Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                                component = ComponentName(this@OzoneSearchOverlayActivity, MainActivity::class.java)
                                putExtra("query", track.searchQuery)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        startActivity(playIntent)
                        finish()
                    },
                    onOpenInOzone = { track ->
                        val searchIntent =
                            Intent(this@OzoneSearchOverlayActivity, MainActivity::class.java).apply {
                                action = ACTION_SEARCH
                                putExtra("search_query", track.searchQuery)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        startActivity(searchIntent)
                        finish()
                    },
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        viewModel.cancel()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
