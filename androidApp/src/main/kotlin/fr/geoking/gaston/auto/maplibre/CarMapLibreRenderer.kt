package fr.geoking.gaston.auto.maplibre

import android.graphics.Canvas
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Copies MapLibre [TextureView] frames onto the Android Auto [SurfaceContainer] surface.
 */
class CarMapLibreRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) {
    private val mapContainer = CarMapContainer(carContext, lifecycle)
    private val uiHandler = Handler(Looper.getMainLooper())

    private var surfaceContainer: SurfaceContainer? = null
    private var styleUrl: String? = null
    private var centerLat: Double = 48.8566
    private var centerLon: Double = 2.3522
    private var zoom: Int = AutoMapCamera.DEFAULT_ZOOM
    private var orientationMode: MapOrientationMode = MapOrientationMode.NorthUp
    private var headingDegrees: Float = 0f
    private var selectedPoiId: String? = null
    private var lastPois: List<Poi> = emptyList()
    private var effectiveEnergyTypes: Set<String> = emptySet()
    private var effectivePowerLevels: Set<Int> = emptySet()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var frameListenersAttached = false
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null

    val map: MapLibreMap?
        get() = mapContainer.mapLibreMapInstance

    init {
        mapContainer.onMapReady = { map ->
            val url = styleUrl
            if (url != null) {
                map.setStyle(url) {
                    applyCamera(map)
                    syncPoiLayer()
                    syncSearchRadiusLayer()
                }
            } else {
                applyCamera(map)
                syncPoiLayer()
                syncSearchRadiusLayer()
            }
        }
    }

    fun setStyleUrl(url: String) {
        if (styleUrl == url) return
        styleUrl = url
        mapContainer.setStyleUrl(url)
    }

    fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        centerLat = lat
        centerLon = lon
        zoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
    }

    fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        headingDegrees = bearing
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
    }

    fun setMapOrientation(mode: MapOrientationMode, bearing: Float = headingDegrees) {
        orientationMode = mode
        headingDegrees = bearing
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
    }

    fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapContainer.mapLibreMapInstance?.let { map ->
            map.animateCamera(CameraUpdateFactory.zoomTo(zoom.toDouble()))
        }
    }

    fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        availability: Map<String, StationAvailabilitySummary> = availabilityByPoiId,
        selectedId: String? = selectedPoiId
    ) {
        lastPois = newPois
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
        availabilityByPoiId = availability
        selectedPoiId = selectedId
        syncPoiLayer()
    }

    /**
     * Draws a red stroke circle for the nearby station search boundary.
     * Pass [radiusKm] null to hide.
     */
    fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?) {
        if (searchRadiusCenterLat == centerLat &&
            searchRadiusCenterLon == centerLon &&
            searchRadiusKm == radiusKm
        ) {
            return
        }
        searchRadiusCenterLat = centerLat
        searchRadiusCenterLon = centerLon
        searchRadiusKm = radiusKm
        syncSearchRadiusLayer()
    }

    fun findPoisAt(screenX: Float, screenY: Float): List<Poi> {
        val map = mapContainer.mapLibreMapInstance ?: return emptyList()
        val features = map.queryRenderedFeatures(PointF(screenX, screenY), POI_LAYER_ID)
        val ids = features.mapNotNull { it.getStringProperty(POI_ID_PROPERTY) }.toSet()
        return lastPois.filter { it.id in ids }
    }

    fun zoomForHitTest(): Int = zoom

    fun mapLatForHitTest(): Double = centerLat

    fun mapLonForHitTest(): Double = centerLon

    fun attachSurface(container: SurfaceContainer) {
        surfaceContainer = container
        mapContainer.setSurfaceSize(container.width, container.height)
        attachFrameListeners()
        drawOnSurface()
    }

    fun detachSurface() {
        detachFrameListeners()
        surfaceContainer = null
        uiHandler.removeCallbacksAndMessages(null)
    }

    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        mapContainer.onScale(focusX, focusY, scaleFactor)
    }

    fun scrollBy(distanceX: Float, distanceY: Float) {
        mapContainer.scrollBy(distanceX, distanceY)
    }

    private fun attachFrameListeners() {
        if (frameListenersAttached) return
        val mapView = mapContainer.mapViewInstance ?: return
        mapView.addOnDidBecomeIdleListener { drawOnSurface() }
        mapView.addOnWillStartRenderingFrameListener { drawOnSurface() }
        frameListenersAttached = true
    }

    private fun detachFrameListeners() {
        frameListenersAttached = false
    }

    private fun drawOnSurface() {
        val mapView = mapContainer.mapViewInstance ?: return
        val surface = surfaceContainer?.surface ?: return
        val canvas = surface.lockHardwareCanvas() ?: return
        try {
            drawMapOnCanvas(mapView, canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawMapOnCanvas(mapView: MapView, canvas: Canvas) {
        val textureView = mapView.takeIf { it.childCount > 0 }?.getChildAt(0) as? TextureView
        textureView?.bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun applyCamera(map: MapLibreMap) {
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(centerLat, centerLon))
                    .zoom(zoom.toDouble())
                    .bearing(bearing.toDouble())
                    .build(),
            ),
        )
    }

    private fun syncPoiLayer() {
        val map = mapContainer.mapLibreMapInstance ?: return
        map.getStyle { style ->
            if (style.getSource(POI_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(POI_SOURCE_ID))
            }
            if (style.getLayer(POI_LAYER_ID) == null) {
                style.addLayer(
                    SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                        PropertyFactory.iconImage("{$POI_ID_PROPERTY}"),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                    ),
                )
            }
            val features = lastPois.map { poi ->
                val bitmap = PoiMarkerHelper.getMarkerBitmap(
                    context = carContext,
                    poi = poi,
                    effectiveEnergyTypes = effectiveEnergyTypes,
                    effectivePowerLevels = effectivePowerLevels,
                    isSelected = poi.id == selectedPoiId,
                    cheapestRank = null,
                    sizePx = 96,
                    availability = availabilityByPoiId[poi.id],
                    markerStyle = MarkerStyle.Bubble,
                )
                if (style.getImage(poi.id) != null) style.removeImage(poi.id)
                style.addImage(poi.id, bitmap)
                Feature.fromGeometry(Point.fromLngLat(poi.longitude, poi.latitude)).apply {
                    addStringProperty(POI_ID_PROPERTY, poi.id)
                }
            }
            style.getSourceAs<GeoJsonSource>(POI_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    private fun syncSearchRadiusLayer() {
        val map = mapContainer.mapLibreMapInstance ?: return
        map.getStyle { style ->
            if (style.getSource(SEARCH_RADIUS_SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SEARCH_RADIUS_SOURCE_ID))
            }
            if (style.getLayer(SEARCH_RADIUS_LAYER_ID) == null) {
                val layer = LineLayer(SEARCH_RADIUS_LAYER_ID, SEARCH_RADIUS_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor("#FF0000"),
                    PropertyFactory.lineWidth(2.5f),
                    PropertyFactory.lineOpacity(0.9f),
                )
                if (style.getLayer(POI_LAYER_ID) != null) {
                    style.addLayerBelow(layer, POI_LAYER_ID)
                } else {
                    style.addLayer(layer)
                }
            }
            val radiusKm = searchRadiusKm
            val cLat = searchRadiusCenterLat
            val cLon = searchRadiusCenterLon
            val source = style.getSourceAs<GeoJsonSource>(SEARCH_RADIUS_SOURCE_ID) ?: return@getStyle
            if (radiusKm == null || radiusKm <= 0.0 || cLat == null || cLon == null) {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                return@getStyle
            }
            val ring = AutoMapCamera.circleLatLngRing(cLat, cLon, radiusKm).map { (lat, lon) ->
                Point.fromLngLat(lon, lat)
            }
            if (ring.size < 4) {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
                return@getStyle
            }
            source.setGeoJson(
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(LineString.fromLngLats(ring)),
                ),
            )
        }
    }

    companion object {
        private const val TAG = "CarMapLibreRenderer"
        private const val POI_SOURCE_ID = "poi-source"
        private const val POI_LAYER_ID = "poi-layer"
        private const val POI_ID_PROPERTY = "poi-id"
        private const val SEARCH_RADIUS_SOURCE_ID = "search-radius-source"
        private const val SEARCH_RADIUS_LAYER_ID = "search-radius-layer"
    }
}
