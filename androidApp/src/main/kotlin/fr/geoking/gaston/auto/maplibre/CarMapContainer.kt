package fr.geoking.gaston.auto.maplibre

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.car.app.CarContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.constants.MapLibreConstants
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/**
 * MapLibre [MapView] hosted inside an Android Auto [android.app.Presentation]
 * (VirtualDisplay → host [android.view.Surface]).
 *
 * Spike B: replaces the TextureView bitmap-copy path from
 * [MapLibre-Android-Auto-Sample](https://github.com/maplibre/MapLibre-Android-Auto-Sample)
 * with the Presentation approach from
 * [PR #13](https://github.com/maplibre/MapLibre-Android-Auto-Sample/pull/13) /
 * [helw.net](https://helw.net/2025/11/16/maplibre-on-android-auto/).
 */
class CarMapContainer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) : DefaultLifecycleObserver {

    private val mainHandler = Handler(Looper.getMainLooper())

    var mapViewInstance: MapView? = null
        private set

    var mapLibreMapInstance: MapLibreMap? = null
        private set

    var overlayView: CarMapOverlayView? = null
        private set

    private var contentRoot: FrameLayout? = null
    private var mapLifecycleStarted = false

    private var scaleAnimator: Animator? = null
    private var pendingStyleUrl: String? = null
    var onMapReady: ((MapLibreMap) -> Unit)? = null

    init {
        lifecycle.addObserver(this)
    }

    fun scrollBy(x: Float, y: Float) {
        mapLibreMapInstance?.scrollBy(-x, -y, 0)
    }

    fun setStyleUrl(url: String) {
        pendingStyleUrl = url
        val map = mapLibreMapInstance ?: return
        map.setStyle(url)
    }

    /**
     * Builds (once) the Presentation content: MapView + overlay FrameLayout.
     * Caller must attach the returned view via [android.app.Presentation.setContentView].
     */
    @MainThread
    fun ensureContentView(): View {
        contentRoot?.let { return it }

        MapLibre.getInstance(carContext)
        val mapView = createMapView().also { view ->
            mapViewInstance = view
            view.onCreate(Bundle())
            view.getMapAsync { map ->
                mapLibreMapInstance = map
                val styleUrl = pendingStyleUrl
                if (styleUrl != null) {
                    map.setStyle(styleUrl)
                }
                onMapReady?.invoke(map)
            }
        }
        val overlay = CarMapOverlayView(carContext).also { overlayView = it }
        val root = FrameLayout(carContext).apply {
            addView(
                mapView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                overlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        contentRoot = root
        return root
    }

    /** Call after the content view is shown in a Presentation. */
    @MainThread
    fun startMapLifecycle() {
        val mapView = mapViewInstance ?: return
        if (!mapLifecycleStarted) {
            mapView.onStart()
            mapView.onResume()
            mapLifecycleStarted = true
            Log.d(TAG, "MapView lifecycle started (onStart/onResume)")
        }
    }

    /** Call before dismissing the Presentation / releasing the VirtualDisplay. */
    @MainThread
    fun pauseMapLifecycle() {
        val mapView = mapViewInstance ?: return
        if (mapLifecycleStarted) {
            mapView.onPause()
            mapView.onStop()
            mapLifecycleStarted = false
            Log.d(TAG, "MapView lifecycle paused (onPause/onStop)")
        }
    }

    @MainThread
    fun destroyMap() {
        pauseMapLifecycle()
        cancelAnimator(scaleAnimator)
        mapLibreMapInstance = null
        mapViewInstance?.onDestroy()
        mapViewInstance = null
        overlayView = null
        contentRoot = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runOnMainThread { destroyMap() }
    }

    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (scaleFactor == DOUBLE_CLICK_FACTOR) {
            doubleClickZoom(PointF(focusX, focusY), zoomIn = true)
            return
        }
        if (scaleFactor == -DOUBLE_CLICK_FACTOR) {
            doubleClickZoom(PointF(focusX, focusY), zoomIn = false)
            return
        }
        val currentZoom = mapLibreMapInstance?.zoom ?: return
        val zoomAdditional =
            (kotlin.math.ln(scaleFactor.toDouble()) / kotlin.math.ln(Math.PI / 2)) *
                MapLibreConstants.ZOOM_RATE
        mapLibreMapInstance?.setZoom(currentZoom + zoomAdditional, PointF(focusX, focusY), 0)
    }

    private fun doubleClickZoom(focalPoint: PointF, zoomIn: Boolean) {
        cancelAnimator(scaleAnimator)
        val currentZoom = mapLibreMapInstance?.zoom ?: return
        scaleAnimator = ValueAnimator.ofFloat(
            currentZoom.toFloat(),
            (currentZoom + if (zoomIn) 1.0 else -1.0).toFloat(),
        ).apply {
            duration = MapLibreConstants.ANIMATION_DURATION.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                mapLibreMapInstance?.setZoom(
                    (animation.animatedValue as Float).toDouble(),
                    focalPoint,
                    0,
                )
            }
            start()
        }
    }

    private fun cancelAnimator(animator: Animator?) {
        if (animator?.isStarted == true) animator.cancel()
    }

    private fun createMapView(): MapView =
        MapView(
            carContext,
            MapLibreMapOptions.createFromAttributes(carContext),
        ).apply {
            // Default GLSurfaceView path — Presentation owns the host Surface.
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint())
        }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        private const val TAG = "CarMapContainer"
        const val DOUBLE_CLICK_FACTOR = 2.0f
    }
}
