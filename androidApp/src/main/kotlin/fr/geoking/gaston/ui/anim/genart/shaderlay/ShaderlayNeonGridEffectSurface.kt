package fr.geoking.gaston.ui.anim.genart.shaderlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.sin

/**
 * Ported generative art shader from mattakins/Shaderlay: Synthwave Neon Grid & Horizon Sun.
 * Renders a 3D perspective grid moving toward a horizon line with a segmented retro sun.
 */
object ShaderlayNeonGridEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        isActive: Boolean,
        elapsedSec: Float,
        pulse: Float,
        palette: AnimationPalette
    ) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val horizonY = h * 0.52f
        val time = elapsedSec * (if (isActive) 1.5f else 0.8f)

        val primaryColor = palette.primary
        val secondaryColor = palette.secondary
        val tertiaryColor = palette.tertiary

        // Sky gradient background
        paint.shader = LinearGradient(
            0f, 0f, 0f, horizonY,
            intArrayOf(0xFF0F0C29.toInt(), 0xFF302B63.toInt(), 0xFF24243E.toInt()),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, horizonY, paint)
        paint.shader = null

        // Ground background
        paint.shader = LinearGradient(
            0f, horizonY, 0f, h,
            intArrayOf(0xFF110022.toInt(), 0xFF020005.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, horizonY, w, h, paint)
        paint.shader = null

        // Retro Sun at Horizon
        val sunRadius = h * 0.18f * (if (isActive) (1f + 0.1f * pulse) else 1f)
        val sunY = horizonY - sunRadius * 0.4f

        // Sun gradient glow
        paint.shader = RadialGradient(
            centerX, sunY, sunRadius * 1.8f,
            intArrayOf(
                Color.argb(200, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor)),
                Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, sunY, sunRadius * 1.8f, paint)
        paint.shader = null

        // Sun disk with horizontal scan cuts
        canvas.save()
        canvas.clipRect(centerX - sunRadius, sunY - sunRadius, centerX + sunRadius, horizonY)

        paint.shader = LinearGradient(
            0f, sunY - sunRadius, 0f, sunY + sunRadius,
            intArrayOf(0xFFFBBF24.toInt(), 0xFFF97316.toInt(), 0xFFEF4444.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, sunY, sunRadius, paint)
        paint.shader = null

        // Scanline cuts through sun
        paint.color = 0xFF110022.toInt()
        paint.style = Paint.Style.FILL
        val cutCount = 7
        for (i in 0 until cutCount) {
            val cutY = sunY + (i.toFloat() / cutCount) * sunRadius
            val cutH = (i + 1) * 1.8f
            canvas.drawRect(centerX - sunRadius, cutY, centerX + sunRadius, cutY + cutH, paint)
        }
        canvas.restore()

        // Horizon glow line
        paint.color = secondaryColor
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(0f, horizonY, w, horizonY, paint)

        // 3D Perspective Grid
        paint.color = primaryColor
        paint.strokeWidth = if (isActive) 2.5f else 1.5f

        // Perspective longitudinal lines emanating from horizon vanishing point
        val vLines = 18
        for (i in 0..vLines) {
            val bottomX = (i.toFloat() / vLines) * w * 2.2f - w * 0.6f
            canvas.drawLine(centerX, horizonY, bottomX, h, paint)
        }

        // Horizontal perspective lines scrolling forward
        val hLines = 14
        val speedFactor = (time * 0.6f) % 1f
        for (i in 0 until hLines) {
            val p = (i.toFloat() + speedFactor) / hLines
            // Exponential distribution for 3D depth acceleration
            val depthP = p * p
            val lineY = horizonY + depthP * (h - horizonY)
            if (lineY in horizonY..h) {
                val lineAlpha = (depthP * (if (isActive) 1f else 0.7f)).coerceIn(0f, 1f)
                paint.color = Color.argb(
                    (lineAlpha * 255).toInt(),
                    Color.red(tertiaryColor),
                    Color.green(tertiaryColor),
                    Color.blue(tertiaryColor)
                )
                canvas.drawLine(0f, lineY, w, lineY, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
}
