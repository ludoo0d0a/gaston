package fr.geoking.gaston.ui.anim.genart.lwp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ported generative art shader from cipold/lwp-shaders: 3D Warp Starfield & Cosmic Dust.
 * Projects 3D spatial points with depth perspective (x/z, y/z), motion vectors,
 * and glowing nebula clouds.
 */
class LwpStarfieldEffectSurface(
    private val starCount: Int = 120
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = List(starCount) { Star() }

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
        val maxDim = maxOf(w, h)
        val speed = if (isActive) 0.025f + pulse * 0.01f else 0.012f

        val primaryColor = palette.primary
        val secondaryColor = palette.secondary
        val tertiaryColor = palette.tertiary

        // Cosmic background nebula gradient
        paint.shader = RadialGradient(
            centerX + sin(elapsedSec * 0.3f) * w * 0.2f,
            centerY + cos(elapsedSec * 0.2f) * h * 0.2f,
            maxDim * 0.9f,
            intArrayOf(
                Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                Color.argb(40, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor)),
                0xFF020617.toInt()
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Render stars with 3D projection
        stars.forEach { star ->
            star.z -= speed
            if (star.z <= 0.01f) {
                star.reset()
            }

            // 3D perspective projection
            val px = centerX + (star.x / star.z) * (w * 0.5f)
            val py = centerY + (star.y / star.z) * (h * 0.5f)

            if (px in 0f..w && py in 0f..h) {
                val depthFactor = (1f - star.z).coerceIn(0f, 1f)
                val starSize = (star.size * depthFactor * (if (isActive) 1.8f else 1.2f)).coerceAtLeast(1f)

                // Previous position for streak line when moving fast
                val prevZ = star.z + speed * 1.5f
                val prevPx = centerX + (star.x / prevZ) * (w * 0.5f)
                val prevPy = centerY + (star.y / prevZ) * (h * 0.5f)

                val c = when (star.colorGroup % 3) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> tertiaryColor
                }

                val alpha = (depthFactor * (0.6f + 0.4f * star.brightness)).coerceIn(0f, 1f)

                // Warp trailing line
                paint.color = Color.argb(
                    (alpha * 0.5f * 255).toInt(),
                    Color.red(c),
                    Color.green(c),
                    Color.blue(c)
                )
                paint.strokeWidth = starSize * 0.7f
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(prevPx, prevPy, px, py, paint)

                // Star core
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(
                    (alpha * 255).toInt(),
                    if (isActive) 255 else Color.red(c),
                    if (isActive) 255 else Color.green(c),
                    Color.blue(c)
                )
                canvas.drawCircle(px, py, starSize, paint)
            }
        }
    }

    private class Star {
        var x = Random.nextFloat() * 2f - 1f
        var y = Random.nextFloat() * 2f - 1f
        var z = Random.nextFloat()
        var size = Random.nextFloat() * 3f + 1f
        var brightness = Random.nextFloat()
        var colorGroup = Random.nextInt(0, 3)

        fun reset() {
            x = Random.nextFloat() * 2f - 1f
            y = Random.nextFloat() * 2f - 1f
            z = 1f
            size = Random.nextFloat() * 3f + 1f
            brightness = Random.nextFloat()
            colorGroup = Random.nextInt(0, 3)
        }
    }
}
