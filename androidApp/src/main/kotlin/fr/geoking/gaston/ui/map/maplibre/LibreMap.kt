package fr.geoking.gaston.ui.map.maplibre

import android.content.Context
import android.graphics.PointF
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
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun LibreMap(
    modifier: Modifier = Modifier,
    styleUrl: String,
    initialCameraPosition: Pair<LatLng, Double>,
    contentPaddingBottom: Dp,
    onMapReady: (MapLibreMap) -> Unit,
    poisInView: List<Poi>,
    selectedPoiId: String?,
    availabilityByPoiId: Map<String, StationAvailabilitySummary>,
    onPoiClick: (Poi) -> Unit,
    effectiveEnergyTypes: Set<String>,
    effectivePowerLevels: Set<Int>
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paddingBottomPx = with(density) { contentPaddingBottom.roundToPx() }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    MapLibreView(
        modifier = modifier,
        styleUrl = styleUrl,
        cameraPosition = CameraPosition.Builder()
            .target(initialCameraPosition.first)
            .zoom(initialCameraPosition.second)
            .build(),
        onMapReady = { map ->
            mapLibreMap = map
            map.setPadding(0, 0, 0, paddingBottomPx)
            onMapReady(map)

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
        },
        onMapClick = { latLng ->
            val map = mapLibreMap ?: return@MapLibreView
            val screenPoint = map.projection.toScreenLocation(latLng)
            val features = map.queryRenderedFeatures(PointF(screenPoint.x.toFloat(), screenPoint.y.toFloat()), "poi-layer")
            val poiId = features.firstOrNull()?.getStringProperty("poi-id") ?: return@MapLibreView
            val poi = poisInView.firstOrNull { it.id == poiId } ?: return@MapLibreView
            onPoiClick(poi)
        },
        update = { map ->
            map.setPadding(0, 0, 0, paddingBottomPx)
            updatePoisAndIcons(
                context = context,
                map = map,
                poisInView = poisInView,
                selectedPoiId = selectedPoiId,
                availabilityByPoiId = availabilityByPoiId,
                effectiveEnergyTypes = effectiveEnergyTypes,
                effectivePowerLevels = effectivePowerLevels
            )
        }
    )
}

private fun updatePoisAndIcons(
    context: Context,
    map: MapLibreMap,
    poisInView: List<Poi>,
    selectedPoiId: String?,
    availabilityByPoiId: Map<String, StationAvailabilitySummary>,
    effectiveEnergyTypes: Set<String>,
    effectivePowerLevels: Set<Int>
) {
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

        val features = poisInView.map { poi ->
            val isSelected = poi.id == selectedPoiId
            val availability = availabilityByPoiId[poi.id]

            val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
                context = context,
                poi = poi,
                effectiveEnergyTypes = effectiveEnergyTypes,
                effectivePowerLevels = effectivePowerLevels,
                isSelected = isSelected,
                cheapestRank = null,
                sizePx = if (isSelected) 150 else 120,
                availability = availability,
                markerStyle = MarkerStyle.Bubble
            )

            if (style.getImage(poi.id) != null) {
                style.removeImage(poi.id)
            }
            style.addImage(poi.id, markerBitmap)

            Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
                addStringProperty("poi-id", poi.id)
            }
        }

        style.getSourceAs<GeoJsonSource>("poi-source")
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
}

