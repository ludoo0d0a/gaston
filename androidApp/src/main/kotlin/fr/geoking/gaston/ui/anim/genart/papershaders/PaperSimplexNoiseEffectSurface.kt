package fr.geoking.gaston.ui.anim.genart.papershaders

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Ported generative art shader from AndreFrelicot/paper-shaders-android: Simplex Noise.
 * Computes multi-octave simplex noise flow with stepped softness and smooth color palette interpolation.
 */
object PaperSimplexNoiseEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Simplified 2D noise algorithm for Canvas fragment sampling
    private fun snoise(u: Float, v: Float): Float {
        val s = (u + v) * 0.5f * (kotlin.math.sqrt(3f) - 1f)
        val i = floor(u + s)
        val j = floor(v + s)
        val t = (i + j) * (3f - kotlin.math.sqrt(3f)) / 6f
        val x0 = u - (i - t)
        val y0 = v - (j - t)

        val i1: Float
        val j1: Float
        if (x0 > y0) {
            i1 = 1f
            j1 = 0f
        } else {
            i1 = 0f
            j1 = 1f
        }

        val x1 = x0 - i1 + (3f - kotlin.math.sqrt(3f)) / 6f
        val y1 = y0 - j1 + (3f - kotlin.math.sqrt(3f)) / 6f
        val x2 = x0 - 1f + 2f * (3f - kotlin.math.sqrt(3f)) / 6f
        val y2 = y0 - 1f + 2f * (3f - kotlin.math.sqrt(3f)) / 6f

        var n0 = 0f
        var t0 = 0.5f - x0 * x0 - y0 * y0
        if (t0 > 0) {
            t0 *= t0
            n0 = t0 * t0 * grad(i, j, x0, y0)
        }

        var n1 = 0f
        var t1 = 0.5f - x1 * x1 - y1 * y1
        if (t1 > 0) {
            t1 *= t1
            n1 = t1 * t1 * grad(i + i1, j + j1, x1, y1)
        }

        var n2 = 0f
        var t2 = 0.5f - x2 * x2 - y2 * y2
        if (t2 > 0) {
            t2 *= t2
            n2 = t2 * t2 * grad(i + 1f, j + 1f, x2, y2)
        }

        return 70f * (n0 + n1 + n2)
    }

    private fun grad(i: Float, j: Float, x: Float, y: Float): Float {
        val h = (sin(i * 12.9898f + j * 78.233f) * 43758.5453f).let { it - floor(it) } * 8f
        val angle = h * 0.7853982f // PI / 4
        return cos(angle) * x + sin(angle) * y
    }

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
        val speed = if (isActive) 0.8f else 0.4f
        val time = elapsedSec * speed

        val paletteColors = if (palette.colors.isNotEmpty()) {
            palette.colors
        } else {
            listOf(0xFF4449CF.toInt(), 0xFFFFD1E0.toInt(), 0xFFF94446.toInt())
        }

        val gridSize = 40
        val cellW = w / gridSize
        val cellH = h / gridSize

        paint.style = Paint.Style.FILL

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val u = ix.toFloat() / gridSize
                val v = iy.toFloat() / gridSize

                val shapeUvX = (u - 0.5f) * 2f
                val shapeUvY = (v - 0.5f) * 2f

                var noise = 0.5f * snoise(shapeUvX * 1.5f, shapeUvY * 1.5f - 0.3f * time)
                noise += 0.5f * snoise(shapeUvX * 3.0f, shapeUvY * 3.0f + 0.32f * time)

                val normVal = (((noise + 1f) * 0.5f + time * 0.05f) % 1f + 1f) % 1f
                val colorIdx = normVal * paletteColors.size
                val idx0 = (colorIdx.toInt() % paletteColors.size + paletteColors.size) % paletteColors.size
                val idx1 = (idx0 + 1) % paletteColors.size
                val frac = colorIdx - floor(colorIdx)

                val c0 = paletteColors[idx0]
                val c1 = paletteColors[idx1]

                val r = (Color.red(c0) * (1f - frac) + Color.red(c1) * frac).toInt().coerceIn(0, 255)
                val g = (Color.green(c0) * (1f - frac) + Color.green(c1) * frac).toInt().coerceIn(0, 255)
                val b = (Color.blue(c0) * (1f - frac) + Color.blue(c1) * frac).toInt().coerceIn(0, 255)

                val alpha = if (isActive) (0.85f + 0.15f * pulse) else 0.75f
                paint.color = Color.argb((alpha * 255).toInt(), r, g, b)

                canvas.drawRect(
                    ix * cellW, iy * cellH,
                    (ix + 1) * cellW + 1f, (iy + 1) * cellH + 1f,
                    paint
                )
            }
        }
    }
}
