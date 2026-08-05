package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.shared.network.NetworkService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AutoMapSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext), KoinComponent {

    private val networkService: NetworkService by inject()

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        val dataSourceText = when (settings.poiProviderSelectionMode) {
            PoiProviderSelectionMode.Auto -> {
                val net = networkService.status.value
                val countryLine = net.countryName ?: net.countryCode
                buildString {
                    append("Auto (by country)")
                    if (!countryLine.isNullOrBlank()) {
                        append("\nNetwork country: ").append(countryLine)
                    }
                }
            }
            PoiProviderSelectionMode.Manual ->
                if (settings.selectedPoiProviders.isEmpty()) "None"
                else settings.selectedPoiProviders.joinToString(", ") { it.name }
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_data_source))
                .addText(dataSourceText)
                .setOnClickListener {
                    val next = if (settings.poiProviderSelectionMode == PoiProviderSelectionMode.Manual) {
                        PoiProviderSelectionMode.Auto
                    } else {
                        PoiProviderSelectionMode.Manual
                    }
                    settingsManager.setPoiProviderSelectionMode(next)
                    invalidate()
                }
                .build()
        )

        val themeLabel = when (settings.mapTheme) {
            MapTheme.Dark -> carContext.getString(R.string.map_theme_dark_matter)
            MapTheme.Voyager -> carContext.getString(R.string.map_theme_voyager)
            MapTheme.Standard -> carContext.getString(R.string.map_theme_osm)
            MapTheme.Positron -> carContext.getString(R.string.map_theme_positron)
            MapTheme.Fiord -> carContext.getString(R.string.map_theme_fiord)
            MapTheme.OsmFr -> carContext.getString(R.string.map_theme_osm_fr)
            MapTheme.Hot -> carContext.getString(R.string.map_theme_hot)
        }
        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.settings_map_theme))
                .addText(themeLabel)
                .setOnClickListener {
                    val entries = MapTheme.entries
                    val nextTheme = entries[(settings.mapTheme.ordinal + 1) % entries.size]
                    settingsManager.setMapTheme(nextTheme)
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.settings_map_mode))
                .addText(
                    "Current: ${settings.carMapMode.name}" +
                        when (settings.carMapMode) {
                            CarMapMode.Custom, CarMapMode.MapLibre ->
                                " — north/heading toggle on map header (may not be POI-category compliant)"
                            CarMapMode.Native ->
                                " — map orientation is host-controlled (north-up)"
                        }
                )
                .setOnClickListener {
                    settingsManager.setCarMapMode(settings.carMapMode.next())
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.settings_show_traffic))
                .addText(carContext.getString(R.string.filter_google_traffic))
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.setMapTrafficEnabled(checked)
                        invalidate()
                    }.setChecked(settings.mapTrafficEnabled).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_vehicle_and_range))
                .addText("${settings.vehicleType.name}, ${settings.evRangeKm} km")
                .setOnClickListener {
                    screenManager.pop()
                    screenManager.push(AutoVehicleSettingsScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map Debugging")
                .addText("Show tile borders, coordinates, and diagnostics")
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.setMapTileDebugEnabled(checked)
                        invalidate()
                    }.setChecked(settings.mapTileDebugEnabled).build()
                )
                .build()
        )

        if (settings.mapTileDebugEnabled) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Tile Diagnostics")
                    .addText("View recent tile loading errors and network codes")
                    .setOnClickListener {
                        screenManager.push(AutoTileDiagnosticsScreen(carContext))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Clear Tile Cache")
                    .addText("Force a fresh redownload of all map tiles")
                    .setOnClickListener {
                        AutoSurfaceRenderer.clearTileCache()
                        try {
                            carContext.getCarService(androidx.car.app.AppManager::class.java)
                                .showToast("Tile cache cleared", androidx.car.app.CarToast.LENGTH_SHORT)
                        } catch (_: Exception) {}
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_map_settings)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

class AutoTileDiagnosticsScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val errors = AutoSurfaceRenderer.getRecentTileErrors()
        val contentText = if (errors.isEmpty()) {
            "No tile download errors recorded."
        } else {
            errors.joinToString("\n\n") { err ->
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(err.timestamp))
                "[$time] HTTP ${err.statusCode}\nURL: ${err.url}\nError: ${err.errorMessage}"
            }
        }

        return MessageTemplate.Builder(contentText)
            .setHeader(
                Header.Builder()
                    .setTitle("Tile Diagnostics")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Clear Logs")
                    .setOnClickListener {
                        AutoSurfaceRenderer.clearRecentTileErrors()
                        invalidate()
                    }
                    .build()
            )
            .build()
    }
}
