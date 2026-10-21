package fr.geoking.gaston.auto.map.maplibre

import android.util.Log
import androidx.car.app.CarContext
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.MapTheme

/**
 * OpenFreeMap **vector** style URL for Android Auto MapLibre mode.
 * Always uses [MapTheme.styleUrl] (tiles.openfreemap.org vector styles).
 */
@Suppress("UNUSED_PARAMETER")
fun resolveAutoMapStyleUrl(settings: AppSettings, carContext: CarContext): String {
    val url = settings.mapTheme.styleUrl
    if (!url.contains("openfreemap.org")) {
        Log.w(
            "AutoMapStyleUrl",
            "Unexpected non-OpenFreeMap styleUrl=$url — MapLibre AA expects OpenFreeMap vector styles",
        )
    }
    return url
}

/** Raster XYZ for Android Auto **Custom** mode only (not MapLibre). */
fun resolveAutoRasterTileUrl(settings: AppSettings): String = settings.mapTheme.rasterUrl
