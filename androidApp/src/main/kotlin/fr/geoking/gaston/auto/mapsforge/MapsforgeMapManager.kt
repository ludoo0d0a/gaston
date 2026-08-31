package fr.geoking.gaston.auto.mapsforge

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.reader.MapFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
}

data class MapsforgeServerMap(
    val name: String,
    val region: String,
    val url: String,
    val sizeEstimateMb: Int
)

object MapsforgePresetServers {
    val PRESET_MAPS = listOf(
        MapsforgeServerMap(
            name = "France (All)",
            region = "Europe",
            url = "https://download.mapsforge.org/maps/v5/europe/france.map",
            sizeEstimateMb = 1800
        ),
        MapsforgeServerMap(
            name = "Île-de-France (Paris)",
            region = "France",
            url = "https://download.mapsforge.org/maps/v5/europe/france/ile-de-france.map",
            sizeEstimateMb = 250
        ),
        MapsforgeServerMap(
            name = "Monaco",
            region = "Europe",
            url = "https://download.mapsforge.org/maps/v5/europe/monaco.map",
            sizeEstimateMb = 5
        ),
        MapsforgeServerMap(
            name = "Luxembourg",
            region = "Europe",
            url = "https://download.mapsforge.org/maps/v5/europe/luxembourg.map",
            sizeEstimateMb = 35
        ),
        MapsforgeServerMap(
            name = "Belgium",
            region = "Europe",
            url = "https://download.mapsforge.org/maps/v5/europe/belgium.map",
            sizeEstimateMb = 320
        ),
        MapsforgeServerMap(
            name = "Germany (Berlin)",
            region = "Germany",
            url = "https://download.mapsforge.org/maps/v5/europe/germany/berlin.map",
            sizeEstimateMb = 120
        )
    )
}

class MapsforgeMapManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("mapsforge_map_prefs", Context.MODE_PRIVATE)
    private val mapsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "mapsforge")
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
        val files = mapsDir.listFiles { _, name -> name.endsWith(".map", ignoreCase = true) }?.toList() ?: emptyList()
        _installedMaps.value = files.sortedBy { it.name }
    }

    fun getActiveMapFile(): File? {
        val activeName = prefs.getString("active_map_name", null)
        val installed = _installedMaps.value
        if (activeName != null) {
            val found = installed.firstOrNull { it.name == activeName }
            if (found != null && found.exists()) return found
        }
        return installed.firstOrNull()
    }

    fun setActiveMapFile(file: File) {
        prefs.edit().putString("active_map_name", file.name).apply()
    }

    fun createMapDataStore(): MapDataStore? {
        val activeFile = getActiveMapFile() ?: return null
        return try {
            if (activeFile.exists() && activeFile.length() > 0) {
                MapFile(activeFile)
            } else null
        } catch (e: Exception) {
            Log.e("MapsforgeMapManager", "Failed to open MapFile: ${activeFile.absolutePath}", e)
            null
        }
    }

    suspend fun importMapFromUri(uri: Uri, targetFileName: String? = null): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = targetFileName
                ?: getFileNameFromUri(uri)
                ?: "imported_map_${System.currentTimeMillis()}.map"
            val safeName = if (fileName.endsWith(".map", ignoreCase = true)) fileName else "$fileName.map"
            val targetFile = File(mapsDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Could not open input stream from Uri"))

            refreshInstalledMaps()
            setActiveMapFile(targetFile)
            Log.d("MapsforgeMapManager", "Imported map file successfully to ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("MapsforgeMapManager", "Failed to import map from Uri", e)
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
                .ifBlank { "downloaded_map.map" }
            val fileName = if (rawFileName.endsWith(".map", ignoreCase = true)) rawFileName else "$rawFileName.map"
            val tempFile = File(mapsDir, "$fileName.tmp")
            val targetFile = File(mapsDir, fileName)

            Log.d("MapsforgeMapManager", "Starting download from $urlString to ${tempFile.absolutePath}")
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
            Log.d("MapsforgeMapManager", "Download complete: ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("MapsforgeMapManager", "Download map failed", e)
            _downloadProgress.value = DownloadProgress(
                fileName = customFileName ?: "map.map",
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
            if (getActiveMapFile() == null && _installedMaps.value.isNotEmpty()) {
                setActiveMapFile(_installedMaps.value.first())
            }
            deleted
        } catch (e: Exception) {
            Log.e("MapsforgeMapManager", "Failed to delete map file: ${file.name}", e)
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
