package fr.geoking.gaston.auto.map

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.map.maplibre.CarMapLibreRenderer
import fr.geoking.gaston.auto.map.maplibre.CarMapLibreVirtualDisplayRenderer
import fr.geoking.gaston.auto.map.mapbox.CarMapboxRenderer
import fr.geoking.gaston.auto.map.maplibre.resolveAutoMapStyleUrl
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.components.MapOrientationMode

/**
 * Per-mode configuration for canvas map screens derived from [MapLibrePoiScreen].
 * [CustomMapPoiScreen] is unchanged and does not use this config.
 */
data class CanvasMapModeConfig(
    val logTag: String,
    val hudLabel: String,
    val requiresOfflineFile: Boolean = false,
    val styleUrlResolver: (fr.geoking.gaston.AppSettings, CarContext) -> String?,
    val createRenderer: (CarContext, Lifecycle, CanvasMapModeConfig) -> AaMapSurfaceRenderer,
    val createStationDetailScreen: (
        carContext: CarContext,
        poi: Poi,
        availability: StationAvailabilitySummary?,
        searchLat: Double,
        searchLon: Double,
        zoom: Int,
        orientationMode: MapOrientationMode,
        bearing: Float,
        effectiveEnergies: Set<String>,
        effectivePowerLevels: Set<Int>,
        settingsManager: SettingsManager,
        favoritesRepo: fr.geoking.gaston.community.FavoritesRepository?,
        onDisposed: () -> Unit,
    ) -> Screen,
) {
    companion object {
        fun mapLibreSnapshotter(carContext: CarContext): CanvasMapModeConfig = CanvasMapModeConfig(
            logTag = "MapLibrePoiScreen",
            hudLabel = carContext.getString(R.string.map_mode_maplibre_snapshotter),
            styleUrlResolver = { settings, ctx -> resolveAutoMapStyleUrl(settings, ctx) },
            createRenderer = { ctx, lifecycle, config ->
                CarMapLibreRenderer(ctx, lifecycle).apply {
                    hudModeLabel = config.hudLabel
                }
            },
            createStationDetailScreen = { carContext, poi, availability, searchLat, searchLon, zoom, orientationMode, bearing, effectiveEnergies, effectivePowerLevels, settingsManager, favoritesRepo, onDisposed ->
                MapLibreStationDetailScreen(
                    carContext = carContext,
                    poi = poi,
                    availability = availability,
                    searchLat = searchLat,
                    searchLon = searchLon,
                    zoom = zoom,
                    orientationMode = orientationMode,
                    bearing = bearing,
                    effectiveEnergies = effectiveEnergies,
                    effectivePowerLevels = effectivePowerLevels,
                    settingsManager = settingsManager,
                    favoritesRepo = favoritesRepo,
                    onDisposed = onDisposed,
                )
            },
        )

        fun mapbox(carContext: CarContext): CanvasMapModeConfig = CanvasMapModeConfig(
            logTag = "MapboxPoiScreen",
            hudLabel = carContext.getString(R.string.map_mode_mapbox),
            styleUrlResolver = { _, _ -> "mapbox://styles/mapbox/streets-v12" },
            createRenderer = { ctx, lifecycle, config ->
                CarMapboxRenderer(ctx, lifecycle).apply {
                    hudModeLabel = config.hudLabel
                }
            },
            createStationDetailScreen = { carContext, poi, availability, searchLat, searchLon, zoom, orientationMode, bearing, effectiveEnergies, effectivePowerLevels, settingsManager, favoritesRepo, onDisposed ->
                MapLibreStationDetailScreen(
                    carContext = carContext,
                    poi = poi,
                    availability = availability,
                    searchLat = searchLat,
                    searchLon = searchLon,
                    zoom = zoom,
                    orientationMode = orientationMode,
                    bearing = bearing,
                    effectiveEnergies = effectiveEnergies,
                    effectivePowerLevels = effectivePowerLevels,
                    settingsManager = settingsManager,
                    favoritesRepo = favoritesRepo,
                    onDisposed = onDisposed,
                )
            },
        )

        fun mapLibreVirtualDisplay(carContext: CarContext): CanvasMapModeConfig = CanvasMapModeConfig(
            logTag = "MapLibrePresPoiScreen",
            hudLabel = carContext.getString(R.string.map_mode_maplibre_virtualdisplay),
            styleUrlResolver = { settings, ctx -> resolveAutoMapStyleUrl(settings, ctx) },
            createRenderer = { ctx, lifecycle, config ->
                CarMapLibreVirtualDisplayRenderer(ctx, lifecycle).apply {
                    hudModeLabel = config.hudLabel
                }
            },
            createStationDetailScreen = { carContext, poi, availability, searchLat, searchLon, zoom, orientationMode, bearing, effectiveEnergies, effectivePowerLevels, settingsManager, favoritesRepo, onDisposed ->
                MapLibreStationDetailScreen(
                    carContext = carContext,
                    poi = poi,
                    availability = availability,
                    searchLat = searchLat,
                    searchLon = searchLon,
                    zoom = zoom,
                    orientationMode = orientationMode,
                    bearing = bearing,
                    effectiveEnergies = effectiveEnergies,
                    effectivePowerLevels = effectivePowerLevels,
                    settingsManager = settingsManager,
                    favoritesRepo = favoritesRepo,
                    onDisposed = onDisposed,
                )
            },
        )

    }
}
