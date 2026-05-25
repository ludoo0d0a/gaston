package fr.geoking.gaston.auto.maplibre

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.car.app.CarContext
import fr.geoking.gaston.auto.carWindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.constants.MapLibreConstants
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/**
 * Offscreen MapLibre [MapView] (texture mode) attached via [fr.geoking.gaston.auto.carWindowManager].
 * Pattern from [MapLibre-Android-Auto-Sample](https://github.com/maplibre/MapLibre-Android-Auto-Sample).
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

    var surfaceWidth: Int? = null
    var surfaceHeight: Int? = null

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

    fun setSurfaceSize(surfaceWidth: Int, surfaceHeight: Int) {
        if (this.surfaceWidth == surfaceWidth && this.surfaceHeight == surfaceHeight) return
        this.surfaceWidth = surfaceWidth
        this.surfaceHeight = surfaceHeight
        mapViewInstance?.let { view ->
            carContext.carWindowManager.updateViewLayout(view, windowLayoutParams())
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        MapLibre.getInstance(carContext)
        runOnMainThread {
            mapViewInstance = createMapView().apply {
                carContext.carWindowManager.addView(this, windowLayoutParams())
                onStart()
                getMapAsync { map ->
                    mapViewInstance = this@apply
                    mapLibreMapInstance = map
                    val styleUrl = pendingStyleUrl
                    if (styleUrl != null) {
                        map.setStyle(styleUrl)
                    }
                    onMapReady?.invoke(map)
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runOnMainThread {
            mapLibreMapInstance = null
            mapViewInstance?.run {
                onStop()
                onDestroy()
                carContext.carWindowManager.removeView(this)
            }
            mapViewInstance = null
        }
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
            MapLibreMapOptions.createFromAttributes(carContext).apply {
                textureMode(true)
            },
        ).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, Paint())
        }

    private fun windowLayoutParams() = WindowManager.LayoutParams(
        surfaceWidth ?: WindowManager.LayoutParams.MATCH_PARENT,
        surfaceHeight ?: WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION,
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.RGBX_8888,
    )

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        const val DOUBLE_CLICK_FACTOR = 2.0f
    }
}
