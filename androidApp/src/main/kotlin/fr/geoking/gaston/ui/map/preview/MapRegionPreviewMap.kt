package fr.geoking.gaston.ui.map.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.auto.map.mapsforge.MapsforgeServerMap
import fr.geoking.gaston.auto.map.pmtiles.PmtilesServerMap
import fr.geoking.gaston.ui.map.maplibre.MapLibreView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

data class MapRegionItem(
    val key: String,
    val name: String,
    val regionName: String,
    val sizeEstimateMb: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

fun MapsforgeServerMap.toRegionItem(): MapRegionItem = MapRegionItem(
    key = storageKey,
    name = name,
    regionName = region,
    sizeEstimateMb = sizeEstimateMb,
    minLat = minLat,
    maxLat = maxLat,
    minLon = minLon,
    maxLon = maxLon,
)

fun PmtilesServerMap.toRegionItem(): MapRegionItem = MapRegionItem(
    key = storageKey,
    name = name,
    regionName = region,
    sizeEstimateMb = sizeEstimateMb,
    minLat = minLat,
    maxLat = maxLat,
    minLon = minLon,
    maxLon = maxLon,
)

@Composable
fun MapRegionPreviewMap(
    regions: List<MapRegionItem>,
    selectedRegionKey: String?,
    onRegionSelected: (MapRegionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    val initialCamera = remember {
        CameraPosition.Builder()
            .target(LatLng(46.5, 2.5))
            .zoom(4.0)
            .build()
    }

    fun syncLayers(map: MapLibreMap) {
        map.getStyle { style ->
            val sourceId = "region-preview-source"
            val fillLayerId = "region-preview-fill"
            val lineLayerId = "region-preview-line"

            if (style.getSource(sourceId) == null) {
                style.addSource(GeoJsonSource(sourceId))
            }

            if (style.getLayer(fillLayerId) == null) {
                val fill = FillLayer(fillLayerId, sourceId).withProperties(
                    PropertyFactory.fillColor(Expression.get("fillColor")),
                    PropertyFactory.fillOpacity(Expression.get("fillOpacity")),
                )
                style.addLayer(fill)
            }

            if (style.getLayer(lineLayerId) == null) {
                val line = LineLayer(lineLayerId, sourceId).withProperties(
                    PropertyFactory.lineColor(Expression.get("strokeColor")),
                    PropertyFactory.lineWidth(Expression.get("strokeWidth")),
                    PropertyFactory.lineOpacity(0.9f),
                )
                style.addLayer(line)
            }

            val features = regions.map { region ->
                val isSelected = region.key.equals(selectedRegionKey, ignoreCase = true) ||
                    region.name.equals(selectedRegionKey, ignoreCase = true)
                val ring = listOf(
                    Point.fromLngLat(region.minLon, region.minLat),
                    Point.fromLngLat(region.maxLon, region.minLat),
                    Point.fromLngLat(region.maxLon, region.maxLat),
                    Point.fromLngLat(region.minLon, region.maxLat),
                    Point.fromLngLat(region.minLon, region.minLat),
                )
                Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                    addStringProperty("regionKey", region.key)
                    addStringProperty("regionName", region.name)
                    addStringProperty("fillColor", if (isSelected) "#2563EB" else "#64748B")
                    addNumberProperty("fillOpacity", if (isSelected) 0.45 else 0.15)
                    addStringProperty("strokeColor", if (isSelected) "#1D4ED8" else "#475569")
                    addNumberProperty("strokeWidth", if (isSelected) 3.0 else 1.5)
                }
            }

            style.getSourceAs<GeoJsonSource>(sourceId)
                ?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    LaunchedEffect(selectedRegionKey, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        val selectedRegion = regions.firstOrNull {
            it.key.equals(selectedRegionKey, ignoreCase = true) ||
                it.name.equals(selectedRegionKey, ignoreCase = true)
        }
        if (selectedRegion != null) {
            val bounds = LatLngBounds.from(
                selectedRegion.maxLat,
                selectedRegion.maxLon,
                selectedRegion.minLat,
                selectedRegion.minLon
            )
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 40))
            } catch (e: Exception) {
                android.util.Log.e("MapRegionPreviewMap", "Failed to fit camera bounds", e)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
    ) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            cameraPosition = initialCamera,
            onMapReady = { map ->
                mapInstance = map
                syncLayers(map)
            },
            onMapClick = { point ->
                val clickedRegion = regions.find { r ->
                    point.latitude in r.minLat..r.maxLat && point.longitude in r.minLon..r.maxLon
                }
                if (clickedRegion != null) {
                    onRegionSelected(clickedRegion)
                }
            },
            syncToken = selectedRegionKey to regions.size,
            update = { map ->
                syncLayers(map)
            }
        )
    }
}
