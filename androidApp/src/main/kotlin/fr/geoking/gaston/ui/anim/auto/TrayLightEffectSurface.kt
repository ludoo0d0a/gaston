package fr.geoking.gaston.ui.anim.auto

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.min

/**
 * Tray light effect for Android Auto surface (ported from Compose TrayLightCanvas).
 * Draws a bottom gradient bar with rounded top corners; alpha pulses when active.
 */
object TrayLightEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    /** Height of the tray as a fraction of surface height (phone uses ~100dp). */
    private const val TRAY_HEIGHT_FRACTION = 0.2f

    /** Radius of top corners as fraction of tray height. */
    private const val CORNER_RADIUS_FRACTION = 0.5f

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        isActive: Boolean,
        pulse: Float,
        palette: AnimationPalette
    ) {
        val trayHeight = min(height * TRAY_HEIGHT_FRACTION, 120f).coerceAtLeast(40f)
        val topY = height - trayHeight
        val cornerRadius = trayHeight * CORNER_RADIUS_FRACTION

        val alpha = if (isActive) {
            (0.3f + 0.5f * pulse).coerceIn(0f, 1f)
        } else {
            0.2f
        }
        val trayColor = palette.primary
        val color = android.graphics.Color.argb(
            (alpha * 255).toInt().coerceIn(0, 255),
            android.graphics.Color.red(trayColor),
            android.graphics.Color.green(trayColor),
            android.graphics.Color.blue(trayColor)
        )

        path.reset()
        path.moveTo(0f, height.toFloat())
        path.lineTo(width.toFloat(), height.toFloat())
        path.lineTo(width.toFloat(), topY + cornerRadius)
        path.quadTo(width.toFloat(), topY, width - cornerRadius, topY)
        path.lineTo(cornerRadius, topY)
        path.quadTo(0f, topY, 0f, topY + cornerRadius)
        path.close()

        paint.shader = LinearGradient(
            0f, topY, 0f, height.toFloat(),
            android.graphics.Color.TRANSPARENT,
            color,
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
        paint.shader = null
    }
}
