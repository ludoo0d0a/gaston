package fr.geoking.gaston.ui.map.maplibre

import android.content.Context
import fr.geoking.gaston.aac.DangerZoneTriangle
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.api.traffic.TrafficSeverity
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.resolveAvailabilitySummary
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
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
    private const val RADAR_ZONE_FILL_COLOR_PROPERTY = "fill-color"
    private const val RADAR_ZONE_FILL_OPACITY_PROPERTY = "fill-opacity"
    private const val RADAR_ZONE_STROKE_COLOR_PROPERTY = "stroke-color"
    private const val RADAR_ZONE_KIND_PROPERTY = "kind"
    const val USER_LOCATION_SOURCE_ID = "user-location-source"
    const val USER_LOCATION_LAYER_ID = "user-location-layer"
    const val USER_LOCATION_ICON_ID = "user-location-arrow-icon"
    const val HEADING_PROPERTY = "user-heading"
    const val RAINVIEWER_SOURCE_ID = "rainviewer-radar-source"
    const val RAINVIEWER_LAYER_ID = "rainviewer-radar-layer"
    const val TRAFFIC_ZONE_SOURCE_ID = "traffic-zone-source"
    const val TRAFFIC_ZONE_FILL_LAYER_ID = "traffic-zone-fill"
    const val TRAFFIC_ZONE_LINE_LAYER_ID = "traffic-zone-line"
    const val TRAFFIC_POINT_SOURCE_ID = "traffic-point-source"
    const val TRAFFIC_POINT_LAYER_ID = "traffic-point-layer"
    private const val TRAFFIC_COLOR_PROPERTY = "traffic-color"
    private const val TRAFFIC_FILL_OPACITY_PROPERTY = "traffic-fill-opacity"

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
     * Shared danger-zone sectors around radar amenity POIs (full + km + 200 m rings).
     * Geometry and colours from [DangerZoneTriangle.overlayRingsForRadarPoi].
     */
    fun syncRadarDangerZoneLayer(map: MapLibreMap, pois: List<Poi>) {
        map.getStyle { style ->
            if (style.getSource(RADAR_ZONE_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(RADAR_ZONE_SOURCE_ID))
            }
            if (style.getLayer(RADAR_ZONE_FILL_LAYER_ID) == null) {
                val fill = FillLayer(RADAR_ZONE_FILL_LAYER_ID, RADAR_ZONE_SOURCE_ID).withProperties(
                    PropertyFactory.fillColor(Expression.get(RADAR_ZONE_FILL_COLOR_PROPERTY)),
                    PropertyFactory.fillOpacity(Expression.get(RADAR_ZONE_FILL_OPACITY_PROPERTY)),
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(fill, POI_LAYER_ID)
                } else {
                    style.addLayer(fill)
                }
            }
            if (style.getLayer(RADAR_ZONE_LINE_LAYER_ID) == null) {
                val line = LineLayer(RADAR_ZONE_LINE_LAYER_ID, RADAR_ZONE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(Expression.get(RADAR_ZONE_STROKE_COLOR_PROPERTY)),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineOpacity(0.9f),
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(line, POI_LAYER_ID)
                } else {
                    style.addLayer(line)
                }
            }

            val source = style.getSourceAs<GeoJsonSource>(RADAR_ZONE_SOURCE_ID) ?: return@getStyle
            val features = pois.filter { it.poiCategory == PoiCategory.Radar }.flatMap { poi ->
                DangerZoneTriangle.overlayRingsForRadarPoi(poi).mapNotNull { overlay ->
                    if (overlay.points.size < 4) return@mapNotNull null
                    val ring = overlay.points.map { (lat, lon) -> Point.fromLngLat(lon, lat) }
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                        addStringProperty(RADAR_ZONE_FILL_COLOR_PROPERTY, argbToRgbHex(overlay.fillArgb))
                        addNumberProperty(RADAR_ZONE_FILL_OPACITY_PROPERTY, argbAlpha(overlay.fillArgb).toDouble())
                        addStringProperty(RADAR_ZONE_STROKE_COLOR_PROPERTY, argbToRgbHex(overlay.strokeArgb))
                        addStringProperty(RADAR_ZONE_KIND_PROPERTY, overlay.kind.name)
                    }
                }
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    private fun argbToRgbHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun argbAlpha(argb: Int): Float = ((argb ushr 24) and 0xFF) / 255f

    /**
     * Draws traffic events (zone polygons + point markers) when the traffic overlay is enabled.
     * Pass an empty list to clear. Placed below POI symbols so station pins stay on top.
     */
    fun syncTrafficLayer(map: MapLibreMap, events: List<TrafficEvent>) {
        map.getStyle { style ->
            if (style.getSource(TRAFFIC_ZONE_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(TRAFFIC_ZONE_SOURCE_ID))
            }
            if (style.getSource(TRAFFIC_POINT_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(TRAFFIC_POINT_SOURCE_ID))
            }
            if (style.getLayer(TRAFFIC_ZONE_FILL_LAYER_ID) == null) {
                val fill = FillLayer(TRAFFIC_ZONE_FILL_LAYER_ID, TRAFFIC_ZONE_SOURCE_ID).withProperties(
                    PropertyFactory.fillColor(Expression.get(TRAFFIC_COLOR_PROPERTY)),
                    PropertyFactory.fillOpacity(Expression.get(TRAFFIC_FILL_OPACITY_PROPERTY)),
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(fill, POI_LAYER_ID)
                } else {
                    style.addLayer(fill)
                }
            }
            if (style.getLayer(TRAFFIC_ZONE_LINE_LAYER_ID) == null) {
                val line = LineLayer(TRAFFIC_ZONE_LINE_LAYER_ID, TRAFFIC_ZONE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(Expression.get(TRAFFIC_COLOR_PROPERTY)),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineOpacity(0.9f),
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(line, POI_LAYER_ID)
                } else {
                    style.addLayer(line)
                }
            }
            if (style.getLayer(TRAFFIC_POINT_LAYER_ID) == null) {
                val circles = CircleLayer(TRAFFIC_POINT_LAYER_ID, TRAFFIC_POINT_SOURCE_ID).withProperties(
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleColor(Expression.get(TRAFFIC_COLOR_PROPERTY)),
                    PropertyFactory.circleStrokeWidth(1.5f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleOpacity(0.95f),
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(circles, POI_LAYER_ID)
                } else {
                    style.addLayer(circles)
                }
            }

            val zoneFeatures = ArrayList<Feature>()
            val pointFeatures = ArrayList<Feature>()
            for (event in events) {
                val color = trafficSeverityColorHex(event.severity)
                val zonePoints = event.zonePoints
                if (zonePoints != null && zonePoints.size >= 2) {
                    val ringLatLng = if (zonePoints.size == 2) {
                        val p1 = zonePoints[0]
                        val p2 = zonePoints[1]
                        val latMin = minOf(p1.first, p2.first)
                        var latMax = maxOf(p1.first, p2.first)
                        val lonMin = minOf(p1.second, p2.second)
                        var lonMax = maxOf(p1.second, p2.second)
                        if (latMax == latMin) latMax += 0.0001
                        if (lonMax == lonMin) lonMax += 0.0001
                        listOf(
                            latMin to lonMin,
                            latMax to lonMin,
                            latMax to lonMax,
                            latMin to lonMax,
                            latMin to lonMin,
                        )
                    } else {
                        val closed = ArrayList(zonePoints)
                        if (closed.first() != closed.last()) {
                            closed.add(closed.first())
                        }
                        closed
                    }
                    if (ringLatLng.size >= 4) {
                        val ring = ringLatLng.map { (lat, lon) -> Point.fromLngLat(lon, lat) }
                        zoneFeatures.add(
                            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                                addStringProperty(TRAFFIC_COLOR_PROPERTY, color)
                                addNumberProperty(TRAFFIC_FILL_OPACITY_PROPERTY, 0.3)
                            }
                        )
                    }
                }
                val poiLat = event.poiLatitude ?: continue
                val poiLon = event.poiLongitude ?: continue
                pointFeatures.add(
                    Feature.fromGeometry(Point.fromLngLat(poiLon, poiLat)).apply {
                        addStringProperty(TRAFFIC_COLOR_PROPERTY, color)
                    }
                )
            }
            style.getSourceAs<GeoJsonSource>(TRAFFIC_ZONE_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(zoneFeatures))
            style.getSourceAs<GeoJsonSource>(TRAFFIC_POINT_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(pointFeatures))
        }
    }

    private fun trafficSeverityColorHex(severity: TrafficSeverity): String = when (severity) {
        TrafficSeverity.Normal -> "#4CAF50"
        TrafficSeverity.Congestion -> "#FF9800"
        TrafficSeverity.Closure, TrafficSeverity.Accident, TrafficSeverity.Roadworks -> "#E53935"
        TrafficSeverity.Unknown -> "#FFC107"
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

    private val rainViewerTemplateByMap = java.util.concurrent.ConcurrentHashMap<Int, String>()

    /**
     * RainViewer radar raster tiles (as-is). Pass null [tileUrlTemplate] to hide the layer.
     * Template must use `{z}/{x}/{y}` placeholders.
     */
    fun syncRainViewerLayer(map: MapLibreMap, tileUrlTemplate: String?) {
        val mapKey = System.identityHashCode(map)
        map.getStyle { style ->
            if (tileUrlTemplate.isNullOrBlank()) {
                style.getLayer(RAINVIEWER_LAYER_ID)?.let { style.removeLayer(it) }
                style.getSource(RAINVIEWER_SOURCE_ID)?.let { style.removeSource(it) }
                rainViewerTemplateByMap.remove(mapKey)
                return@getStyle
            }
            if (rainViewerTemplateByMap[mapKey] == tileUrlTemplate &&
                style.getSource(RAINVIEWER_SOURCE_ID) != null &&
                style.getLayer(RAINVIEWER_LAYER_ID) != null
            ) {
                return@getStyle
            }
            style.getLayer(RAINVIEWER_LAYER_ID)?.let { style.removeLayer(it) }
            style.getSource(RAINVIEWER_SOURCE_ID)?.let { style.removeSource(it) }
            val tileSet = TileSet("2.1.0", tileUrlTemplate).apply {
                minZoom = 1f
                maxZoom = 7f
            }
            style.addSource(RasterSource(RAINVIEWER_SOURCE_ID, tileSet, 256))
            val layer = RasterLayer(RAINVIEWER_LAYER_ID, RAINVIEWER_SOURCE_ID).withProperties(
                PropertyFactory.rasterOpacity(0.55f),
            )
            if (style.getLayer(POI_LAYER_ID) != null) {
                style.addLayerBelow(layer, POI_LAYER_ID)
            } else {
                style.addLayer(layer)
            }
            rainViewerTemplateByMap[mapKey] = tileUrlTemplate
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
