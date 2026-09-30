package fr.geoking.gaston.ui.map.maplibre

import android.content.Context
import fr.geoking.gaston.aac.DangerZoneDistances
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.resolveAvailabilitySummary
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

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
    const val RADAR_ZONE_SOURCE_ID = "radar-danger-zone-source"
    const val RADAR_ZONE_FILL_LAYER_ID = "radar-danger-zone-fill"
    const val RADAR_ZONE_LINE_LAYER_ID = "radar-danger-zone-line"
    const val USER_LOCATION_SOURCE_ID = "user-location-source"
    const val USER_LOCATION_LAYER_ID = "user-location-layer"
    const val USER_LOCATION_ICON_ID = "user-location-arrow-icon"
    const val HEADING_PROPERTY = "user-heading"

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
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
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
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                )
            }

            val features = pois.map { poi ->
                val isSelected = poi.id == selectedPoiId
                val availability = poi.resolveAvailabilitySummary(availabilityByPoiId[poi.id])
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
        syncRadarDangerZoneLayer(map, pois)
    }

    /**
     * VMA-based danger-zone circles around radar amenity POIs (fill + stroke).
     */
    fun syncRadarDangerZoneLayer(map: MapLibreMap, pois: List<Poi>) {
        map.getStyle { style ->
            if (style.getSource(RADAR_ZONE_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(RADAR_ZONE_SOURCE_ID))
            }
            if (style.getLayer(RADAR_ZONE_FILL_LAYER_ID) == null) {
                val fill = FillLayer(RADAR_ZONE_FILL_LAYER_ID, RADAR_ZONE_SOURCE_ID).withProperties(
                    PropertyFactory.fillColor("#EF4444"),
                    PropertyFactory.fillOpacity(0.13f)
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(fill, POI_LAYER_ID)
                } else {
                    style.addLayer(fill)
                }
            }
            if (style.getLayer(RADAR_ZONE_LINE_LAYER_ID) == null) {
                val line = LineLayer(RADAR_ZONE_LINE_LAYER_ID, RADAR_ZONE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor("#EF4444"),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineOpacity(0.9f)
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(line, POI_LAYER_ID)
                } else {
                    style.addLayer(line)
                }
            }

            val source = style.getSourceAs<GeoJsonSource>(RADAR_ZONE_SOURCE_ID) ?: return@getStyle
            val features = pois.filter { it.poiCategory == PoiCategory.Radar }.mapNotNull { poi ->
                val radiusKm = DangerZoneDistances.radiusMetersForRadarPoiVma(
                    poi.rawSourceData?.get("vma")
                ) / 1000.0
                if (radiusKm <= 0.0) return@mapNotNull null
                val ring = AutoMapCamera.circleLatLngRing(poi.latitude, poi.longitude, radiusKm).map { (lat, lon) ->
                    Point.fromLngLat(lon, lat)
                }
                if (ring.size < 4) return@mapNotNull null
                Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features))
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

    /**
     * Initializes the user location symbol layer with the blue navigation arrow icon.
     */
    fun initUserLocationLayer(context: Context, map: MapLibreMap) {
        map.getStyle { style ->
            if (style.getImage(USER_LOCATION_ICON_ID) == null) {
                val density = context.resources.displayMetrics.density
                val bitmap = fr.geoking.gaston.ui.map.UserLocationMarkerHelper.createUserLocationBitmap(density)
                style.addImage(USER_LOCATION_ICON_ID, bitmap)
            }
            if (style.getSource(USER_LOCATION_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(USER_LOCATION_SOURCE_ID))
            }
            if (style.getLayer(USER_LOCATION_LAYER_ID) == null) {
                val symbolLayer = SymbolLayer(USER_LOCATION_LAYER_ID, USER_LOCATION_SOURCE_ID).withProperties(
                    PropertyFactory.iconImage(USER_LOCATION_ICON_ID),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                    PropertyFactory.iconRotate(Expression.get(HEADING_PROPERTY)),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
                style.addLayer(symbolLayer)
            }
        }
    }

    /**
     * Synchronizes user location (blue navigation arrow) position and heading on the map style.
     */
    fun syncUserLocationLayer(
        context: Context,
        map: MapLibreMap,
        userLat: Double?,
        userLon: Double?,
        userHeading: Float
    ) {
        map.getStyle { style ->
            initUserLocationLayer(context, map)
            val source = style.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE_ID) ?: return@getStyle
            if (userLat == null || userLon == null) {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                return@getStyle
            }
            val feature = Feature.fromGeometry(Point.fromLngLat(userLon, userLat)).apply {
                addNumberProperty(HEADING_PROPERTY, userHeading)
            }
            source.setGeoJson(FeatureCollection.fromFeature(feature))
        }
    }
}
