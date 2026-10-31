package fr.geoking.gaston.auto.settings.map

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.auto.safeCarTemplate

/**
 * AA list of bundled [MapTheme] values (OpenFreeMap names).
 * Used for MapLibre / raster styles and Mapsforge approximate XML palettes.
 */
class AutoMapThemeSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoMapThemeSelectionScreen", "ListTemplate") {
        val settings = settingsManager.settings.value
        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

        // Prefer unique OpenFreeMap styleUrls so AA shows distinct looks within the list cap.
        val themes = MapTheme.entries
            .distinctBy { it.styleUrl }
            .take(listLimit)

        val listBuilder = ItemList.Builder()
        themes.forEach { theme ->
            val selected = settings.mapTheme.styleUrl == theme.styleUrl
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(themeLabel(theme))
                    .addText(
                        if (selected) carContext.getString(R.string.filter_selected)
                        else themeDescription(theme)
                    )
                    .setOnClickListener {
                        settingsManager.setMapTheme(theme)
                        invalidate()
                    }
                    .build()
            )
        }

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.settings_map_theme))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }

    private fun themeLabel(theme: MapTheme): String = when (theme) {
        MapTheme.Dark -> carContext.getString(R.string.map_theme_dark_matter)
        MapTheme.Voyager -> carContext.getString(R.string.map_theme_voyager)
        MapTheme.Standard -> carContext.getString(R.string.map_theme_osm)
        MapTheme.Positron -> carContext.getString(R.string.map_theme_positron)
        MapTheme.Fiord -> carContext.getString(R.string.map_theme_fiord)
        MapTheme.OsmFr -> carContext.getString(R.string.map_theme_osm_fr)
        MapTheme.Hot -> carContext.getString(R.string.map_theme_hot)
        MapTheme.Bright -> carContext.getString(R.string.map_theme_bright)
        MapTheme.Liberty -> carContext.getString(R.string.map_theme_liberty)
    }

    private fun themeDescription(theme: MapTheme): String = when (theme) {
        MapTheme.Dark -> carContext.getString(R.string.map_theme_dark_matter_desc)
        MapTheme.Voyager -> carContext.getString(R.string.map_theme_voyager_desc)
        MapTheme.Standard -> carContext.getString(R.string.map_theme_osm_desc)
        MapTheme.Positron -> carContext.getString(R.string.map_theme_positron_desc)
        MapTheme.Fiord -> carContext.getString(R.string.map_theme_fiord_desc)
        MapTheme.OsmFr -> carContext.getString(R.string.map_theme_osm_fr_desc)
        MapTheme.Hot -> carContext.getString(R.string.map_theme_hot_desc)
        MapTheme.Bright -> carContext.getString(R.string.map_theme_bright_desc)
        MapTheme.Liberty -> carContext.getString(R.string.map_theme_liberty_desc)
    }
}
