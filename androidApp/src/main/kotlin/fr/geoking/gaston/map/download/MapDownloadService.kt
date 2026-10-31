package fr.geoking.gaston.map.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.geoking.gaston.MainActivity
import fr.geoking.gaston.R
import fr.geoking.gaston.auto.map.mapsforge.DownloadProgress
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Foreground [Service] that downloads offline map packs (Mapsforge / PMTiles) with HTTP resume.
 * Keeps the transfer alive when the Activity or AA screen is destroyed.
 *
 * Play Console: declare Foreground service type **Data sync**
 * (`FOREGROUND_SERVICE_DATA_SYNC`) before uploading a build that includes this service.
 */
class MapDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            downloadJob?.cancel()
            finishWith(Result.failure(Exception("Download cancelled")))
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL)
        val tempPath = intent?.getStringExtra(EXTRA_TEMP_PATH)
        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME)
        val userAgent = intent?.getStringExtra(EXTRA_USER_AGENT)
        if (url.isNullOrBlank() || tempPath.isNullOrBlank() || fileName.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val tempFile = File(tempPath)
        ensureChannel()
        val initial = buildNotification(
            title = getString(R.string.map_download_notification_title),
            text = getString(R.string.mapsforge_downloading, fileName),
            progress = null,
            indeterminate = true,
        )
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, initial, fgsType)

        downloadJob?.cancel()
        downloadJob = scope.launch {
            gate.withLock {
                try {
                    val client = runCatching { GlobalContext.get().get<HttpClient>() }.getOrNull()
                    if (client == null) {
                        val err = Exception("HttpClient not available")
                        MapDownloadProgressHub.publish(
                            DownloadProgress(fileName, 0, -1, error = err.message)
                        )
                        finishWith(Result.failure(err))
                        return@withLock
                    }
                    val result = ResumableHttpDownloader.download(
                        client = client,
                        url = url,
                        tempFile = tempFile,
                        fileName = fileName,
                        userAgent = userAgent,
                        onProgress = { progress ->
                            MapDownloadProgressHub.publish(progress)
                            updateNotification(progress)
                        },
                    )
                    finishWith(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Map download failed", e)
                    MapDownloadProgressHub.publish(
                        DownloadProgress(fileName, 0, -1, error = e.message ?: "Download failed")
                    )
                    finishWith(Result.failure(e))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun finishWith(result: Result<File>) {
        val deferred = activeDeferred
        activeDeferred = null
        deferred?.complete(result)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.map_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.map_download_notification_channel_desc)
                setShowBadge(false)
            }
        )
    }

    private fun updateNotification(progress: DownloadProgress) {
        val text = when {
            progress.error != null -> getString(R.string.mapsforge_download_error)
            progress.isComplete -> getString(R.string.mapsforge_download_success, progress.fileName)
            else -> getString(R.string.mapsforge_downloading, progress.fileName)
        }
        val notification = buildNotification(
            title = getString(R.string.map_download_notification_title),
            text = text,
            progress = progress.takeIf { !it.isComplete && it.error == null },
            indeterminate = progress.totalBytes <= 0 && !progress.isComplete && progress.error == null,
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: DownloadProgress?,
        indeterminate: Boolean,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(progress != null || indeterminate)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when {
            progress != null && progress.totalBytes > 0 -> {
                builder.setProgress(
                    100,
                    progress.progressPercent.coerceIn(0, 100),
                    false,
                )
            }
            indeterminate -> builder.setProgress(100, 0, true)
            else -> builder.setProgress(0, 0, false)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "MapDownloadService"
        const val CHANNEL_ID = "gaston_map_download"
        private const val NOTIFICATION_ID = 2101

        const val ACTION_DOWNLOAD = "fr.geoking.gaston.action.MAP_DOWNLOAD"
        const val ACTION_CANCEL = "fr.geoking.gaston.action.MAP_DOWNLOAD_CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_TEMP_PATH = "temp_path"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_USER_AGENT = "user_agent"

        private val gate = Mutex()
        @Volatile
        private var activeDeferred: CompletableDeferred<Result<File>>? = null

        /**
         * Starts the foreground service and suspends until the temp file download finishes.
         * Partial [tempFile] bytes are preserved on failure for a later resume.
         */
        suspend fun downloadToTemp(
            context: Context,
            url: String,
            tempFile: File,
            fileName: String,
            userAgent: String? = null,
        ): Result<File> {
            if (activeDeferred?.isActive == true) {
                val err = Exception(context.getString(R.string.mapsforge_download_in_progress))
                MapDownloadProgressHub.publish(
                    DownloadProgress(fileName, 0, -1, error = err.message)
                )
                return Result.failure(err)
            }
            val deferred = CompletableDeferred<Result<File>>()
            activeDeferred = deferred
            MapDownloadProgressHub.publish(DownloadProgress(fileName, 0, -1))
            val intent = Intent(context, MapDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TEMP_PATH, tempFile.absolutePath)
                putExtra(EXTRA_FILE_NAME, fileName)
                if (!userAgent.isNullOrBlank()) putExtra(EXTRA_USER_AGENT, userAgent)
            }
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                activeDeferred = null
                MapDownloadProgressHub.publish(
                    DownloadProgress(fileName, 0, -1, error = e.message ?: "Could not start download service")
                )
                deferred.complete(Result.failure(e))
            }
            return deferred.await()
        }
    }
}
