package fr.geoking.gaston.ui.anim.genart.shady

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Ported generative art shader from drinkthestars/shady: Marbled Paper Texture.
 * Multi-frequency simplex noise grain with random specs, squiggly fibers, and organic paper texture.
 */
object ShadyMarbledPaperEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun pseudoNoise(u: Float, v: Float): Float {
        val sinVal = sin(u * 12.9898f + v * 78.233f) * 43758.5453f
        return sinVal - floor(sinVal)
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
        val speed = if (isActive) 0.5f else 0.25f
        val time = elapsedSec * speed

        val primary = palette.primary
        val secondary = palette.secondary
        val tertiary = palette.tertiary

        val gridSize = 40
        val cellW = w / gridSize
        val cellH = h / gridSize

        paint.style = Paint.Style.FILL

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val u = ix.toFloat() / gridSize
                val v = iy.toFloat() / gridSize

                // Base dynamic gradient background
                val bgGrad = (sin(u * 3f + time) * cos(v * 3f + time) + 1f) * 0.5f
                var baseR = Color.red(primary) * bgGrad + Color.red(secondary) * (1f - bgGrad)
                var baseG = Color.green(primary) * bgGrad + Color.green(secondary) * (1f - bgGrad)
                var baseB = Color.blue(primary) * bgGrad + Color.blue(secondary) * (1f - bgGrad)

                // Multi-scale noise textures
                val grain = pseudoNoise(u * 50f + time * 0.1f, v * 50f) * 0.1f - 0.05f
                val fiber = pseudoNoise(u * 120f, v * 120f + time * 0.05f) * 0.1f - 0.05f
                val dots = pseudoNoise(u * 200f, v * 200f)

                var specOffset = 0f
                if ((dots * 10f) - floor(dots * 10f) > 0.82f) {
                    specOffset = 25f
                }

                var fiberOffset = 0f
                if ((fiber * 10f + v * 10f) - floor(fiber * 10f + v * 10f) > 0.93f) {
                    fiberOffset = 40f
                }

                var r = (baseR + grain * 255f + fiber * 255f + specOffset + fiberOffset).coerceIn(0f, 255f)
                var g = (baseG + grain * 255f + fiber * 255f + specOffset + fiberOffset).coerceIn(0f, 255f)
                var b = (baseB + grain * 255f + fiber * 255f + specOffset + fiberOffset).coerceIn(0f, 255f)

                // Blend subtle tertiary highlight
                if (pulse > 0.5f) {
                    val tFrac = (pulse - 0.5f) * 0.2f
                    r = r * (1f - tFrac) + Color.red(tertiary) * tFrac
                    g = g * (1f - tFrac) + Color.green(tertiary) * tFrac
                    b = b * (1f - tFrac) + Color.blue(tertiary) * tFrac
                }

                val alpha = if (isActive) (0.9f + 0.1f * pulse) else 0.8f
                paint.color = Color.argb((alpha * 255).toInt(), r.toInt(), g.toInt(), b.toInt())

                canvas.drawRect(
                    ix * cellW, iy * cellH,
                    (ix + 1) * cellW + 1f, (iy + 1) * cellH + 1f,
                    paint
                )
            }
        }
    }
}
