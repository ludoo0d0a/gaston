@file:Suppress("DEPRECATION")

package fr.geoking.gaston.ui.map.maplibre

import fr.geoking.gaston.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.api.routing.RouteResult
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.components.CheapestStationHighlight
import fr.geoking.gaston.ui.components.MapScaffold
import fr.geoking.gaston.ui.components.MapLocateMeButton
import fr.geoking.gaston.ui.components.MapOverlayWidgets
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsMapScreen(
    route: RouteResult?,
    pois: List<Poi>,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val settings by settingsManager.settings.collectAsState()
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraPosition by remember { mutableStateOf<CameraPosition?>(null) }

    DisposableEffect(mapLibreMap) {
        val map = mapLibreMap
        if (map == null) {
            cameraPosition = null
            onDispose { }
        } else {
            cameraPosition = map.cameraPosition
            val idleListener = MapLibreMap.OnCameraIdleListener {
                cameraPosition = map.cameraPosition
            }
            val moveListener = MapLibreMap.OnCameraMoveListener {
                cameraPosition = map.cameraPosition
            }
            map.addOnCameraIdleListener(idleListener)
            map.addOnCameraMoveListener(moveListener)
            onDispose {
                map.removeOnCameraIdleListener(idleListener)
                map.removeOnCameraMoveListener(moveListener)
            }
        }
    }

    val context = LocalContext.current
    val initialCameraPosition = remember(route) {
        route?.points?.firstOrNull()?.let { point ->
            CameraPosition.Builder()
                .target(LatLng(point.first, point.second))
                .zoom(10.0)
                .build()
        } ?: CameraPosition.Builder()
            .target(LatLng(settings.lastKnownLat ?: 48.8566, settings.lastKnownLon ?: 2.3522))
            .zoom(10.0)
            .build()
    }

    val routeLat = route?.points?.firstOrNull()?.first ?: settings.lastKnownLat ?: 48.8566
    val routeLon = route?.points?.firstOrNull()?.second ?: settings.lastKnownLon ?: 2.3522
    val effectiveProviders = remember(settings, routeLat, routeLon) {
        settings.effectiveProvidersAt(routeLat, routeLon)
    }
    val filteredPois = remember(pois, settings, effectiveProviders) {
        fr.geoking.gaston.StationMapFilters.apply(
            settings = settings,
            pois = pois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = false
        )
    }

    MapScaffold(
        title = stringResource(R.string.map_title_navigation_preview),
        settingsManager = settingsManager,
        mapCenterLatitude = route?.points?.firstOrNull()?.first,
        mapCenterLongitude = route?.points?.firstOrNull()?.second,
        onBack = onBack,
        onRefresh = { /* Route is fixed, but could refresh POIs if needed */ },
        onLocateMe = {
            route?.points?.firstOrNull()?.let { point ->
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(point.first, point.second), 15.0)
                )
            }
        },
        onShowSettings = { /* Maybe show simplified settings? */ },
        onShowSources = { /* Not used in directions map */ }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Map overlay scale widget (placed at the bottom-left)
            MapOverlayWidgets(
                bearing = (cameraPosition?.bearing ?: 0.0).toFloat(),
                zoom = (cameraPosition?.zoom ?: 10.0).toFloat(),
                latitude = cameraPosition?.target?.latitude ?: routeLat,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
                    .zIndex(1f)
            )

            MapLocateMeButton(
                onLocateMe = {
                    route?.points?.firstOrNull()?.let { point ->
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(point.first, point.second), 15.0)
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .zIndex(1f)
            )

            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                styleUrl = run {
                    val isDarkMode = false // On phone, keep map in day theme
                    if (settings.mapTheme.isDark == isDarkMode) {
                        settings.mapTheme.styleUrl
                    } else {
                        MapTheme.Voyager.styleUrl
                    }
                },
                cameraPosition = initialCameraPosition,
                onMapReady = { map ->
                    mapLibreMap = map
                    map.getStyle { style ->
                        // Setup Route Source and Layer
                        if (style.getSource("route-source") == null) {
                            val routePoints = route?.points?.map { org.maplibre.geojson.Point.fromLngLat(it.second, it.first) } ?: emptyList()
                            val routeLineString = org.maplibre.geojson.LineString.fromLngLats(routePoints)
                            style.addSource(GeoJsonSource("route-source", routeLineString))
                        }
                        if (style.getLayer("route-layer") == null) {
                            style.addLayer(
                                LineLayer("route-layer", "route-source").withProperties(
                                    PropertyFactory.lineColor(android.graphics.Color.CYAN),
                                    PropertyFactory.lineWidth(5f)
                                )
                            )
                        }

                        // Setup POI Source and Layer
                        if (style.getSource("poi-source") == null) {
                            style.addSource(GeoJsonSource("poi-source"))
                        }
                        if (style.getLayer("poi-layer") == null) {
                            style.addLayer(
                                SymbolLayer("poi-layer", "poi-source").withProperties(
                                    PropertyFactory.iconImage("{poi-id}"),
                                    PropertyFactory.iconAllowOverlap(true),
                                    PropertyFactory.iconIgnorePlacement(true)
                                )
                            )
                        }
                    }
                },
                update = { map ->
                    map.getStyle { style ->
                        val energyTypes = settings.effectiveMapEnergyFilterIds()
                        val powerLevels = settings.effectiveIrvePowerLevels()
                        val fuelIdsForCheapest = energyTypes - "electric"
                        val minPrice = CheapestStationHighlight.minFuelPrice(filteredPois, fuelIdsForCheapest)

                        val features = filteredPois.map { poi ->
                            val isCheapest = CheapestStationHighlight.isCheapestFuelStation(
                                poi = poi,
                                minPrice = minPrice,
                                fuelIds = fuelIdsForCheapest
                            )
                            val cheapestRank = if (isCheapest) 1 else null
                            // Update icon only if it doesn't exist
                            if (style.getImage(poi.id) == null) {
                                val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
                                    context = context,
                                    poi = poi,
                                    effectiveEnergyTypes = energyTypes,
                                    effectivePowerLevels = powerLevels,
                                    isSelected = false,
                                    cheapestRank = cheapestRank,
                                    sizePx = 120,
                                    availability = null,
                                    markerStyle = MarkerStyle.Bubble
                                )
                                style.addImage(poi.id, markerBitmap)
                            }

                            Feature.fromGeometry(
                                org.maplibre.geojson.Point.fromLngLat(poi.longitude, poi.latitude)
                            ).apply {
                                addStringProperty("poi-id", poi.id)
                            }
                        }

                        style.getSourceAs<GeoJsonSource>("poi-source")?.setGeoJson(FeatureCollection.fromFeatures(features))

                        // Update route if changed (though it's mostly fixed here)
                        val routePoints = route?.points?.map { org.maplibre.geojson.Point.fromLngLat(it.second, it.first) } ?: emptyList()
                        style.getSourceAs<GeoJsonSource>("route-source")?.setGeoJson(org.maplibre.geojson.LineString.fromLngLats(routePoints))
                    }
                }
            )
        }
    }
}
