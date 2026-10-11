package fr.geoking.gaston.ui.anim.genart.ppsspp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ported generative art shader from jdgleaver/ppsspp_shaders: Cel-Shader & Edge Detection.
 * Quantizes continuous color gradients into discrete comic/cel bands and draws thick ink outlines.
 */
object PpssppCelShaderEffectSurface {
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
        val time = elapsedSec

        // Background
        paint.color = 0xFF0F172A.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)

        val primaryColor = palette.primary
        val secondaryColor = palette.secondary
        val tertiaryColor = palette.tertiary

        // Cel-shaded geometric shapes with cartoon quantization
        val baseRadius = minOf(w, h) * 0.28f * (if (isActive) (1f + 0.15f * pulse) else 1f)

        // Shape 1: Outer Rotating Hexagon/Polygon with thick black border
        val polySides = 6
        val polyRotation = time * 0.5f

        fun quantizeColor(color: Int, levels: Int = 4): Int {
            val r = (Color.red(color) / 255f * levels).toInt() * (255 / levels)
            val g = (Color.green(color) / 255f * levels).toInt() * (255 / levels)
            val b = (Color.blue(color) / 255f * levels).toInt() * (255 / levels)
            return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }

        // Concentric cel-shaded quantized color rings
        val bands = 5
        for (b in bands downTo 1) {
            val r = baseRadius * (b.toFloat() / bands)
            path.reset()
            for (i in 0 until polySides) {
                val a = polyRotation + (i.toFloat() / polySides) * 2f * Math.PI.toFloat()
                val px = centerX + cos(a) * r
                val py = centerY + sin(a) * r
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            val bandColor = when (b % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }

            // Fill quantized color
            paint.color = quantizeColor(bandColor, levels = 3)
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)

            // Ink Outline (Sobel-simulated cel border)
            paint.color = 0xFF020617.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isActive) 6f else 4f
            canvas.drawPath(path, paint)
        }

        // Floating comic dots / highlight spheres
        paint.style = Paint.Style.FILL
        val dotCount = 8
        for (i in 0 until dotCount) {
            val a = time * 0.8f + (i.toFloat() / dotCount) * 2f * Math.PI.toFloat()
            val orbitR = baseRadius * 1.4f
            val dx = centerX + cos(a) * orbitR
            val dy = centerY + sin(a) * orbitR
            val dotR = 16f

            // Dot black border
            paint.color = 0xFF000000.toInt()
            canvas.drawCircle(dx, dy, dotR + 3f, paint)

            // Dot fill
            paint.color = quantizeColor(secondaryColor, levels = 2)
            canvas.drawCircle(dx, dy, dotR, paint)
        }
    }
}
