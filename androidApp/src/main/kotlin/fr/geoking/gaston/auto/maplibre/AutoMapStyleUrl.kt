package fr.geoking.gaston.auto.maplibre

import androidx.car.app.CarContext
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.auto.isDarkMode

/** OpenFreeMap style URL aligned with phone [fr.geoking.gaston.ui.map.maplibre.VectorMapScreen]. */
fun resolveAutoMapStyleUrl(settings: AppSettings, carContext: CarContext): String {
    val dark = when (settings.uiThemeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> carContext.isDarkMode
    }
    val theme = if (dark) MapTheme.Dark else MapTheme.Modern
    return theme.styleUrl
}
