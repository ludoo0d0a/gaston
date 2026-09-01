package fr.geoking.gaston.auto

import android.graphics.Rect
import androidx.car.app.SurfaceContainer
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.Poi

/**
 * Common surface-map renderer API for Android Auto canvas modes (MapLibre, MapTiler, Protomaps, Mapsforge).
 * [fr.geoking.gaston.auto.CustomMapPoiScreen] uses [AutoSurfaceRenderer] directly and is unchanged.
 */
interface AaMapSurfaceRenderer {
    var hudModeLabel: String
    var offlineUnavailable: Boolean

    fun attachSurface(container: SurfaceContainer)
    fun detachSurface()
    fun setStyleUrl(url: String)
    fun updateLocation(lat: Double, lon: Double, zoomLevel: Int)
    fun updateUserLocation(lat: Double, lon: Double, bearing: Float)
    fun setMapOrientation(mode: MapOrientationMode, bearing: Float = 0f)
    fun updateVisibleArea(area: Rect)
    fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        availability: Map<String, StationAvailabilitySummary>,
        selectedId: String? = null,
    )
    fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?)
    fun setQueryPending(pending: Boolean)
    fun requestRedraw()
    fun findPoisAt(screenX: Float, screenY: Float): List<Poi>
    fun zoomForHitTest(): Int
    fun mapLatForHitTest(): Double
    fun mapLonForHitTest(): Double
    fun centerPxXForHitTest(): Double
    fun centerPxYForHitTest(): Double
    fun currentZoom(): Int
}
