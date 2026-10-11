package fr.geoking.gaston.ui.anim.genart.papershaders

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported generative art shader from AndreFrelicot/paper-shaders-android: Grain Gradient.
 * Multi-point dynamic mesh gradient overlayed with fine film grain noise.
 */
object PaperGrainGradientEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun pseudoNoise(u: Float, v: Float): Float {
        val sinVal = sin(u * 12.9898f + v * 78.233f) * 43758.5453f
        return sinVal - kotlin.math.floor(sinVal)
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

        val primary = palette.primary
        val secondary = palette.secondary
        val tertiary = palette.tertiary

        // Define 3 dynamic focal points moving gracefully
        val p1x = 0.3f + 0.2f * sin(time * 0.7f)
        val p1y = 0.3f + 0.2f * cos(time * 0.5f)

        val p2x = 0.7f + 0.2f * cos(time * 0.6f)
        val p2y = 0.7f + 0.2f * sin(time * 0.8f)

        val p3x = 0.5f + 0.3f * sin(time * 0.9f)
        val p3y = 0.5f + 0.3f * cos(time * 0.4f)

        val gridSize = 40
        val cellW = w / gridSize
        val cellH = h / gridSize

        paint.style = Paint.Style.FILL

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val u = ix.toFloat() / gridSize
                val v = iy.toFloat() / gridSize

                val d1 = sqrt((u - p1x) * (u - p1x) + (v - p1y) * (v - p1y))
                val d2 = sqrt((u - p2x) * (u - p2x) + (v - p2y) * (v - p2y))
                val d3 = sqrt((u - p3x) * (u - p3x) + (v - p3y) * (v - p3y))

                val w1 = 1f / (d1 + 0.1f)
                val w2 = 1f / (d2 + 0.1f)
                val w3 = 1f / (d3 + 0.1f)
                val totalWeight = w1 + w2 + w3

                val r1 = Color.red(primary) * (w1 / totalWeight)
                val g1 = Color.green(primary) * (w1 / totalWeight)
                val b1 = Color.blue(primary) * (w1 / totalWeight)

                val r2 = Color.red(secondary) * (w2 / totalWeight)
                val g2 = Color.green(secondary) * (w2 / totalWeight)
                val b2 = Color.blue(secondary) * (w2 / totalWeight)

                val r3 = Color.red(tertiary) * (w3 / totalWeight)
                val g3 = Color.green(tertiary) * (w3 / totalWeight)
                val b3 = Color.blue(tertiary) * (w3 / totalWeight)

                var r = (r1 + r2 + r3) / 255f
                var g = (g1 + g2 + g3) / 255f
                var b = (b1 + b2 + b3) / 255f

                // Add film grain noise
                val grain = (pseudoNoise(u * 100f, v * 100f) - 0.5f) * 0.08f
                r = (r + grain).coerceIn(0f, 1f)
                g = (g + grain).coerceIn(0f, 1f)
                b = (b + grain).coerceIn(0f, 1f)

                val alpha = if (isActive) (0.85f + 0.15f * pulse) else 0.75f
                paint.color = Color.argb(
                    (alpha * 255).toInt(),
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
