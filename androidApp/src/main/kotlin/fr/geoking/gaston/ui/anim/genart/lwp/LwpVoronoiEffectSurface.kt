package fr.geoking.gaston.ui.anim.genart.lwp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported generative art shader from cipold/lwp-shaders: Voronoi / Cellular Caustics.
 * Evaluates nearest seed node distance metrics, cell border luminescence,
 * and liquid caustic light distribution.
 */
object LwpVoronoiEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        val time = elapsedSec * (if (isActive) 1.2f else 0.6f)

        // Vignette background
        paint.shader = RadialGradient(
            centerX, centerY, maxOf(w, h) * 0.8f,
            intArrayOf(0xFF0F172A.toInt(), 0xFF020617.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 8 dynamic Voronoi seed points floating in normalized space [-1, 1]
        val nodeCount = 8
        val nodeX = FloatArray(nodeCount)
        val nodeY = FloatArray(nodeCount)
        for (i in 0 until nodeCount) {
            val fi = i.toFloat()
            nodeX[i] = sin(time * (0.8f + fi * 0.1f) + fi * 1.5f) * 0.4f
            nodeY[i] = cos(time * (0.7f + fi * 0.12f) + fi * 2.1f) * 0.4f
        }

        val gridSize = 36
        val cellW = w / gridSize
        val cellH = h / gridSize

        val paletteColors = if (palette.colors.isNotEmpty()) palette.colors else listOf(0xFF06B6D4.toInt(), 0xFF38BDF8.toInt())

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val nx = (ix.toFloat() / gridSize - 0.5f)
                val ny = (iy.toFloat() / gridSize - 0.5f)

                var minDist1 = 1e9f
                var minDist2 = 1e9f
                var closestNode = 0

                for (i in 0 until nodeCount) {
                    val dx = nx - nodeX[i]
                    val dy = ny - nodeY[i]
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < minDist1) {
                        minDist2 = minDist1
                        minDist1 = dist
                        closestNode = i
                    } else if (dist < minDist2) {
                        minDist2 = dist
                    }
                }

                // Voronoi edge glow (difference between 2nd nearest and 1st nearest distance)
                val edgeFactor = (minDist2 - minDist1).coerceIn(0f, 0.5f) * 2f
                val cellColor = paletteColors[closestNode % paletteColors.size]

                val brightness = if (isActive) (0.8f + 0.4f * pulse) else 0.7f
                val caustic = (1f - edgeFactor) * 0.6f + (1f - minDist1 * 2f).coerceIn(0f, 0.4f)

                val r = (Color.red(cellColor) / 255f * caustic * brightness).coerceIn(0f, 1f)
                val g = (Color.green(cellColor) / 255f * caustic * brightness).coerceIn(0f, 1f)
                val b = (Color.blue(cellColor) / 255f * caustic * brightness).coerceIn(0f, 1f)

                paint.color = Color.argb(
                    ((0.5f + 0.5f * caustic) * 255).toInt().coerceIn(0, 255),
                    (r * 255).toInt(),
                    (g * 255).toInt(),
                    (b * 255).toInt()
                )

                canvas.drawRect(
                    ix * cellW, iy * cellH,
                    (ix + 1) * cellW + 1f, (iy + 1) * cellH + 1f,
                    paint
                )
            }
        }
    }
}
