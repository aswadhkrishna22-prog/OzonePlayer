/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.musicrecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import moe.rukamori.archivetune.R

class OzoneSearchPermissionActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startOverlayService()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.ozone_search_mic_permission_required),
                    Toast.LENGTH_LONG,
                ).show()
            }
            closeActivity()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitions()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startOverlayService()
            closeActivity()
            return
        }

        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startOverlayService() {
        val overlayIntent =
            Intent(this, OzoneSearchOverlayService::class.java).apply {
                action = OzoneSearchOverlayService.ACTION_START_SEARCH
            }
        try {
            ContextCompat.startForegroundService(this, overlayIntent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.ozone_search_no_match), Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeActivity() {
        finish()
        disableTransitions()
    }

    private fun disableTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
