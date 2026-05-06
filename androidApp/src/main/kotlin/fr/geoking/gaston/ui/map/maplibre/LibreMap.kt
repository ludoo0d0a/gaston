package fr.geoking.gaston.ui.map.maplibre

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.camera.CameraPosition

interface LibreMapController {
    val cameraPosition: StateFlow<CameraPosition>
    fun animateCamera(update: CameraUpdate)
}

internal class LibreMapControllerImpl(
    private val map: MapLibreMap,
    private val _cameraPosition: MutableStateFlow<CameraPosition>
) : LibreMapController {
    override val cameraPosition: StateFlow<CameraPosition> = _cameraPosition.asStateFlow()

    override fun animateCamera(update: CameraUpdate) {
        map.animateCamera(update)
    }
}

@Composable
fun LibreMap(
    modifier: Modifier = Modifier,
    styleUrl: String,
    initialCameraPosition: Pair<LatLng, Double>,
    contentPaddingBottom: Dp = 0.dp,
    onMapReady: (LibreMapController) -> Unit,
    poisInView: List<Poi>,
    selectedPoiId: String?,
    availabilityByPoiId: Map<String, StationAvailabilitySummary>,
    onPoiClick: (Poi) -> Unit,
    effectiveEnergyTypes: Set<String>,
    effectivePowerLevels: Set<Int>
) {
    val context = LocalContext.current
    var internalMap by remember { mutableStateOf<MapLibreMap?>(null) }

    val cameraPositionFlow = remember {
        MutableStateFlow(CameraPosition.Builder()
            .target(initialCameraPosition.first)
            .zoom(initialCameraPosition.second)
            .build())
    }

    // Keep track of POIs for click handling
    val currentPois = rememberUpdatedState(poisInView)

    MapLibreView(
        modifier = modifier,
        styleUrl = styleUrl,
        onMapReady = { map ->
            internalMap = map
            onMapReady(LibreMapControllerImpl(map, cameraPositionFlow))

            map.addOnCameraIdleListener {
                cameraPositionFlow.value = map.cameraPosition
            }
            map.addOnCameraMoveListener {
                cameraPositionFlow.value = map.cameraPosition
            }

            map.getStyle { style ->
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

            map.addOnMapClickListener { point ->
                val pixel = map.projection.toScreenLocation(point)
                val features = map.queryRenderedFeatures(pixel, "poi-layer")
                if (features.isNotEmpty()) {
                    val poiIdWithSelection = features[0].getStringProperty("poi-id")
                    // poi-id property in my implementation is cacheKey which is "id_isSelected_availability"
                    val poiId = poiIdWithSelection.split("_")[0]
                    val poi = currentPois.value.find { it.id == poiId }
                    if (poi != null) {
                        onPoiClick(poi)
                        return@addOnMapClickListener true
                    }
                }
                false
            }
        },
        update = { map ->
            map.getStyle { style ->
                val features = poisInView.map { poi ->
                    val isSelected = poi.id == selectedPoiId
                    val cacheKey = "${poi.id}_${isSelected}_${availabilityByPoiId[poi.id]?.availableCount}"

                    if (style.getImage(cacheKey) == null) {
                        val bitmap = PoiMarkerHelper.getMarkerBitmap(
                            context = context,
                            poi = poi,
                            effectiveEnergyTypes = effectiveEnergyTypes,
                            effectivePowerLevels = effectivePowerLevels,
                            isSelected = isSelected,
                            sizePx = if (isSelected) 160 else 120,
                            availability = availabilityByPoiId[poi.id],
                            markerStyle = MarkerStyle.Bubble
                        )
                        style.addImage(cacheKey, bitmap)
                    }

                    Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
                        addStringProperty("poi-id", cacheKey)
                    }
                }

                style.getSourceAs<GeoJsonSource>("poi-source")?.setGeoJson(FeatureCollection.fromFeatures(features))
            }
        }
    )

    LaunchedEffect(contentPaddingBottom) {
        internalMap?.let { map ->
            map.setPadding(0, 0, 0, contentPaddingBottom.value.toInt())
        }
    }
}
