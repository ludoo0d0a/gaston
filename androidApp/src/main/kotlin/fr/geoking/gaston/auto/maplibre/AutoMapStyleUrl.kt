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

/** Standard raster tile URLs for Android Auto. Always light theme. */
fun resolveAutoRasterTileUrl(isLab: Boolean = false): String {
    return "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
}
