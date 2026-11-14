package moe.rukamori.archivetune.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DownloadLocationUriKey
import moe.rukamori.archivetune.constants.FlacQuality
import moe.rukamori.archivetune.constants.FlacDownloadQualityKey
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

class FlacDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val safDirectoryManager = SafDirectoryManager(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString("songId") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: return@withContext Result.failure()
        val artist = inputData.getString("artist") ?: return@withContext Result.failure()
        val album = inputData.getString("album") ?: return@withContext Result.failure()

        createNotificationChannel()
        setForeground(createForegroundInfo(title))

        val treeUriString = context.dataStore.getAsync(DownloadLocationUriKey, "")

        val entryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, FlacDownloaderEntryPoint::class.java)

        val song =
            entryPoint.database().song(songId).first()
                ?: Song(
                    song = SongEntity(id = songId, title = title, albumName = album),
                    artists = listOf(ArtistEntity(id = artist, name = artist)),
                )

        val quality =
            context.dataStore.getAsync(FlacDownloadQualityKey, FlacQuality.HI_RES.name).toEnum(FlacQuality.HI_RES)

        val streamUrl = entryPoint.losslessStreamResolver().resolve(song, quality)
        if (streamUrl == null) {
            showDownloadError(title)
            return@withContext Result.failure()
        }

        val fileName = "${sanitizeFileName(title)}.flac"
        var outputStream: OutputStream? = null
        var deleteFile: () -> Unit = {}
        var savedPath = ""

        if (treeUriString.isNotEmpty()) {
            val treeUri = Uri.parse(treeUriString)
            val albumDir = safDirectoryManager.getOrCreateAlbumDirectory(treeUri, artist, album)
                ?: return@withContext Result.failure()

            val existingFile = albumDir.findFile(fileName)
            if (existingFile != null && existingFile.exists()) {
                entryPoint.database().update(song.song.copy(dateDownload = LocalDateTime.now()))
                return@withContext Result.success()
            }

            val file = albumDir.createFile("audio/flac", fileName) ?: return@withContext Result.failure()
            deleteFile = { file.delete() }
            outputStream = context.contentResolver.openOutputStream(file.uri)
            savedPath = file.uri.path ?: fileName
        } else {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val ozoneDir = File(musicDir, "OzonePlayer")
            val artistDir = File(ozoneDir, sanitizeFileName(artist))
            val albumDir = File(artistDir, sanitizeFileName(album))
            if (!albumDir.exists()) {
                albumDir.mkdirs()
            }
            val file = File(albumDir, fileName)
            if (file.exists()) {
                entryPoint.database().update(song.song.copy(dateDownload = LocalDateTime.now()))
                return@withContext Result.success()
            }
            file.createNewFile()
            deleteFile = { file.delete() }
            outputStream = FileOutputStream(file)
            savedPath = file.absolutePath
        }

        if (outputStream == null) {
            return@withContext Result.failure()
        }

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            val url = URL(streamUrl.url)
            connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            connection.setRequestProperty("Referer", "https://music.youtube.com/")
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                deleteFile()
                return@withContext Result.failure()
            }

            inputStream = connection.inputStream
            outputStream.use { out ->
                inputStream.copyTo(out)
            }
            entryPoint.database().update(song.song.copy(dateDownload = LocalDateTime.now()))
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.flac_download_saved, savedPath), Toast.LENGTH_SHORT).show()
            }
            Result.success()
        } catch (e: Exception) {
            deleteFile()
            showDownloadError(title)
            return@withContext Result.failure()
        } finally {
            inputStream?.close()
            connection?.disconnect()
            safDirectoryManager.clearCache()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    "download",
                    context.getString(R.string.downloading),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    private fun showDownloadError(title: String) {
        createNotificationChannel()
        val notification =
            NotificationCompat.Builder(context, "download")
                .setContentTitle(context.getString(R.string.flac_download_failed))
                .setContentText(title)
                .setSmallIcon(R.drawable.error)
                .build()
        NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, "download")
            .setContentTitle(context.getString(R.string.downloading))
            .setContentText(title)
            .setSmallIcon(R.drawable.download)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(
                title.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                title.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(title.hashCode(), notification)
        }
    }
}
