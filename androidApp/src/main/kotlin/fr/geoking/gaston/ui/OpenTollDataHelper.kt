package fr.geoking.gaston.ui

import android.content.Context
import fr.geoking.gaston.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Default download URL for OpenTollData French highway toll JSON (AREA network).
 * Data: https://github.com/louis2038/OpenTollData, license ODbL-1.0.
 */
const val OPEN_TOLL_DATA_DOWNLOAD_URL = "https://raw.githubusercontent.com/louis2038/OpenTollData/main/TollPrice_DataBase/toll_price_AREA.json"

/** Filename for the downloaded toll data JSON. */
const val OPEN_TOLL_DATA_FILENAME = "toll_data.json"

/**
 * Helper for OpenTollData: download French highway toll JSON to app files dir.
 * Same pattern as [fr.geoking.gaston.agents.LlamatikModelHelper] (on-device model download).
 * Downloaded file is stored at [context.filesDir]/open_toll_data/[OPEN_TOLL_DATA_FILENAME].
 */
class OpenTollDataHelper(private val context: Context) {

    private fun tollDataDir(): File = File(context.filesDir, "open_toll_data")

    private fun fileForTollData(): File = File(tollDataDir(), OPEN_TOLL_DATA_FILENAME)

    /**
     * Returns true if toll data is available at the path stored in settings.
     */
    fun isTollDataDownloaded(settings: AppSettings): Boolean {
        val path = settings.tollDataPath ?: return false
        if (path.isBlank()) return false
        return File(path).exists()
    }

    /**
     * Returns true if the toll data file exists in app storage (default download location).
     */
    fun isTollDataFilePresent(): Boolean = fileForTollData().exists()

    /**
     * Path to show in UI.
     */
    fun getDisplayPath(settings: AppSettings): String {
        val path = settings.tollDataPath
        if (path.isNullOrBlank()) return "Not downloaded"
        return path
    }

    /**
     * Absolute path where the toll data file is (or will be) saved.
     */
    fun getDownloadDestinationPath(): String = fileForTollData().absolutePath

    /**
     * Downloads the OpenTollData JSON to app files dir. Reports progress (bytes read, total if known).
     * Returns the absolute path to use as [AppSettings.tollDataPath] on success.
     */
    suspend fun download(
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val destFile = fileForTollData()
        try {
            val url = URL(OPEN_TOLL_DATA_DOWNLOAD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${connection.responseCode}"))
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            destFile.parentFile?.mkdirs() ?: run {
                return@withContext Result.failure(Exception("Could not create open_toll_data directory"))
            }

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesDownloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        onProgress(bytesDownloaded, totalBytes)
                    }
                }
            }

            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            if (destFile.exists()) destFile.delete()
            Result.failure(e)
        }
    }
}
