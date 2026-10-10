package fr.geoking.gaston.ui.map.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.weather.RainViewerMapsClient
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.PhoneMapPoiHitTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

@Composable
fun LibreMap(
    modifier: Modifier = Modifier,
    styleUrl: String,
    styleJson: String? = null,
    initialCameraPosition: Pair<LatLng, Double>,
    contentPaddingBottom: Dp,
    onMapReady: (MapLibreMap) -> Unit,
    poisInView: List<Poi>,
    selectedPoiId: String?,
    availabilityByPoiId: Map<String, StationAvailabilitySummary>,
    onPoiClick: (Poi?) -> Unit,
    effectiveEnergyTypes: Set<String>,
    effectivePowerLevels: Set<Int>,
    userLat: Double? = null,
    userLon: Double? = null,
    userHeading: Float = 0f,
    /** When true, fetch RainViewer radar tiles and show as a raster under POIs. */
    weatherRadarEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paddingBottomPx = with(density) { contentPaddingBottom.roundToPx() }
    val rainViewerClient = koinInject<RainViewerMapsClient>()

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val lastPaddingBottomPx = remember { intArrayOf(-1) }
    var rainViewerTileUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(weatherRadarEnabled) {
        if (!weatherRadarEnabled) {
            rainViewerTileUrl = null
            return@LaunchedEffect
        }
        rainViewerTileUrl = withContext(Dispatchers.IO) {
            runCatching { rainViewerClient.latestRadarFrame()?.tileUrlTemplate }.getOrNull()
        }
    }

    val syncToken = remember(
        poisInView,
        selectedPoiId,
        availabilityByPoiId,
        effectiveEnergyTypes,
        effectivePowerLevels,
        userLat,
        userLon,
        userHeading,
        rainViewerTileUrl,
        weatherRadarEnabled,
    ) {
        arrayOf(
            poisInView,
            selectedPoiId,
            availabilityByPoiId,
            effectiveEnergyTypes,
            effectivePowerLevels,
            userLat,
            userLon,
            userHeading,
            rainViewerTileUrl,
            weatherRadarEnabled,
        )
    }

    MapLibreView(
        modifier = modifier,
        styleUrl = styleUrl,
        styleJson = styleJson,
        cameraPosition = CameraPosition.Builder()
            .target(initialCameraPosition.first)
            .zoom(initialCameraPosition.second)
            .build(),
        onMapReady = { map ->
            mapLibreMap = map
            map.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, paddingBottomPx.toDouble()))
            lastPaddingBottomPx[0] = paddingBottomPx
            onMapReady(map)
            MapLibreSharedHelper.initPoiLayer(map)
        },
        onMapClick = { latLng ->
            val map = mapLibreMap ?: return@MapLibreView
            val screenPoint = map.projection.toScreenLocation(latLng)
            val nearestPoi = PhoneMapPoiHitTest.findNearestPoiAtScreenPoint(
                screenX = screenPoint.x,
                screenY = screenPoint.y,
                pois = poisInView,
                markerWidthPx = 120,
            ) { poi ->
                val pos = map.projection.toScreenLocation(LatLng(poi.latitude, poi.longitude))
                pos.x to pos.y
            }
            onPoiClick(nearestPoi)
        },
        syncToken = syncToken,
        update = { map ->
            if (lastPaddingBottomPx[0] != paddingBottomPx) {
                map.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, paddingBottomPx.toDouble()))
                lastPaddingBottomPx[0] = paddingBottomPx
            }
            MapLibreSharedHelper.syncRainViewerLayer(
                map = map,
                tileUrlTemplate = if (weatherRadarEnabled) rainViewerTileUrl else null,
            )
            MapLibreSharedHelper.syncPoiLayer(
                context = context,
                map = map,
                pois = poisInView,
                selectedPoiId = selectedPoiId,
                availabilityByPoiId = availabilityByPoiId,
                effectiveEnergyTypes = effectiveEnergyTypes,
                effectivePowerLevels = effectivePowerLevels,
                sizeProvider = { _, isSelected -> if (isSelected) 150 else 120 }
            )
            MapLibreSharedHelper.syncUserLocationLayer(
                context = context,
                map = map,
                userLat = userLat,
                userLon = userLon,
                userHeading = userHeading
            )
        }
    )
}
