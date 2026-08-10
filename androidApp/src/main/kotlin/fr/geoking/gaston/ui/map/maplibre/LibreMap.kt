package fr.geoking.gaston.ui.map.maplibre

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
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
import fr.geoking.gaston.poi.Poi
import org.maplibre.android.camera.CameraPosition
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
    effectivePowerLevels: Set<Int>
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paddingBottomPx = with(density) { contentPaddingBottom.roundToPx() }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val lastPaddingBottomPx = remember { intArrayOf(-1) }

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
            map.setPadding(0, 0, 0, paddingBottomPx)
            lastPaddingBottomPx[0] = paddingBottomPx
            onMapReady(map)
            MapLibreSharedHelper.initPoiLayer(map)
        },
        onMapClick = { latLng ->
            val map = mapLibreMap ?: return@MapLibreView
            val screenPoint = map.projection.toScreenLocation(latLng)
            val screenX = screenPoint.x.toFloat()
            val screenY = screenPoint.y.toFloat()
            val tolerance = 32f // pixels tolerance for easier tap targets on phone screen
            val rect = RectF(
                screenX - tolerance,
                screenY - tolerance,
                screenX + tolerance,
                screenY + tolerance
            )
            val features = map.queryRenderedFeatures(rect, MapLibreSharedHelper.POI_LAYER_ID)
            val ids = features.mapNotNull { it.getStringProperty(MapLibreSharedHelper.POI_ID_PROPERTY) }.toSet()
            val matchedPois = poisInView.filter { it.id in ids }

            if (matchedPois.isNotEmpty()) {
                val nearestPoi = matchedPois.minByOrNull { poi ->
                    val screenPos = map.projection.toScreenLocation(LatLng(poi.latitude, poi.longitude))
                    val dx = screenX - screenPos.x
                    val dy = screenY - screenPos.y
                    dx * dx + dy * dy
                }
                onPoiClick(nearestPoi)
            } else {
                onPoiClick(null)
            }
        },
        update = { map ->
            if (lastPaddingBottomPx[0] != paddingBottomPx) {
                map.setPadding(0, 0, 0, paddingBottomPx)
                lastPaddingBottomPx[0] = paddingBottomPx
            }
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
        }
    )
}
