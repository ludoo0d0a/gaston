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
                    append(carContext.getString(R.string.action_auto_by_country))
                    if (!countryLine.isNullOrBlank()) {
                        append('\n').append(carContext.getString(R.string.network_country_label, countryLine))
                    }
                }
            }
            PoiProviderSelectionMode.Manual ->
                if (settings.selectedPoiProviders.isEmpty()) carContext.getString(R.string.network_none)
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
            MapTheme.Bright -> carContext.getString(R.string.map_theme_bright)
            MapTheme.Liberty -> carContext.getString(R.string.map_theme_liberty)
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
                    when (settings.carMapMode) {
                        CarMapMode.Custom, CarMapMode.MapLibre ->
                            carContext.getString(R.string.map_mode_current_custom, settings.carMapMode.name)
                        CarMapMode.Native ->
                            carContext.getString(R.string.map_mode_current_native, settings.carMapMode.name)
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

        if (settings.mapTileDebugEnabled) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.tile_diagnostics))
                    .addText(carContext.getString(R.string.tile_diagnostics_subtitle))
                    .setOnClickListener {
                        screenManager.push(AutoTileDiagnosticsScreen(carContext))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.tile_clear_cache))
                    .addText(carContext.getString(R.string.tile_clear_cache_subtitle))
                    .setOnClickListener {
                        AutoSurfaceRenderer.clearTileCache()
                        try {
                            carContext.getCarService(androidx.car.app.AppManager::class.java)
                                .showToast(
                                    carContext.getString(R.string.tile_cache_cleared),
                                    androidx.car.app.CarToast.LENGTH_SHORT
                                )
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
            carContext.getString(R.string.tile_no_errors)
        } else {
            errors.joinToString("\n\n") { err ->
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(err.timestamp))
                carContext.getString(R.string.tile_error_entry, time, err.statusCode, err.url, err.errorMessage)
            }
        }

        return MessageTemplate.Builder(contentText)
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.tile_diagnostics))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.settings_clear_logs))
                    .setOnClickListener {
                        AutoSurfaceRenderer.clearRecentTileErrors()
                        invalidate()
                    }
                    .build()
            )
            .build()
    }
}
