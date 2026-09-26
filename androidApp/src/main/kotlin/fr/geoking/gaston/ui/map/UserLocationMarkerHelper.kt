package fr.geoking.gaston.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Creates user location marker bitmaps matching Android Auto's blue navigation arrow.
 */
object UserLocationMarkerHelper {
    private val NAVIGATION_BLUE = Color.parseColor("#4285F4")

    fun createUserLocationBitmap(density: Float = 2.5f): Bitmap {
        val sizePx = (48 * density).toInt().coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val radius = sizePx * 0.38f
        val arrowPath = Path().apply {
            moveTo(0f, -radius)
            lineTo(-radius * 0.8f, radius * 0.8f)
            lineTo(0f, radius * 0.4f)
            lineTo(radius * 0.8f, radius * 0.8f)
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAVIGATION_BLUE
            style = Paint.Style.FILL
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (4f * density).coerceAtLeast(2f)
            strokeJoin = Paint.Join.ROUND
        }

        canvas.save()
        canvas.translate(sizePx / 2f, sizePx / 2f)
        canvas.drawPath(arrowPath, fillPaint)
        canvas.drawPath(arrowPath, strokePaint)
        canvas.restore()

        return bitmap
    }
}
