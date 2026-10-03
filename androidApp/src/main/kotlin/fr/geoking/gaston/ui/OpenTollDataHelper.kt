package fr.geoking.gaston.ui

import android.content.Context
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.api.toll.OpenTollDataMerger
import fr.geoking.gaston.api.toll.OpenTollDataModel
import fr.geoking.gaston.api.toll.OpenTollDataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Known OpenTollData JSON filenames under TollPrice_DataBase/.
 * AREA is published today; others are attempted and skipped on 404 until upstream adds them.
 * Data: https://github.com/louis2038/OpenTollData, license ODbL-1.0.
 */
val OPEN_TOLL_DATA_FILENAMES: List<String> = listOf(
    "toll_price_AREA.json",
    "toll_price_ASF.json",
    "toll_price_APRR.json",
    "toll_price_COFIROUTE.json",
    "toll_price_SANEF.json",
)

const val OPEN_TOLL_DATA_RAW_BASE =
    "https://raw.githubusercontent.com/louis2038/OpenTollData/main/TollPrice_DataBase"

/** @deprecated Prefer [OPEN_TOLL_DATA_FILENAMES]; kept for callers expecting a single URL. */
const val OPEN_TOLL_DATA_DOWNLOAD_URL =
    "$OPEN_TOLL_DATA_RAW_BASE/toll_price_AREA.json"

/** Filename for the merged toll data JSON stored on device. */
const val OPEN_TOLL_DATA_FILENAME = "toll_data.json"

/**
 * Helper for OpenTollData: download French highway toll JSON(s), merge, save to app files dir.
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
     * Downloads all available OpenTollData JSON files, merges them, and writes the result.
     * Reports progress (bytes read across all files, total if known).
     * Returns the absolute path to use as [AppSettings.tollDataPath] on success.
     */
    suspend fun download(
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val destFile = fileForTollData()
        try {
            val models = mutableListOf<OpenTollDataModel>()
            var bytesDownloaded = 0L
            var totalKnown: Long? = null

            for (filename in OPEN_TOLL_DATA_FILENAMES) {
                val url = URL("$OPEN_TOLL_DATA_RAW_BASE/$filename")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 120_000
                connection.requestMethod = "GET"
                connection.connect()
                val code = connection.responseCode
                if (code == 404) {
                    connection.disconnect()
                    continue
                }
                if (code !in 200..299) {
                    connection.disconnect()
                    return@withContext Result.failure(Exception("HTTP $code for $filename"))
                }
                val contentLength = connection.contentLengthLong.takeIf { it > 0 }
                if (contentLength != null) {
                    totalKnown = (totalKnown ?: 0L) + contentLength
                }
                val text = connection.inputStream.bufferedReader().use { reader ->
                    val sb = StringBuilder()
                    val buf = CharArray(16 * 1024)
                    var n: Int
                    while (reader.read(buf).also { n = it } != -1) {
                        sb.append(buf, 0, n)
                        bytesDownloaded += n
                        onProgress(bytesDownloaded, totalKnown)
                    }
                    sb.toString()
                }
                connection.disconnect()
                val parsed = OpenTollDataParser.parse(text)
                    ?: return@withContext Result.failure(Exception("Parse failed for $filename"))
                models.add(parsed)
            }

            if (models.isEmpty()) {
                return@withContext Result.failure(Exception("No OpenTollData files available"))
            }

            val merged = OpenTollDataMerger.merge(models)
            val json = kotlinx.serialization.json.Json {
                prettyPrint = false
                encodeDefaults = true
            }
            val mergedText = json.encodeToString(OpenTollDataModel.serializer(), merged)

            destFile.parentFile?.mkdirs() ?: run {
                return@withContext Result.failure(Exception("Could not create open_toll_data directory"))
            }
            FileOutputStream(destFile).use { output ->
                output.write(mergedText.toByteArray(Charsets.UTF_8))
            }
            onProgress(bytesDownloaded, totalKnown)

            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            if (destFile.exists()) destFile.delete()
            Result.failure(e)
        }
    }
}
