package fr.geoking.gaston.map.download

import fr.geoking.gaston.auto.map.mapsforge.DownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared download progress for Mapsforge / PMTiles offline packs.
 * Backed by [MapDownloadService] so UI survives Activity / AA screen teardown.
 */
object MapDownloadProgressHub {
    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    fun publish(progress: DownloadProgress?) {
        _progress.value = progress
    }
}
