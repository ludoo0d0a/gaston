package fr.geoking.gaston.auto.map.maplibre

import android.graphics.Rect
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.auto.map.AaMapSurfaceRenderer
import fr.geoking.gaston.ui.map.components.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import org.koin.core.context.GlobalContext

/**
 * High-performance MapLibre renderer for Android Auto using VirtualDisplay + Presentation.
 * Shares [MapLibreAaGlHost] with the other GL wrappers (OpenFreeMap + GeoJSON POIs / bearing).
 */
class CarMapLibreVirtualDisplayRenderer(
    private val carContext: CarContext,
    @Suppress("UNUSED_PARAMETER") lifecycle: Lifecycle,
) : AaMapSurfaceRenderer {
    override var hudModeLabel: String = "MapLibre (VirtualDisplay)"
    override var offlineUnavailable: Boolean = false
    override var onFatalError: (() -> Unit)?
        get() = host.onFatalError
        set(value) { host.onFatalError = value }

    private val settingsManager =
        GlobalContext.get().get<SettingsManager>()
    private val host = MapLibreAaGlHost(carContext, TAG)

    init {
        host.setStyleUrl(resolveAutoMapStyleUrl(settingsManager.settings.value, carContext))
    }

    override fun currentZoom(): Int = host.zoom

    override fun requestRedraw() {
        // MapView redraws itself.
    }

    override fun setStyleUrl(url: String) {
        host.setStyleUrl(url)
    }

    override fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        host.updateLocation(lat, lon, zoomLevel)
    }

    override fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        host.updateUserLocation(lat, lon, bearing)
    }

    override fun setMapOrientation(mode: MapOrientationMode, bearing: Float) {
        host.setMapOrientation(mode, bearing)
    }

    override fun updateVisibleArea(area: Rect) {
        host.updateVisibleArea(area)
    }

    override fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        availability: Map<String, StationAvailabilitySummary>,
        selectedId: String?,
    ) {
        host.updatePois(newPois, effectiveEnergyTypes, effectivePowerLevels, availability, selectedId)
    }

    override fun updateTraffic(events: List<TrafficEvent>) {
        host.updateTraffic(events)
    }

    override fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?) {
        host.updateSearchRadius(centerLat, centerLon, radiusKm)
    }

    override fun setQueryPending(pending: Boolean) {
        // No Canvas HUD on GL surface path.
    }

    override fun findPoisAt(screenX: Float, screenY: Float): List<Poi> =
        host.findPoisAt(screenX, screenY)

    override fun zoomForHitTest(): Int = host.zoom
    override fun mapLatForHitTest(): Double = host.centerLat
    override fun mapLonForHitTest(): Double = host.centerLon
    override fun centerPxXForHitTest(): Double = host.centerPxX()
    override fun centerPxYForHitTest(): Double = host.centerPxY()

    override fun attachSurface(container: SurfaceContainer) {
        host.attachSurface(container)
    }

    override fun detachSurface() {
        host.detachSurface()
    }

    companion object {
        private const val TAG = "CarMapLibreVD"
    }
}
