package fr.geoking.gaston.ui.map.maplibre

import android.content.Context
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Shared utility to deduplicate and share MapLibre layer configuration, POI symbol markers, and
 * search radius circle rendering between Phone-based and Auto-based vector map surfaces.
 */
object MapLibreSharedHelper {
    const val POI_SOURCE_ID = "poi-source"
    const val POI_LAYER_ID = "poi-layer"
    const val POI_ID_PROPERTY = "poi-id"
    const val SEARCH_RADIUS_SOURCE_ID = "search-radius-source"
    const val SEARCH_RADIUS_LAYER_ID = "search-radius-layer"

    /**
     * Initializes the POI source and symbol layer.
     */
    fun initPoiLayer(map: MapLibreMap) {
        map.getStyle { style ->
            if (style.getSource(POI_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(POI_SOURCE_ID))
            }
            if (style.getLayer(POI_LAYER_ID) == null) {
                style.addLayer(
                    SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                        PropertyFactory.iconImage("{$POI_ID_PROPERTY}"),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                )
            }
        }
    }

    /**
     * Synchronizes POI icons, status availability, and geojoson markers on the map style.
     */
    fun syncPoiLayer(
        context: Context,
        map: MapLibreMap,
        pois: List<Poi>,
        selectedPoiId: String?,
        availabilityByPoiId: Map<String, StationAvailabilitySummary>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        sizeProvider: (Poi, Boolean) -> Int
    ) {
        map.getStyle { style ->
            if (style.getSource(POI_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(POI_SOURCE_ID))
            }
            if (style.getLayer(POI_LAYER_ID) == null) {
                style.addLayer(
                    SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                        PropertyFactory.iconImage("{$POI_ID_PROPERTY}"),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                )
            }

            val features = pois.map { poi ->
                val isSelected = poi.id == selectedPoiId
                val availability = availabilityByPoiId[poi.id]
                val size = sizeProvider(poi, isSelected)

                val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
                    context = context,
                    poi = poi,
                    effectiveEnergyTypes = effectiveEnergyTypes,
                    effectivePowerLevels = effectivePowerLevels,
                    isSelected = isSelected,
                    cheapestRank = null,
                    sizePx = size,
                    availability = availability,
                    markerStyle = MarkerStyle.Bubble
                )

                if (style.getImage(poi.id) != null) {
                    style.removeImage(poi.id)
                }
                style.addImage(poi.id, markerBitmap)

                Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
                    addStringProperty(POI_ID_PROPERTY, poi.id)
                }
            }

            style.getSourceAs<GeoJsonSource>(POI_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    /**
     * Synchronizes the red circle search radius boundary.
     */
    fun syncSearchRadiusLayer(
        map: MapLibreMap,
        centerLat: Double?,
        centerLon: Double?,
        radiusKm: Double?
    ) {
        map.getStyle { style ->
            if (style.getSource(SEARCH_RADIUS_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SEARCH_RADIUS_SOURCE_ID))
            }
            if (style.getLayer(SEARCH_RADIUS_LAYER_ID) == null) {
                val layer = LineLayer(SEARCH_RADIUS_LAYER_ID, SEARCH_RADIUS_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor("#FF0000"),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineOpacity(0.9f)
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(layer, POI_LAYER_ID)
                } else {
                    style.addLayer(layer)
                }
            }

            val source = style.getSourceAs<GeoJsonSource>(SEARCH_RADIUS_SOURCE_ID) ?: return@getStyle
            if (radiusKm == null || radiusKm <= 0.0 || centerLat == null || centerLon == null) {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                return@getStyle
            }

            val ring = AutoMapCamera.circleLatLngRing(centerLat, centerLon, radiusKm).map { (lat, lon) ->
                Point.fromLngLat(lon, lat)
            }
            if (ring.size < 4) {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                return@getStyle
            }

            source.setGeoJson(
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(LineString.fromLngLats(ring))
                )
            )
        }
    }
}
