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
    val sizeEstimateMb: Int,
    val minLat: Double = -90.0,
    val maxLat: Double = 90.0,
    val minLon: Double = -180.0,
    val maxLon: Double = 180.0
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon
}

object MapsforgePresetServers {
    private const val FR_BASE = "https://download.mapsforge.org/maps/v5/europe/france"

    /** Former administrative régions as published by mapsforge.org (one `.map` per region). */
    val FRANCE_REGION_MAPS = listOf(
        MapsforgeServerMap("Alsace", "France", "$FR_BASE/alsace.map", 86, 47.3, 49.1, 6.8, 8.3),
        MapsforgeServerMap("Aquitaine", "France", "$FR_BASE/aquitaine.map", 211, 42.8, 45.7, -1.8, 1.2),
        MapsforgeServerMap("Auvergne", "France", "$FR_BASE/auvergne.map", 113, 44.6, 46.8, 2.0, 4.5),
        MapsforgeServerMap("Basse-Normandie", "France", "$FR_BASE/basse-normandie.map", 104, 48.3, 49.8, -2.0, 0.5),
        MapsforgeServerMap("Bourgogne", "France", "$FR_BASE/bourgogne.map", 151, 46.2, 48.4, 2.8, 5.5),
        MapsforgeServerMap("Bretagne", "France", "$FR_BASE/bretagne.map", 208, 47.3, 48.9, -5.2, -1.0),
        MapsforgeServerMap("Centre", "France", "$FR_BASE/centre.map", 176, 46.3, 48.9, 0.1, 3.2),
        MapsforgeServerMap("Champagne-Ardenne", "France", "$FR_BASE/champagne-ardenne.map", 80, 47.8, 50.3, 3.4, 5.9),
        MapsforgeServerMap("Corse", "France", "$FR_BASE/corse.map", 25, 41.3, 43.1, 8.5, 9.6),
        MapsforgeServerMap("Franche-Comté", "France", "$FR_BASE/franche-comte.map", 89, 46.2, 48.0, 5.3, 7.2),
        MapsforgeServerMap("Guadeloupe", "France", "$FR_BASE/guadeloupe.map", 17, 15.8, 16.6, -61.9, -61.0),
        MapsforgeServerMap("Guyane", "France", "$FR_BASE/guyane.map", 15, 2.1, 5.8, -54.6, -51.6),
        MapsforgeServerMap("Haute-Normandie", "France", "$FR_BASE/haute-normandie.map", 79, 48.8, 50.1, 0.0, 1.8),
        MapsforgeServerMap("Île-de-France (Paris)", "France", "$FR_BASE/ile-de-france.map", 200, 48.1, 49.3, 1.4, 3.6),
        MapsforgeServerMap("Languedoc-Roussillon", "France", "$FR_BASE/languedoc-roussillon.map", 178, 42.3, 44.9, 1.7, 4.9),
        MapsforgeServerMap("Limousin", "France", "$FR_BASE/limousin.map", 73, 44.9, 46.5, 0.6, 2.6),
        MapsforgeServerMap("Lorraine", "France", "$FR_BASE/lorraine.map", 123, 48.3, 49.7, 5.2, 7.6),
        MapsforgeServerMap("Martinique", "France", "$FR_BASE/martinique.map", 14, 14.4, 14.9, -61.3, -60.8),
        MapsforgeServerMap("Mayotte", "France", "$FR_BASE/mayotte.map", 8, -13.1, -12.6, 45.0, 45.3),
        MapsforgeServerMap("Midi-Pyrénées", "France", "$FR_BASE/midi-pyrenees.map", 245, 42.3, 45.1, -0.3, 3.5),
        MapsforgeServerMap("Nord-Pas-de-Calais", "France", "$FR_BASE/nord-pas-de-calais.map", 159, 50.0, 51.1, 1.5, 4.3),
        MapsforgeServerMap("Pays de la Loire", "France", "$FR_BASE/pays-de-la-loire.map", 238, 46.3, 48.6, -2.6, 0.9),
        MapsforgeServerMap("Picardie", "France", "$FR_BASE/picardie.map", 106, 48.8, 50.4, 1.4, 4.3),
        MapsforgeServerMap("Poitou-Charentes", "France", "$FR_BASE/poitou-charentes.map", 147, 45.1, 47.2, -1.6, 1.1),
        MapsforgeServerMap(
            "Provence-Alpes-Côte d'Azur",
            "France",
            "$FR_BASE/provence-alpes-cote-d-azur.map",
            231,
            42.9, 45.1, 4.2, 7.8,
        ),
        MapsforgeServerMap("La Réunion", "France", "$FR_BASE/reunion.map", 22, -21.4, -20.9, 55.2, 55.9),
        MapsforgeServerMap("Rhône-Alpes", "France", "$FR_BASE/rhone-alpes.map", 340, 44.1, 46.5, 4.5, 7.2),
    )

    val NEIGHBOR_MAPS = listOf(
        MapsforgeServerMap(
            name = "Monaco",
            region = "Europe",
            url = "https://download.mapsforge.org/maps/v5/europe/monaco.map",
            sizeEstimateMb = 5,
            minLat = 43.7, maxLat = 43.8, minLon = 7.4, maxLon = 7.5,
        ),
        MapsforgeServerMap(
            name = "Luxembourg",
            region = "Europe",
            url = "https://download.mapsforge.org/maps/v5/europe/luxembourg.map",
            sizeEstimateMb = 35,
            minLat = 49.4, maxLat = 50.2, minLon = 5.7, maxLon = 6.6,
        ),
        MapsforgeServerMap(
            name = "Belgium",
            region = "Europe",
            url = "https://download.mapsforge.org/maps/v5/europe/belgium.map",
            sizeEstimateMb = 320,
            minLat = 49.5, maxLat = 51.5, minLon = 2.5, maxLon = 6.4,
        ),
        MapsforgeServerMap(
            name = "Germany (Berlin)",
            region = "Germany",
            url = "https://download.mapsforge.org/maps/v5/europe/germany/berlin.map",
            sizeEstimateMb = 120,
            minLat = 52.3, maxLat = 52.7, minLon = 13.0, maxLon = 13.8,
        ),
    )

    val FRANCE_ALL = MapsforgeServerMap(
        name = "France (All)",
        region = "Europe",
        url = "https://download.mapsforge.org/maps/v5/europe/france.map",
        sizeEstimateMb = 3338,
        minLat = 41.3, maxLat = 51.1, minLon = -5.2, maxLon = 9.6,
    )

    val PRESET_MAPS: List<MapsforgeServerMap> =
        FRANCE_REGION_MAPS + NEIGHBOR_MAPS + listOf(FRANCE_ALL)

    fun getRecommendedPreset(lat: Double?, lon: Double?): MapsforgeServerMap {
        val default = FRANCE_REGION_MAPS.first { it.name.contains("Île-de-France") }
        if (lat == null || lon == null) return default
        // Prefer the tightest bbox that covers the point (avoids oversized neighbors with fuzzy bounds).
        val matchingRegional = PRESET_MAPS
            .filter { it.sizeEstimateMb < 1000 && it.contains(lat, lon) }
            .minByOrNull { (it.maxLat - it.minLat) * (it.maxLon - it.minLon) }
        if (matchingRegional != null) return matchingRegional

        return PRESET_MAPS.firstOrNull { it.contains(lat, lon) } ?: default
    }
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
