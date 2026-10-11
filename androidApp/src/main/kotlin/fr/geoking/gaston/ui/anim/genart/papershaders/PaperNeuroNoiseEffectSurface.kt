package fr.geoking.gaston.ui.anim.genart.papershaders

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Ported generative art shader from AndreFrelicot/paper-shaders-android: Neuro Noise.
 * Multi-layered rotating sinusoidal neural waves with contrast and brightness color blending.
 */
object PaperNeuroNoiseEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun rotateX(x: Float, y: Float, th: Float): Float {
        return cos(th) * x + sin(th) * y
    }

    private fun rotateY(x: Float, y: Float, th: Float): Float {
        return -sin(th) * x + cos(th) * y
    }

    private fun nnShape(x: Float, y: Float, t: Float): Float {
        var u = x
        var v = y
        var sineAccX = 0f
        var sineAccY = 0f
        var resX = 0f
        var resY = 0f
        var scale = 8f

        for (j in 0 until 12) {
            val nextU = rotateX(u, v, 1f)
            val nextV = rotateY(u, v, 1f)
            u = nextU
            v = nextV

            val nextAccX = rotateX(sineAccX, sineAccY, 1f)
            val nextAccY = rotateY(sineAccX, sineAccY, 1f)
            sineAccX = nextAccX
            sineAccY = nextAccY

            val layerX = u * scale + j.toFloat() + sineAccX - t
            val layerY = v * scale + j.toFloat() + sineAccY - t

            sineAccX += sin(layerX)
            sineAccY += sin(layerY)

            resX += (0.5f + 0.5f * cos(layerX)) / scale
            resY += (0.5f + 0.5f * cos(layerY)) / scale

            scale *= 1.2f
        }
        return resX + resY
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
        val speed = if (isActive) 0.6f else 0.3f
        val time = elapsedSec * speed

        val frontColor = palette.primary
        val midColor = palette.secondary
        val backColor = palette.tertiary

        val gridSize = 40
        val cellW = w / gridSize
        val cellH = h / gridSize

        paint.style = Paint.Style.FILL

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val u = (ix.toFloat() / gridSize - 0.5f) * 0.3f
                val v = (iy.toFloat() / gridSize - 0.5f) * 0.3f

                var noise = nnShape(u, v, time * 0.5f)
                noise = 1.2f * noise * noise
                noise = noise.toDouble().pow(1.2).toFloat().coerceAtMost(1.4f)

                val blend = ((noise - 0.7f) / 0.7f).coerceIn(0f, 1f)

                // Blend mid to front color
                val rBlend = (Color.red(midColor) * (1f - blend) + Color.red(frontColor) * blend) / 255f
                val gBlend = (Color.green(midColor) * (1f - blend) + Color.green(frontColor) * blend) / 255f
                val bBlend = (Color.blue(midColor) * (1f - blend) + Color.blue(frontColor) * blend) / 255f

                val safeNoise = noise.coerceAtLeast(0f)
                val opacity = (safeNoise * 0.8f).coerceIn(0f, 1f)

                val rBack = Color.red(backColor) / 255f
                val gBack = Color.green(backColor) / 255f
                val bBack = Color.blue(backColor) / 255f

                val finalR = (rBlend * opacity + rBack * (1f - opacity)).coerceIn(0f, 1f)
                val finalG = (gBlend * opacity + gBack * (1f - opacity)).coerceIn(0f, 1f)
                val finalB = (bBlend * opacity + bBack * (1f - opacity)).coerceIn(0f, 1f)

                val alpha = if (isActive) (0.9f + 0.1f * pulse) else 0.8f
                paint.color = Color.argb(
                    (alpha * 255).toInt(),
                    (finalR * 255).toInt(),
                    (finalG * 255).toInt(),
                    (finalB * 255).toInt()
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
