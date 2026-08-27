package fr.geoking.gaston.auto.maplibre

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import fr.geoking.gaston.auto.AutoMapOverlayHelper
import fr.geoking.gaston.auto.AutoMapQueryLoader

/**
 * Draws compass / scale / query loader / MapLibre debug HUD above the MapLibre [org.maplibre.android.maps.MapView]
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

    /** Extra MapLibre AA diagnostic lines (shown when [mapTileDebugEnabled]). */
    var debugLines: List<String> = emptyList()
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
        if (mapTileDebugEnabled && debugLines.isNotEmpty()) {
            drawDebugHud(canvas)
        }
    }

    private fun drawDebugHud(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val area = visibleArea ?: Rect(0, 0, width, height)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            typeface = Typeface.MONOSPACE
        }
        val bgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
        }
        val pad = 6f * density
        val lineHeight = 14f * density
        val blockHeight = pad * 2 + debugLines.size * lineHeight
        val maxWidth = debugLines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val left = area.left + 8f * density
        val top = area.top + 48f * density
        canvas.drawRect(left, top, left + maxWidth + pad * 2, top + blockHeight, bgPaint)
        var y = top + pad + lineHeight * 0.8f
        for (line in debugLines) {
            canvas.drawText(line, left + pad, y, textPaint)
            y += lineHeight
        }
    }
}
