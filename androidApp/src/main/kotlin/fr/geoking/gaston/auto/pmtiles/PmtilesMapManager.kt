package fr.geoking.gaston.auto.pmtiles

import android.content.Context
import android.net.Uri
import android.util.Log
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.auto.mapsforge.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class PmtilesServerMap(
    val name: String,
    val region: String,
    val url: String,
    val sizeEstimateMb: Int,
    val minLat: Double = -90.0,
    val maxLat: Double = 90.0,
    val minLon: Double = -180.0,
    val maxLon: Double = 180.0
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon
}

/**
 * Preset regional PMTiles downloads.
 *
 * Protomaps daily builds are full-planet archives only (~120 GiB at
 * `https://build.protomaps.com/YYYYMMDD.pmtiles`); regional paths like
 * `…/monaco.pmtiles` do not exist (HTTP 404). BBBike publishes downloadable
 * Shortbread-schema PMTiles zips for OSM extracts — used here as the public
 * preset source.
 */
object PmtilesPresetServers {
    private const val BBBIKE_PMTILES =
        "https://data.bbbike.org/osm/pmtiles/region"

    val PRESET_MAPS = listOf(
        PmtilesServerMap(
            name = "Île-de-France (Paris)",
            region = "France",
            url = "$BBBIKE_PMTILES/europe/france/ile-de-france/ile-de-france.osm.pmtiles-shortbread.zip",
            sizeEstimateMb = 131,
            minLat = 48.1, maxLat = 49.3, minLon = 1.4, maxLon = 3.6
        ),
        PmtilesServerMap(
            name = "Monaco",
            region = "Europe",
            url = "$BBBIKE_PMTILES/europe/monaco/monaco.osm.pmtiles-shortbread.zip",
            sizeEstimateMb = 1,
            minLat = 43.7, maxLat = 43.8, minLon = 7.4, maxLon = 7.5
        ),
        PmtilesServerMap(
            name = "Luxembourg",
            region = "Europe",
            url = "$BBBIKE_PMTILES/europe/luxembourg/luxembourg.osm.pmtiles-shortbread.zip",
            sizeEstimateMb = 32,
            minLat = 49.4, maxLat = 50.2, minLon = 5.7, maxLon = 6.6
        ),
        PmtilesServerMap(
            name = "Belgium",
            region = "Europe",
            url = "$BBBIKE_PMTILES/europe/belgium/belgium.osm.pmtiles-shortbread.zip",
            sizeEstimateMb = 400,
            minLat = 49.5, maxLat = 51.5, minLon = 2.5, maxLon = 6.4
        ),
        PmtilesServerMap(
            name = "Germany (Berlin)",
            region = "Germany",
            url = "$BBBIKE_PMTILES/europe/germany/berlin/berlin.osm.pmtiles-shortbread.zip",
            sizeEstimateMb = 33,
            minLat = 52.3, maxLat = 52.7, minLon = 13.0, maxLon = 13.8
        ),
        PmtilesServerMap(
            name = "France (All)",
            region = "Europe",
            url = "$BBBIKE_PMTILES/europe/france/france.osm.pmtiles-shortbread.zip",
            sizeEstimateMb = 2766,
            minLat = 41.3, maxLat = 51.1, minLon = -5.2, maxLon = 9.6
        )
    )

    fun getRecommendedPreset(lat: Double?, lon: Double?): PmtilesServerMap {
        if (lat == null || lon == null) return PRESET_MAPS.first { it.name.contains("Île-de-France") }
        val matchingSmallMap = PRESET_MAPS
            .filter { it.sizeEstimateMb < 1000 }
            .firstOrNull { it.contains(lat, lon) }
        if (matchingSmallMap != null) return matchingSmallMap

        val matchingAnyMap = PRESET_MAPS.firstOrNull { it.contains(lat, lon) }
        return matchingAnyMap ?: PRESET_MAPS.first { it.name.contains("Île-de-France") }
    }
}

class PmtilesMapManager(
    private val context: Context,
    private val settingsManager: SettingsManager? = null
) {
    private val prefs = context.getSharedPreferences("pmtiles_map_prefs", Context.MODE_PRIVATE)
    private val pmtilesDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "pmtiles")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val _installedMaps = MutableStateFlow<List<File>>(emptyList())
    val installedMaps: StateFlow<List<File>> = _installedMaps.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    init {
        refreshInstalledMaps()
    }

    fun refreshInstalledMaps() {
        val files = pmtilesDir.listFiles { _, name -> name.endsWith(".pmtiles", ignoreCase = true) }?.toList() ?: emptyList()
        _installedMaps.value = files.sortedBy { it.name }
        val active = getActiveMapFile()
        if (active != null) {
            settingsManager?.setOfflinePmtilesPath(active.absolutePath)
        }
    }

    fun getActiveMapFile(): File? {
        val configuredPath = settingsManager?.settings?.value?.offlinePmtilesPath
        val activeName = prefs.getString("active_map_name", null)
        val installed = _installedMaps.value

        if (!configuredPath.isNullOrBlank()) {
            val file = File(configuredPath)
            if (file.exists()) return file
            val found = installed.firstOrNull { it.name == file.name }
            if (found != null && found.exists()) return found
        }

        if (activeName != null) {
            val found = installed.firstOrNull { it.name == activeName }
            if (found != null && found.exists()) return found
        }

        return installed.firstOrNull()
    }

    fun setActiveMapFile(file: File) {
        prefs.edit().putString("active_map_name", file.name).apply()
        settingsManager?.setOfflinePmtilesPath(file.absolutePath)
    }

    suspend fun importMapFromUri(uri: Uri, targetFileName: String? = null): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = targetFileName
                ?: getFileNameFromUri(uri)
                ?: "imported_map_${System.currentTimeMillis()}.pmtiles"
            val safeName = if (fileName.endsWith(".pmtiles", ignoreCase = true)) fileName else "$fileName.pmtiles"
            val targetFile = File(pmtilesDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Could not open input stream from Uri"))

            refreshInstalledMaps()
            setActiveMapFile(targetFile)
            Log.d("PmtilesMapManager", "Imported pmtiles file successfully to ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("PmtilesMapManager", "Failed to import pmtiles from Uri", e)
            Result.failure(e)
        }
    }

    suspend fun downloadMap(
        urlString: String,
        customFileName: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val rawFileName = customFileName
            ?: urlString.substringAfterLast('/').substringBefore('?')
                .ifBlank { "downloaded_map.pmtiles" }
        val fileName = when {
            rawFileName.endsWith(".pmtiles", ignoreCase = true) -> rawFileName
            rawFileName.endsWith(".zip", ignoreCase = true) ->
                rawFileName.removeSuffix(".zip").removeSuffix(".ZIP") + ".pmtiles"
            else -> "$rawFileName.pmtiles"
        }
        val tempDownload = File(pmtilesDir, "$fileName.download")
        val targetFile = File(pmtilesDir, fileName)

        try {
            Log.d("PmtilesMapManager", "Starting download from $urlString to ${tempDownload.absolutePath}")
            _downloadProgress.value = DownloadProgress(fileName, 0, -1)

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "GastonAndroid/1.0 (pmtiles-download)")
            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) {
                val err = "HTTP Error $code: ${connection.responseMessage}"
                _downloadProgress.value = DownloadProgress(fileName, 0, -1, error = err)
                return@withContext Result.failure(Exception(err))
            }

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempDownload).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        _downloadProgress.value = DownloadProgress(
                            fileName = fileName,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes
                        )
                    }
                }
            }

            val isZip = urlString.substringBefore('?').endsWith(".zip", ignoreCase = true) ||
                looksLikeZip(tempDownload)
            if (isZip) {
                extractPmtilesFromZip(tempDownload, targetFile)
                tempDownload.delete()
            } else {
                if (targetFile.exists()) targetFile.delete()
                if (!tempDownload.renameTo(targetFile)) {
                    tempDownload.copyTo(targetFile, overwrite = true)
                    tempDownload.delete()
                }
            }

            if (!targetFile.isFile || targetFile.length() == 0L) {
                targetFile.delete()
                val err = "Downloaded file is missing or empty"
                _downloadProgress.value = DownloadProgress(fileName, 0, -1, error = err)
                return@withContext Result.failure(Exception(err))
            }

            _downloadProgress.value = DownloadProgress(
                fileName = fileName,
                bytesDownloaded = targetFile.length(),
                totalBytes = targetFile.length(),
                isComplete = true
            )

            refreshInstalledMaps()
            setActiveMapFile(targetFile)
            Log.d("PmtilesMapManager", "Download complete: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("PmtilesMapManager", "Download pmtiles map failed", e)
            tempDownload.delete()
            if (targetFile.exists() && targetFile.length() == 0L) targetFile.delete()
            _downloadProgress.value = DownloadProgress(
                fileName = fileName,
                bytesDownloaded = 0,
                totalBytes = -1,
                error = e.message ?: "Download failed"
            )
            Result.failure(e)
        }
    }

    internal fun looksLikeZip(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        FileInputStream(file).use { input ->
            val sig = ByteArray(4)
            if (input.read(sig) != 4) return false
            // ZIP local file header: PK\x03\x04
            return sig[0] == 'P'.code.toByte() &&
                sig[1] == 'K'.code.toByte() &&
                sig[2] == 3.toByte() &&
                sig[3] == 4.toByte()
        }
    }

    internal fun extractPmtilesFromZip(zipFile: File, targetFile: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (!entry.isDirectory && name.endsWith(".pmtiles", ignoreCase = true)) {
                    val staging = File(pmtilesDir, "${targetFile.name}.extracting")
                    FileOutputStream(staging).use { output -> zis.copyTo(output) }
                    zis.closeEntry()
                    if (targetFile.exists()) targetFile.delete()
                    if (!staging.renameTo(targetFile)) {
                        staging.copyTo(targetFile, overwrite = true)
                        staging.delete()
                    }
                    return
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw Exception("ZIP archive contains no .pmtiles file")
    }

    fun deleteMap(file: File): Boolean {
        return try {
            val deleted = file.delete()
            refreshInstalledMaps()
            val remainingActive = getActiveMapFile()
            if (remainingActive == null && _installedMaps.value.isNotEmpty()) {
                setActiveMapFile(_installedMaps.value.first())
            } else if (_installedMaps.value.isEmpty()) {
                prefs.edit().remove("active_map_name").apply()
                settingsManager?.setOfflinePmtilesPath(null)
            }
            deleted
        } catch (e: Exception) {
            Log.e("PmtilesMapManager", "Failed to delete pmtiles file: ${file.name}", e)
            false
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
