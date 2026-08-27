package fr.geoking.gaston.auto.maplibre

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import fr.geoking.gaston.auto.AutoMapOverlayHelper
import fr.geoking.gaston.auto.AutoMapQueryLoader

/**
 * Draws compass / scale / query loader above the MapLibre [org.maplibre.android.maps.MapView]
 * inside the Android Auto [android.app.Presentation] content hierarchy.
 */
class CarMapOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var visibleArea: Rect? = null
        set(value) {
            if (field == value) return
            field = value?.let { Rect(it) }
            invalidate()
        }

    var surfaceWidth: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var surfaceHeight: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var bearing: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var zoom: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var latitude: Double = 0.0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var mapTileDebugEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var queryPending: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        AutoMapOverlayHelper.drawCompassAndScale(
            canvas = canvas,
            context = context,
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            bearing = bearing,
            zoom = zoom,
            latitude = latitude,
            mapTileDebugEnabled = mapTileDebugEnabled,
            isDensityScaled = true,
        )
        if (queryPending) {
            AutoMapQueryLoader.draw(
                canvas = canvas,
                density = resources.displayMetrics.density,
                visibleArea = visibleArea,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
            )
        }
    }
}
