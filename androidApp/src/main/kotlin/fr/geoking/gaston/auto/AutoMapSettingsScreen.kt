package fr.geoking.gaston.auto

import fr.geoking.gaston.R
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
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

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoMapSettingsScreen", "ListTemplate") {
        val settings = settingsManager.settings.value
        val rows = mutableListOf<Row>()

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

        rows.add(
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

        rows.add(
            Row.Builder()
                .setTitle(carContext.getString(R.string.settings_map_mode))
                .addText(settings.carMapMode.displayLabel(carContext))
                .setOnClickListener {
                    AutoCarMapModeSwitcher.cycle(
                        screen = this@AutoMapSettingsScreen,
                        settingsManager = settingsManager,
                        title = carContext.getString(R.string.dashboard_nearby_stations),
                        replaceMapNow = false,
                    )
                    invalidate()
                }
                .build()
        )

        rows.add(
            Row.Builder()
                .setTitle(carContext.getString(R.string.mapsforge_offline_maps))
                .addText(carContext.getString(R.string.mapsforge_offline_maps_subtitle))
                .setOnClickListener {
                    val mapManager = fr.geoking.gaston.auto.mapsforge.MapsforgeMapManager(carContext)
                    screenManager.push(fr.geoking.gaston.auto.mapsforge.AutoMapsforgeMapManagementScreen(carContext, mapManager))
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
        rows.add(
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

        rows.add(
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

        rows.add(
            Row.Builder()
                .setTitle(carContext.getString(R.string.screen_vehicle_and_range))
                .addText("${settings.vehicleType.name}, ${settings.evRangeKm} km")
                .setOnClickListener {
                    screenManager.pop()
                    screenManager.push(AutoVehicleSettingsScreen(carContext, settingsManager))
                }
                .build()
        )

        rows.add(
            Row.Builder()
                .setTitle(carContext.getString(R.string.dev_debug_grid))
                .addText(carContext.getString(R.string.dev_debug_grid_subtitle))
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.setMapTileDebugEnabled(checked)
                        invalidate()
                    }.setChecked(settings.mapTileDebugEnabled).build()
                )
                .build()
        )

        if (settings.mapTileDebugEnabled) {
            rows.add(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.tile_diagnostics))
                    .addText(carContext.getString(R.string.tile_diagnostics_subtitle))
                    .setOnClickListener {
                        screenManager.push(AutoTileDiagnosticsScreen(carContext))
                    }
                    .build()
            )

            rows.add(
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

        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }
        val listBuilder = ItemList.Builder()
        rows.take(listLimit).forEach { listBuilder.addItem(it) }

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(carContext.getString(R.string.screen_map_settings)).setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

class AutoTileDiagnosticsScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val tileErrors = AutoSurfaceRenderer.getRecentTileErrors()
        val snapshotErrors = fr.geoking.gaston.auto.maplibre.CarMapLibreRenderer.getRecentSnapshotErrors()
        val contentText = when {
            tileErrors.isEmpty() && snapshotErrors.isEmpty() ->
                carContext.getString(R.string.tile_no_errors)
            else -> buildString {
                if (snapshotErrors.isNotEmpty()) {
                    append("MapLibre snapshot\n")
                    append(
                        snapshotErrors.joinToString("\n\n") { err ->
                            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(err.timestamp))
                            "[$time] ${err.message}"
                        }
                    )
                }
                if (tileErrors.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Raster tiles\n")
                    append(
                        tileErrors.joinToString("\n\n") { err ->
                            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(err.timestamp))
                            carContext.getString(R.string.tile_error_entry, time, err.statusCode, err.url, err.errorMessage)
                        }
                    )
                }
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
                        fr.geoking.gaston.auto.maplibre.CarMapLibreRenderer.clearRecentSnapshotErrors()
                        invalidate()
                    }
                    .build()
            )
            .build()
    }
}
