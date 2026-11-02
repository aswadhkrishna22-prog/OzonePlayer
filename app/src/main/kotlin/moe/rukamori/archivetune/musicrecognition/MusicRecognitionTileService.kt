/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.musicrecognition

import android.Manifest
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import moe.rukamori.archivetune.R
import timber.log.Timber

class MusicRecognitionTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            val manageIntent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent =
                    PendingIntent.getActivity(
                        this,
                        0,
                        manageIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(manageIntent)
            }

            Toast.makeText(
                this,
                getString(R.string.ozone_search_overlay_permission_hint),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                this,
                getString(R.string.ozone_search_mic_permission_required),
                Toast.LENGTH_LONG,
            ).show()
        }

        val overlayIntent =
            Intent(this, OzoneSearchOverlayService::class.java).apply {
                action = OzoneSearchOverlayService.ACTION_START_SEARCH
            }

        unlockAndRun {
            try {
                ContextCompat.startForegroundService(this@MusicRecognitionTileService, overlayIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start OzoneSearchOverlayService")
            }

            try {
                val dummyDialog =
                    Dialog(this@MusicRecognitionTileService, android.R.style.Theme_Translucent_NoTitleBar).apply {
                        window?.setBackgroundDrawableResource(android.R.color.transparent)
                        setOnShowListener {
                            dismiss()
                        }
                    }
                showDialog(dummyDialog)
            } catch (e: Throwable) {
                Timber.w(e, "Could not collapse notification shade via showDialog")
            }
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.ozone_search)
            icon = Icon.createWithResource(this@MusicRecognitionTileService, R.drawable.search)
            updateTile()
        }
    }
}
