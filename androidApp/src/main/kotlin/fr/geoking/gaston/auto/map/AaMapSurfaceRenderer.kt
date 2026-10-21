package fr.geoking.gaston.auto.map

import android.graphics.Rect
import androidx.car.app.SurfaceContainer
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.components.AutoSurfaceRenderer
import fr.geoking.gaston.ui.map.components.MapOrientationMode

/**
 * Common surface-map renderer API for Android Auto canvas modes (MapLibre, Mapsforge).
 * [fr.geoking.gaston.auto.map.CustomMapPoiScreen] uses [AutoSurfaceRenderer] directly and is unchanged.
 */
interface AaMapSurfaceRenderer {
    var hudModeLabel: String
    var offlineUnavailable: Boolean

    /** Called (on the main thread) when the renderer cannot produce a map any more; the screen falls back to another mode. */
    var onFatalError: (() -> Unit)?
        get() = null
        set(_) {}

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
    /** Traffic zone/point overlay (MapLibre GL). Canvas modes usually get traffic as [Poi] pins. */
    fun updateTraffic(events: List<TrafficEvent>) {}
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
