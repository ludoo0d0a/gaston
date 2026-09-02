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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

object PmtilesPresetServers {
    val PRESET_MAPS = listOf(
        PmtilesServerMap(
            name = "Île-de-France (Paris)",
            region = "France",
            url = "https://build.protomaps.com/20240101/ile-de-france.pmtiles",
            sizeEstimateMb = 280,
            minLat = 48.1, maxLat = 49.3, minLon = 1.4, maxLon = 3.6
        ),
        PmtilesServerMap(
            name = "Monaco",
            region = "Europe",
            url = "https://build.protomaps.com/20240101/monaco.pmtiles",
            sizeEstimateMb = 8,
            minLat = 43.7, maxLat = 43.8, minLon = 7.4, maxLon = 7.5
        ),
        PmtilesServerMap(
            name = "Luxembourg",
            region = "Europe",
            url = "https://build.protomaps.com/20240101/luxembourg.pmtiles",
            sizeEstimateMb = 42,
            minLat = 49.4, maxLat = 50.2, minLon = 5.7, maxLon = 6.6
        ),
        PmtilesServerMap(
            name = "Belgium",
            region = "Europe",
            url = "https://build.protomaps.com/20240101/belgium.pmtiles",
            sizeEstimateMb = 360,
            minLat = 49.5, maxLat = 51.5, minLon = 2.5, maxLon = 6.4
        ),
        PmtilesServerMap(
            name = "Germany (Berlin)",
            region = "Germany",
            url = "https://build.protomaps.com/20240101/berlin.pmtiles",
            sizeEstimateMb = 140,
            minLat = 52.3, maxLat = 52.7, minLon = 13.0, maxLon = 13.8
        ),
        PmtilesServerMap(
            name = "France (All)",
            region = "Europe",
            url = "https://build.protomaps.com/20240101/france.pmtiles",
            sizeEstimateMb = 2100,
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
        try {
            val rawFileName = customFileName
                ?: urlString.substringAfterLast('/').substringBefore('?')
                .ifBlank { "downloaded_map.pmtiles" }
            val fileName = if (rawFileName.endsWith(".pmtiles", ignoreCase = true)) rawFileName else "$rawFileName.pmtiles"
            val tempFile = File(pmtilesDir, "$fileName.tmp")
            val targetFile = File(pmtilesDir, fileName)

            Log.d("PmtilesMapManager", "Starting download from $urlString to ${tempFile.absolutePath}")
            _downloadProgress.value = DownloadProgress(fileName, 0, -1)

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val err = "HTTP Error ${connection.responseCode}: ${connection.responseMessage}"
                _downloadProgress.value = DownloadProgress(fileName, 0, -1, error = err)
                return@withContext Result.failure(Exception(err))
            }

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
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

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            _downloadProgress.value = DownloadProgress(
                fileName = fileName,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                isComplete = true
            )

            refreshInstalledMaps()
            setActiveMapFile(targetFile)
            Log.d("PmtilesMapManager", "Download complete: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("PmtilesMapManager", "Download pmtiles map failed", e)
            _downloadProgress.value = DownloadProgress(
                fileName = customFileName ?: "map.pmtiles",
                bytesDownloaded = 0,
                totalBytes = -1,
                error = e.message ?: "Download failed"
            )
            Result.failure(e)
        }
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
