package fr.geoking.gaston.ui.anim.genart.shady

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported generative art shader from drinkthestars/shady: Noodle Zoom.
 * Raymarched 3D twisting noodle tunnel with rotating coordinates and glowing color palette.
 */
object ShadyNoodleZoomEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun f(px: Float, py: Float, pz: Float, time: Float): Float {
        val z = pz - time * 10f
        val a = z * 0.1f
        val cosA = cos(a)
        val sinA = sin(a)
        val rotX = px * cosA + py * sinA
        val rotY = -px * sinA + py * cosA
        return 0.1f - (abs(cos(rotX) + sin(rotY)))
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

        val gridSize = 40
        val cellW = w / gridSize
        val cellH = h / gridSize

        paint.style = Paint.Style.FILL

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val dx = 0.5f - ix.toFloat() / gridSize
                val dy = 0.5f - iy.toFloat() / gridSize
                val dz = 1.0f

                var px = 0f
                var py = 0f
                var pz = 0f

                for (i in 0 until 16) {
                    val dist = f(px, py, pz, time)
                    px += dist * dx
                    py += dist * dy
                    pz += dist * dz
                }

                val pLength = sqrt(px * px + py * py + pz * pz).coerceAtLeast(0.1f)
                val colorValX = (sin(px) + 2f) / pLength
                val colorValY = (sin(py) + 5f) / pLength
                val colorValZ = (sin(pz) + 12f) / pLength

                val normColor = ((colorValX + colorValY + colorValZ) / 3f).coerceIn(0f, 1.5f)

                val r = (Color.red(primary) / 255f * normColor).coerceIn(0f, 1f)
                val g = (Color.green(secondary) / 255f * normColor).coerceIn(0f, 1f)
                val b = (Color.blue(primary) / 255f * normColor).coerceIn(0f, 1f)

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
