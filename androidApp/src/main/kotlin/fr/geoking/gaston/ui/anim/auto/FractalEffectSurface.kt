package fr.geoking.gaston.ui.anim.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.ln

/**
 * Fractal (Mandelbrot) effect for Android Auto surface.
 * Slow infinite zoom into the set boundary; phase and zoom driven by elapsed time.
 */
object FractalEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private const val ZOOM_CYCLE_MS = 50_000L
    private const val PHASE_CYCLE_MS = 20_000L
    private const val MAX_ITER = 128

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
        val w = width.toFloat()
        val h = height.toFloat()
        // Zoom 1..80 over 50s, then restart
        val zoomProgress = (System.currentTimeMillis() % ZOOM_CYCLE_MS) / ZOOM_CYCLE_MS.toFloat()
        val zoom = 1f + zoomProgress * 79f
        // Phase 0..1 over 20s
        val phase = (System.currentTimeMillis() % PHASE_CYCLE_MS) / PHASE_CYCLE_MS.toFloat()
        val brightness = if (isActive) 1.2f else 1f

        // Dark background
        paint.shader = RadialGradient(
            centerX, centerY, maxOf(w, h) * 0.8f,
            intArrayOf(0xFF1E1B4B.toInt(), 0xFF0F172A.toInt(), 0xFF020617.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val cx = 0.25f
        val cy = 0f
        val gridSize = 48 // lower res for Auto performance
        val cellW = w / gridSize
        val cellH = h / gridSize
        val halfSpan = 2f / zoom

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val sx = (ix + 0.5f) * cellW
                val sy = (iy + 0.5f) * cellH
                val re = cx - halfSpan + (sx / w) * (2f * halfSpan)
                val im = cy - halfSpan + (1f - sy / h) * (2f * halfSpan)
                val (escaped, smooth) = mandelbrotSmooth(re, im, 64)
                paint.color = fractalColor(escaped, smooth, phase, brightness, isActive, palette)
                canvas.drawRect(
                    ix * cellW, iy * cellH,
                    (ix + 1) * cellW + 1f, (iy + 1) * cellH + 1f,
                    paint
                )
            }
        }
    }

    private fun mandelbrotSmooth(cr: Float, ci: Float, maxIter: Int): Pair<Int, Float> {
        var zr = 0.0
        var zi = 0.0
        var n = 0
        while (n < maxIter) {
            val zr2 = zr * zr
            val zi2 = zi * zi
            if (zr2 + zi2 > 4.0) {
                val logZn = ln(zr2 + zi2) / 2.0
                val nu = ln(logZn / ln(2.0)) / ln(2.0)
                val smooth = (n + 1 - nu).toFloat().coerceIn(0f, 1f)
                return n to smooth
            }
            val newZi = 2 * zr * zi + ci
            val newZr = zr2 - zi2 + cr
            zi = newZi
            zr = newZr
            n++
        }
        return maxIter to 0f
    }

    private fun fractalColor(
        iterations: Int,
        smooth: Float,
        phase: Float,
        brightness: Float,
    isActive: Boolean,
    palette: AnimationPalette
    ): Int {
        if (iterations >= MAX_ITER) return 0xFF020617.toInt()
        val t = (iterations + smooth) / MAX_ITER.toFloat()
    val paletteColors = if (palette.colors.isNotEmpty()) palette.colors else listOf(0xFF6366F1.toInt())
    val idx = ((t * 3f + phase) % 1f) * paletteColors.size
    val i0 = (idx.toInt() % paletteColors.size).coerceIn(0, paletteColors.size - 1)
    val i1 = ((idx.toInt() + 1) % paletteColors.size).coerceIn(0, paletteColors.size - 1)
        val frac = idx - idx.toInt()
    val c0 = paletteColors[i0]
    val c1 = paletteColors[i1]
        val r = (Color.red(c0) / 255f * (1 - frac) + Color.red(c1) / 255f * frac).coerceIn(0f, 1f)
        val g = (Color.green(c0) / 255f * (1 - frac) + Color.green(c1) / 255f * frac).coerceIn(0f, 1f)
        val b = (Color.blue(c0) / 255f * (1 - frac) + Color.blue(c1) / 255f * frac).coerceIn(0f, 1f)
        val alpha = ((0.4f + 0.5f * t) * brightness).let { a ->
            if (isActive) minOf(a * 1.1f, 1f) else a
        }
        return Color.argb(
            (alpha * 255).toInt().coerceIn(0, 255),
            (r * brightness * 255).toInt().coerceIn(0, 255),
            (g * brightness * 255).toInt().coerceIn(0, 255),
            (b * brightness * 255).toInt().coerceIn(0, 255)
        )
    }
}
