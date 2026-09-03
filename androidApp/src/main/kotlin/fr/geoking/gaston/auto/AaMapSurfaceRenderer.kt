package fr.geoking.gaston.auto

import android.graphics.Rect
import androidx.car.app.SurfaceContainer
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.poi.Poi

/**
 * Common surface-map renderer API for Android Auto canvas modes.
 * Canvas screens share [AaCanvasMapControls] (zoom, compass, recenter) and [AaMapDrivingChrome]
 * (orientation + heading arrow). Custom uses [AutoSurfaceRenderer] which also implements [AaMapDrivingChrome].
 */
interface AaMapSurfaceRenderer : AaMapDrivingChrome {
    var hudModeLabel: String
    var offlineUnavailable: Boolean

    fun attachSurface(container: SurfaceContainer)
    fun detachSurface()
    fun setStyleUrl(url: String)
    fun updateLocation(lat: Double, lon: Double, zoomLevel: Int)
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
