package fr.geoking.gaston.ui.map.maplibre

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.routing.RouteResult
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.components.MapScaffold
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory

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

    val context = LocalContext.current
    val initialCameraPosition = remember(route) {
        route?.points?.firstOrNull()?.let { point ->
            CameraPosition.Builder()
                .target(LatLng(point.first, point.second))
                .zoom(10.0)
                .build()
        } ?: CameraPosition.Builder()
            .target(LatLng(48.8566, 2.3522))
            .zoom(10.0)
            .build()
    }

    val effectiveProviders = settings.effectiveProviders()
    val filteredPois = remember(pois, settings, effectiveProviders) {
        fr.geoking.gaston.StationMapFilters.apply(
            settings = settings,
            pois = pois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = false
        )
    }

    MapScaffold(
        title = "Navigation Preview",
        settingsManager = settingsManager,
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
        isLoading = false
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                styleUrl = settings.mapTheme.styleUrl,
                cameraPosition = initialCameraPosition,
                onMapReady = { map ->
                    mapLibreMap = map

                    // Draw route polyline
                    route?.points?.let { points ->
                        val latLngPoints = points.map { LatLng(it.first, it.second) }
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(latLngPoints)
                                .color(android.graphics.Color.CYAN)
                                .width(5f)
                        )
                    }

                    // Draw markers for POIs along route
                    filteredPois.forEach { poi ->
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(poi.latitude, poi.longitude))
                                .title(poi.name)
                                .snippet(poi.address)
                        )
                    }
                },
                update = { map ->
                    map.clear()
                    // Re-draw route polyline
                    route?.points?.let { points ->
                        val latLngPoints = points.map { LatLng(it.first, it.second) }
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(latLngPoints)
                                .color(android.graphics.Color.CYAN)
                                .width(5f)
                        )
                    }

                    val energyTypes = settings.effectiveMapEnergyFilterIds()
                    val powerLevels = settings.effectiveIrvePowerLevels()

                    filteredPois.forEach { poi ->
                        val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
                            context = context,
                            poi = poi,
                            effectiveEnergyTypes = energyTypes,
                            effectivePowerLevels = powerLevels,
                            isSelected = false,
                            sizePx = 120,
                            availability = null, // Availability not easily available here
                            markerStyle = MarkerStyle.Bubble
                        )
                        val icon = IconFactory.getInstance(context).fromBitmap(markerBitmap)
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(poi.latitude, poi.longitude))
                                .icon(icon)
                        )
                    }
                }
            )
        }
    }
}
