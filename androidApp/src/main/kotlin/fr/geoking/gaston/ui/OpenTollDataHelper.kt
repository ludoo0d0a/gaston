package fr.geoking.gaston.ui

import android.content.Context
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.api.toll.OpenTollDataMerger
import fr.geoking.gaston.api.toll.OpenTollDataModel
import fr.geoking.gaston.api.toll.OpenTollDataParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream

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
class OpenTollDataHelper(
    private val context: Context,
    private val client: HttpClient,
) {

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
                val url = "$OPEN_TOLL_DATA_RAW_BASE/$filename"
                val response = try {
                    client.get(url)
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
                val code = response.status.value
                if (code == 404) {
                    continue
                }
                if (code !in 200..299) {
                    return@withContext Result.failure(Exception("HTTP $code for $filename"))
                }
                val contentLength = response.contentLength()
                if (contentLength != null && contentLength > 0) {
                    totalKnown = (totalKnown ?: 0L) + contentLength
                }
                val channel = response.bodyAsChannel()
                val baos = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    baos.write(buffer, 0, read)
                    bytesDownloaded += read
                    onProgress(bytesDownloaded, totalKnown)
                }
                val text = baos.toString("UTF-8")
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
