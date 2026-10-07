package fr.geoking.gaston.auto

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.CarMapMode
import java.io.File

object OfflineMapAvailability {

    fun offlineFilePath(settings: AppSettings): String? = when (settings.carMapMode) {
        CarMapMode.Protomaps -> settings.offlinePmtilesPath
        CarMapMode.Mapsforge -> settings.offlineMapsforgePath
        else -> null
    }

    fun requiresOfflineFile(mode: CarMapMode): Boolean = mode.requiresOfflineMapFile

    fun isOfflineFileAvailable(settings: AppSettings): Boolean {
        val path = offlineFilePath(settings) ?: return !settings.carMapMode.requiresOfflineMapFile
        val file = File(path)
        return file.isFile && file.canRead() && file.length() > 0L
    }

    fun offlineFileDisplayName(settings: AppSettings): String? =
        offlineFilePath(settings)?.let { File(it).name }
}
