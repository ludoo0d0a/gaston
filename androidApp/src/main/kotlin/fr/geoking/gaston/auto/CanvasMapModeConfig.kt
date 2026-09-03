package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.maplibre.CarMapLibreRenderer
import fr.geoking.gaston.auto.maplibre.MapsforgeAaRenderer
import fr.geoking.gaston.auto.maplibre.resolveAutoMapStyleUrl
import fr.geoking.gaston.auto.maplibre.resolveMapTilerStyleUrl
import fr.geoking.gaston.auto.maplibre.resolveProtomapsLocalPath
import fr.geoking.gaston.di.MapDeps
import fr.geoking.gaston.poi.Poi

/**
 * Per-mode configuration for canvas map screens derived from [MapLibrePoiScreen].
 * [CustomMapPoiScreen] is unchanged and does not use this config.
 */
data class CanvasMapModeConfig(
    val logTag: String,
    val carMapMode: CarMapMode,
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
        fun mapLibre(carContext: CarContext): CanvasMapModeConfig = CanvasMapModeConfig(
            logTag = "MapLibrePoiScreen",
            carMapMode = CarMapMode.MapLibre,
            hudLabel = carContext.getString(R.string.map_mode_maplibre),
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

        fun mapTiler(carContext: CarContext): CanvasMapModeConfig = mapLibre(carContext).copy(
            logTag = "MapTilerPoiScreen",
            carMapMode = CarMapMode.MapTiler,
            hudLabel = carContext.getString(R.string.map_mode_maptiler),
            styleUrlResolver = { settings, _ -> resolveMapTilerStyleUrl(settings) },
        )

        fun protomaps(carContext: CarContext): CanvasMapModeConfig = mapLibre(carContext).copy(
            logTag = "ProtomapsPoiScreen",
            carMapMode = CarMapMode.Protomaps,
            hudLabel = carContext.getString(R.string.map_mode_protomaps),
            requiresOfflineFile = true,
            styleUrlResolver = { settings, ctx ->
                if (resolveProtomapsLocalPath(settings) != null) {
                    resolveAutoMapStyleUrl(settings, ctx)
                } else {
                    null
                }
            },
        )

        fun mapsforge(carContext: CarContext): CanvasMapModeConfig = CanvasMapModeConfig(
            logTag = "MapsforgePoiScreen",
            carMapMode = CarMapMode.Mapsforge,
            hudLabel = carContext.getString(R.string.map_mode_mapsforge),
            requiresOfflineFile = true,
            styleUrlResolver = { _, _ -> null },
            createRenderer = { ctx, lifecycle, config ->
                MapsforgeAaRenderer(ctx, lifecycle).apply {
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
