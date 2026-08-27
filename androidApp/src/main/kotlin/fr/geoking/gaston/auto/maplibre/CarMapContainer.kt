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
 * Important: create the [MapView] only for a live Presentation display, then call
 * [resumeAfterPresented] **after** [android.app.Presentation.show]. Reusing a
 * MapView across Presentation dismiss/show often yields a black GLSurfaceView.
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
    /** Bumps on each [buildContent] / [tearDown] so late async callbacks are ignored. */
    private var generation: Int = 0

    private var scaleAnimator: Animator? = null
    var onMapReady: ((MapLibreMap) -> Unit)? = null
    var onStyleLoaded: ((String) -> Unit)? = null
    var onMapFailLoading: ((String?) -> Unit)? = null

    init {
        lifecycle.addObserver(this)
    }

    fun scrollBy(x: Float, y: Float) {
        mapLibreMapInstance?.scrollBy(-x, -y, 0)
    }

    /**
     * Builds a fresh MapView + overlay tree for [styleUrl].
     * Call [resumeAfterPresented] only after the Presentation is shown.
     */
    @MainThread
    fun buildContent(styleUrl: String): View {
        tearDown()
        val gen = ++generation
        Log.i(TAG, "buildContent gen=$gen style=$styleUrl")

        MapLibre.getInstance(carContext)
        val mapView = createMapView().also { view ->
            mapViewInstance = view
            view.onCreate(Bundle())
            view.addOnDidFailLoadingMapListener { message ->
                if (gen != generation) return@addOnDidFailLoadingMapListener
                Log.e(TAG, "MapLibre failed loading map: $message")
                onMapFailLoading?.invoke(message)
            }
            view.getMapAsync { map ->
                if (gen != generation) {
                    Log.w(TAG, "getMapAsync ignored stale gen=$gen current=$generation")
                    return@getMapAsync
                }
                mapLibreMapInstance = map
                Log.i(TAG, "MapLibreMap ready gen=$gen")
                loadStyle(map, styleUrl, gen)
                onMapReady?.invoke(map)
            }
        }
        val overlay = CarMapOverlayView(carContext).also { overlayView = it }
        val root = FrameLayout(carContext).apply {
            setBackgroundColor(android.graphics.Color.rgb(0xF0, 0xF0, 0xF0))
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

    /** Apply a new OpenFreeMap style URI on the live map (if ready). */
    @MainThread
    fun setStyleUrl(url: String) {
        val map = mapLibreMapInstance
        if (map == null) {
            Log.d(TAG, "setStyleUrl deferred (map not ready): $url")
            return
        }
        loadStyle(map, url, generation)
    }

    /** Call after [android.app.Presentation.show]. */
    @MainThread
    fun resumeAfterPresented() {
        val mapView = mapViewInstance ?: run {
            Log.w(TAG, "resumeAfterPresented: no MapView")
            return
        }
        if (!mapLifecycleStarted) {
            mapView.onStart()
            mapView.onResume()
            mapLifecycleStarted = true
            Log.i(TAG, "MapView lifecycle started (onStart/onResume) gen=$generation")
        } else {
            Log.d(TAG, "resumeAfterPresented: already started")
        }
        mapView.requestLayout()
        mapView.invalidate()
    }

    @MainThread
    fun tearDown() {
        cancelAnimator(scaleAnimator)
        val mapView = mapViewInstance
        if (mapView != null) {
            Log.i(TAG, "tearDown MapView gen=$generation started=$mapLifecycleStarted")
            try {
                if (mapLifecycleStarted) {
                    mapView.onPause()
                    mapView.onStop()
                    mapLifecycleStarted = false
                }
                mapView.onDestroy()
            } catch (e: Exception) {
                Log.w(TAG, "MapView teardown failed", e)
            }
        }
        mapLibreMapInstance = null
        mapViewInstance = null
        overlayView = null
        contentRoot = null
        generation++
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runOnMainThread { tearDown() }
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

    private fun loadStyle(map: MapLibreMap, url: String, gen: Int) {
        Log.i(TAG, "setStyle gen=$gen url=$url")
        map.setStyle(url) { style ->
            if (gen != generation) return@setStyle
            Log.i(TAG, "style loaded gen=$gen layers=${style.layers.size} url=$url")
            onStyleLoaded?.invoke(url)
        }
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
            // Default GLSurfaceView — Presentation owns the host Surface (sample Spike B).
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
