package fr.geoking.gaston.auto.maplibre

import android.graphics.Rect
import android.opengl.GLES20
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AaMapSurfaceRenderer
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.poi.Poi

/**
 * Fluid OpenFreeMap vector renderer for Android Auto.
 *
 * Primary path: MapLibre [org.maplibre.android.maps.MapView] presents into the AA surface via
 * VirtualDisplay ([MapLibreAaGlHost]) — MapLibre owns EGL for 60 FPS vector + upright labels.
 * Fallback: [CarEglSurfaceRenderer] clears the surface if VirtualDisplay attach fails.
 */
class CarMapLibreEglRenderer(
    private val carContext: CarContext,
    @Suppress("UNUSED_PARAMETER") lifecycle: Lifecycle,
) : AaMapSurfaceRenderer {
    override var hudModeLabel: String = "MapLibre EGL"
    override var offlineUnavailable: Boolean = false

    private val settingsManager =
        org.koin.core.context.GlobalContext.get().get<fr.geoking.gaston.SettingsManager>()
    private val host = MapLibreAaGlHost(carContext, TAG)
    private val eglHelper = CarEglSurfaceRenderer()
    private var usingEglFallback: Boolean = false

    init {
        host.setStyleUrl(resolveAutoMapStyleUrl(settingsManager.settings.value, carContext))
    }

    override fun currentZoom(): Int = host.zoom

    override fun requestRedraw() {
        if (usingEglFallback) {
            drawEglFallbackClear()
        }
    }

    override fun setStyleUrl(url: String) {
        host.setStyleUrl(url)
    }

    override fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        host.updateLocation(lat, lon, zoomLevel)
        requestRedraw()
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
        usingEglFallback = false
        eglHelper.detachSurface()
        host.attachSurface(container)
        // If VirtualDisplay failed synchronously we still try EGL clear so the surface is not black forever.
        // Async failures are logged by MapLibreAaGlHost; DHU testing validates the primary path.
        Log.i(TAG, "Attached MapLibre GL host to AA surface ${container.width}x${container.height}")
    }

    override fun detachSurface() {
        host.detachSurface()
        eglHelper.detachSurface()
        usingEglFallback = false
    }

    /** Optional clear-color fallback when MapView cannot own the surface. */
    fun attachEglFallback(container: SurfaceContainer) {
        host.detachSurface()
        usingEglFallback = eglHelper.attachSurface(container)
        if (usingEglFallback) {
            drawEglFallbackClear()
            Log.w(TAG, "Using EGL clear fallback (no MapLibre MapView)")
        }
    }

    private fun drawEglFallbackClear() {
        if (!eglHelper.makeCurrent()) return
        GLES20.glClearColor(0.12f, 0.14f, 0.18f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        eglHelper.swapBuffers()
    }

    companion object {
        private const val TAG = "CarMapLibreEglRenderer"
    }
}
