package fr.geoking.gaston.ui.anim.genart.shady

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Ported generative art shader from drinkthestars/shady: Warp Speed 2 (David Hoskins).
 * Chaotic fractal loop creating a high-speed hyperspace star warp tunnel.
 */
object ShadyWarpSpeedEffectSurface {
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
        val speed = if (isActive) 1.2f else 0.6f
        val time = (elapsedSec * speed - 2f) * 58f

        val primary = palette.primary
        val secondary = palette.secondary

        val initX = sin(time * 0.0032f) * 0.3f
        val initY = 0.35f - cos(time * 0.005f) * 0.3f
        val initZ = time * 0.002f

        val gridSize = 40
        val cellW = w / gridSize
        val cellH = h / gridSize

        paint.style = Paint.Style.FILL

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val uvX = (ix.toFloat() / gridSize) * 2f - 1f
                val uvY = (iy.toFloat() / gridSize) * 2f - 1f

                var s = 0f
                var v = 0f
                var colR = 0f
                var colG = 0f
                var colB = 0f

                val outerIter = 24
                for (r in 0 until outerIter) {
                    var px = initX + s * uvX
                    var py = initY + s * uvY
                    var pz = initZ + s * 0.05f

                    pz -= kotlin.math.floor(pz)

                    for (i in 0 until 6) {
                        val dotP = (px * px + py * py + pz * pz).coerceAtLeast(1e-5f)
                        px = abs(px * 2.04f) / dotP - 0.9f
                        py = abs(py * 2.04f) / dotP - 0.9f
                        pz = abs(pz * 2.04f) / dotP - 0.9f
                    }

                    val dotP = px * px + py * py + pz * pz
                    v += dotP.toDouble().pow(0.7).toFloat() * 0.06f

                    colR += (v * 0.2f + 0.4f) * v * 0.0003f
                    colG += (12f - s * 2f) * v * 0.00003f
                    colB += (0.1f + v * 1f) * v * 0.0003f

                    s += 0.035f
                }

                // Blend with active palette colors
                val rFinal = (colR * (Color.red(primary) / 255f) * 8f).coerceIn(0f, 1f)
                val gFinal = (colG * (Color.green(secondary) / 255f) * 8f).coerceIn(0f, 1f)
                val bFinal = (colB * (Color.blue(primary) / 255f) * 8f).coerceIn(0f, 1f)

                val alpha = if (isActive) (0.85f + 0.15f * pulse) else 0.75f
                paint.color = Color.argb(
                    (alpha * 255).toInt(),
                    (rFinal * 255).toInt(),
                    (gFinal * 255).toInt(),
                    (bFinal * 255).toInt()
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
