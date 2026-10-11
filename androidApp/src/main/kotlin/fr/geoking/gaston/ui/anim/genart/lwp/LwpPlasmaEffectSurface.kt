package fr.geoking.gaston.ui.anim.genart.lwp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported generative art shader from cipold/lwp-shaders: Plasma field.
 * Computes multi-harmonic sine wave plasma turbulence, dynamic phase shifts,
 * and smooth color palette transitions.
 */
object LwpPlasmaEffectSurface {
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
        val speed = if (isActive) 1.5f else 0.8f
        val t = elapsedSec * speed
        val brightness = if (isActive) 1.2f else 1.0f

        // Draw deep vignette background
        paint.shader = RadialGradient(
            centerX, centerY, maxOf(w, h) * 0.85f,
            intArrayOf(0xFF1E1B4B.toInt(), 0xFF0F172A.toInt(), 0xFF020617.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val gridSize = 40
        val cellW = w / gridSize
        val cellH = h / gridSize

        val paletteColors = if (palette.colors.isNotEmpty()) palette.colors else listOf(0xFF6366F1.toInt(), 0xFF8B5CF6.toInt())

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val u = ix.toFloat() / gridSize - 0.5f
                val v = iy.toFloat() / gridSize - 0.5f

                // Harmonic sum of sinusoids (lwp-shaders plasma GLSL algorithm)
                val v1 = sin(u * 10f + t)
                val v2 = sin(10f * (u * sin(t / 2f) + v * cos(t / 3f)) + t)
                val cx = u + 0.5f * sin(t / 5f)
                val cy = v + 0.5f * cos(t / 3f)
                val v3 = sin(sqrt(cx * cx + cy * cy + 1e-5f) * 12f + t)

                val plasmaVal = (v1 + v2 + v3) / 3f // range roughly -1..1
                val normVal = (plasmaVal + 1f) / 2f // range 0..1

                val colorIdx = ((normVal + t * 0.1f) % 1f) * paletteColors.size
                val i0 = (colorIdx.toInt() % paletteColors.size).coerceIn(0, paletteColors.size - 1)
                val i1 = ((colorIdx.toInt() + 1) % paletteColors.size).coerceIn(0, paletteColors.size - 1)
                val frac = colorIdx - colorIdx.toInt()

                val c0 = paletteColors[i0]
                val c1 = paletteColors[i1]

                val r = (Color.red(c0) / 255f * (1f - frac) + Color.red(c1) / 255f * frac) * brightness
                val g = (Color.green(c0) / 255f * (1f - frac) + Color.green(c1) / 255f * frac) * brightness
                val b = (Color.blue(c0) / 255f * (1f - frac) + Color.blue(c1) / 255f * frac) * brightness

                val alpha = if (isActive) (0.7f + 0.3f * pulse) else 0.6f

                paint.color = Color.argb(
                    (alpha * 255).toInt().coerceIn(0, 255),
                    (r.coerceIn(0f, 1f) * 255).toInt(),
                    (g.coerceIn(0f, 1f) * 255).toInt(),
                    (b.coerceIn(0f, 1f) * 255).toInt()
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
