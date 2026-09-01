package fr.geoking.gaston.auto.maplibre

import androidx.car.app.CarContext
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.MapTheme

/**
 * OpenFreeMap **vector** style URL for Android Auto MapLibre mode.
 * Always uses [fr.geoking.gaston.MapTheme.styleUrl] (tiles.openfreemap.org vector styles).
 */
@Suppress("UNUSED_PARAMETER")
fun resolveAutoMapStyleUrl(settings: AppSettings, carContext: CarContext): String {
    val url = settings.mapTheme.styleUrl
    if (!url.contains("openfreemap.org")) {
        android.util.Log.w(
            "AutoMapStyleUrl",
            "Unexpected non-OpenFreeMap styleUrl=$url — MapLibre AA expects OpenFreeMap vector styles",
        )
    }
    return url
}

/** Raster XYZ for Android Auto **Custom** mode only (not MapLibre). */
fun resolveAutoRasterTileUrl(settings: AppSettings): String = settings.mapTheme.rasterUrl

/** MapTiler vector style URL (requires [BuildConfig.MAPTILER_KEY]). */
fun resolveMapTilerStyleUrl(settings: AppSettings): String? {
    val key = BuildConfig.MAPTILER_KEY.trim()
    if (key.isEmpty()) return null
    val styleId = when (settings.mapTheme) {
        MapTheme.Dark -> "dataviz-dark"
        MapTheme.Positron -> "basic-v2-light"
        MapTheme.Fiord -> "topo-v2"
        else -> "streets-v2"
    }
    return "https://api.maptiler.com/maps/$styleId/style.json?key=$key"
}

/** Returns non-null when a readable local PMTiles path is configured (rendering is mode-specific). */
fun resolveProtomapsLocalPath(settings: AppSettings): String? {
    val path = settings.offlinePmtilesPath?.trim().orEmpty()
    if (path.isEmpty()) return null
    val file = java.io.File(path)
    return path.takeIf { file.isFile && file.canRead() && file.length() > 0L }
}

