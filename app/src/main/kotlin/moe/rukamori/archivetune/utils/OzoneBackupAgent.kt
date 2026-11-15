/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Custom [BackupAgentHelper] ensuring any pending migration of sensitive credentials
 * from `settings.preferences_pb` to `secure_settings.preferences_pb` is executed
 * synchronously BEFORE Android's backup framework packages files for Cloud Backup
 * or Device-to-Device transfer.
 *
 * This mitigates the risk on existing installations where the app is updated in the
 * background (e.g. by Google Play) and Android initiates an automated cloud backup
 * before the user has launched the application.
 *
 * Known Android Backup Limitations:
 * 1. Historical Snapshots: If an existing device previously performed an Auto Backup
 *    under an older version of the app, Google Drive may already store an older snapshot
 *    of `settings.preferences_pb` containing credentials. That remote snapshot cannot be
 *    retroactively rewritten by local client code until a new backup cycle completes.
 * 2. Unencrypted at Rest: Excluding `secure_settings.preferences_pb` from backup prevents
 *    cloud extraction, but internal storage files remain unencrypted at rest without
 *    hardware-backed Keystore encryption.
 * 3. OEM / ADB Tools: Low-level tools like `adb backup` or rooted transfer utilities
 *    bypass `data_extraction_rules.xml`.
 */
class OzoneBackupAgent : BackupAgentHelper() {

    override fun onFullBackup(data: FullBackupDataOutput?) {
        runPreBackupMigration("onFullBackup")
        super.onFullBackup(data)
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
        runPreBackupMigration("onBackup")
        super.onBackup(oldState, data, newState)
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        runPreBackupMigration("onRestoreFinished")
    }

    internal fun runPreBackupMigration(trigger: String) {
        try {
            val store = applicationContext.dataStore
            if (store is SecureRoutedPreferencesDataStore) {
                runBlocking {
                    store.runMigration()
                }
                Timber.d("OzoneBackupAgent: migration verified before %s", trigger)
            }
        } catch (e: Exception) {
            Timber.e(e, "OzoneBackupAgent: pre-backup migration error during %s", trigger)
        }
    }
}
