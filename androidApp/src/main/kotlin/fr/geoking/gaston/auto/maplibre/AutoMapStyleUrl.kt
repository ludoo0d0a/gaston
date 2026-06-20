package fr.geoking.gaston.auto.maplibre

import androidx.car.app.CarContext
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.auto.isDarkMode

/** OpenFreeMap style URL aligned with phone [fr.geoking.gaston.ui.map.maplibre.VectorMapScreen]. */
fun resolveAutoMapStyleUrl(settings: AppSettings, carContext: CarContext): String {
    return settings.mapTheme.styleUrl
}

/** Standard raster tile URLs for Android Auto. Uses the user-selected theme. */
fun resolveAutoRasterTileUrl(settings: AppSettings): String {
    return settings.mapTheme.rasterUrl
}
