package fr.geoking.gaston.ui.anim.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.sin

/**
 * Waves effect for Android Auto surface.
 */
object WavesEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        isActive: Boolean,
        phaseBase: Float,
        pulse: Float,
        palette: AnimationPalette
    ) {
        val amplitudeMult = if (isActive) 1.5f else 1f

        paint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0xFF0F172A.toInt(), 0xFF1E293B.toInt(), 0xFF0F172A.toInt(), 0xFF020617.toInt()),
            floatArrayOf(0f, 0.33f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val layers = listOf(
            Triple(phaseBase * 0.5f, 40f * amplitudeMult, 0.015f),
            Triple(phaseBase * 0.4f, 50f * amplitudeMult, 0.012f),
            Triple(phaseBase * 0.35f, 45f * amplitudeMult, 0.010f)
        )
        val waveColors = intArrayOf(palette.primary, palette.secondary, palette.tertiary)
        val centerYs = floatArrayOf(centerY * 0.3f, centerY * 0.6f, centerY * 0.9f)
        layers.forEachIndexed { i, (phase, amplitude, freq) ->
            drawWaveLayer(canvas, width, height, phase, amplitude, freq, waveColors[i], centerYs[i], 0.5f)
        }
        if (isActive) {
            drawWaveLayer(
                canvas,
                width,
                height,
                phaseBase * 0.6f,
                35f * amplitudeMult,
                0.018f,
                palette.quaternary,
                centerY * 0.45f,
                0.3f
            )
        }

        // Radial waves
        val maxRadius = width.coerceAtLeast(height) * 0.8f
        val waveCount = if (isActive) 8 else 5
        for (i in 0 until waveCount) {
            val radius = (maxRadius / waveCount) * (i + 1) + sin(phaseBase + i) * 20f
            val alpha = (1f - i / waveCount.toFloat()) * 0.15f
            paint.color = Color.argb(
                (alpha * 255).toInt(),
                Color.red(palette.primary),
                Color.green(palette.primary),
                Color.blue(palette.primary)
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isActive) 3f else 2f
            paint.shader = null
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawWaveLayer(
        canvas: Canvas,
        width: Int,
        height: Int,
        phase: Float,
        amplitude: Float,
        frequency: Float,
        color: Int,
        centerY: Float,
        alpha: Float
    ) {
        path.reset()
        path.moveTo(0f, centerY)
        val segments = width.coerceAtLeast(50)
        val step = width.toFloat() / segments
        for (i in 0..segments) {
            val x = i * step
            val y = centerY + sin(x * frequency + phase) * amplitude
            path.lineTo(x, y)
        }
        path.lineTo(width.toFloat(), height.toFloat())
        path.lineTo(0f, height.toFloat())
        path.close()

        val a = (alpha * 255).toInt().coerceIn(0, 255)
        paint.shader = LinearGradient(
            0f, centerY - amplitude, 0f, height.toFloat(),
            intArrayOf(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
        paint.shader = null

        path.reset()
        path.moveTo(0f, centerY + sin(phase) * amplitude)
        for (i in 1..segments) {
            val x = i * step
            val y = centerY + sin(x * frequency + phase) * amplitude
            path.lineTo(x, y)
        }
        paint.color = Color.argb((alpha + 0.2f).coerceIn(0f, 1f).times(255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }
}
