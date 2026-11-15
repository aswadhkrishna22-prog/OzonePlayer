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
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Custom [BackupAgentHelper] ensuring any pending migration of sensitive credentials
 * from `settings.preferences_pb` to `secure_settings.preferences_pb` is executed
 * synchronously BEFORE Android's backup framework packages files for Cloud Backup
 * or Device-to-Device transfer.
 *
 * Fail-Closed Guarantee:
 * In accordance with AOSP BackupAgent architecture, throwing [IOException] from
 * [onFullBackup] or [onBackup] aborts the backup operation with AGENT_ERROR, preventing
 * Android's framework from uploading un-migrated or corrupt state. Errors are never
 * silently swallowed.
 *
 * Restored files:
 * Any restored `secure_settings.preferences_pb` file is dropped and deleted, and
 * [runMigration] is invoked immediately in [onRestoreFinished] to partition legacy state.
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

    override fun onRestoreFile(
        data: ParcelFileDescriptor?,
        size: Long,
        destination: File?,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        super.onRestoreFile(data, size, destination, type, mode, mtime)
        if (destination != null && (destination.name == "secure_settings.preferences_pb" || destination.name.contains("secure_settings"))) {
            destination.delete()
            Timber.w("OzoneBackupAgent: removed restored secure_settings file to prevent untrusted overwrite")
        }
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        try {
            val store = applicationContext.dataStore
            if (store is SecureRoutedPreferencesDataStore) {
                runBlocking {
                    withTimeout(BACKUP_MIGRATION_TIMEOUT_MS) {
                        store.runMigration()
                    }
                }
                Timber.d("OzoneBackupAgent: migration verified after restore")
            }
        } catch (e: Exception) {
            Timber.e(e, "OzoneBackupAgent: post-restore migration error")
        }
    }

    @Throws(IOException::class)
    internal fun runPreBackupMigration(trigger: String) {
        val store = applicationContext.dataStore
        executeMigration(store, trigger)
    }

    @Throws(IOException::class)
    internal fun executeMigration(store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>, trigger: String) {
        if (store is SecureRoutedPreferencesDataStore) {
            try {
                runBlocking {
                    withTimeout(BACKUP_MIGRATION_TIMEOUT_MS) {
                        store.runMigration()
                    }
                }
                Timber.d("OzoneBackupAgent: migration verified before %s", trigger)
            } catch (e: Exception) {
                Timber.e(e, "OzoneBackupAgent: pre-backup migration error during %s", trigger)
                throw IOException("OzoneBackupAgent: Pre-backup migration failed during $trigger", e)
            }
        }
    }

    companion object {
        const val BACKUP_MIGRATION_TIMEOUT_MS = 5000L
    }
}
