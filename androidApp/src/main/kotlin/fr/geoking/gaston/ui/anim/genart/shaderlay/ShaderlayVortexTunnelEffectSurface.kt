package fr.geoking.gaston.ui.anim.genart.shaderlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported generative art shader from mattakins/Shaderlay: Polar Vortex Warp Tunnel.
 * Transforms UV into polar coordinates (radius, angle), executing logarithmic spiral zoom,
 * concentric ring frequency bursts, and rotation.
 */
object ShaderlayVortexTunnelEffectSurface {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

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
        val time = elapsedSec * (if (isActive) 1.4f else 0.8f)

        val primaryColor = palette.primary
        val secondaryColor = palette.secondary
        val tertiaryColor = palette.tertiary

        // Dark center background
        paint.shader = RadialGradient(
            centerX, centerY, maxOf(w, h) * 0.85f,
            intArrayOf(0xFF18181B.toInt(), 0xFF09090B.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val gridSize = 36
        val cellW = w / gridSize
        val cellH = h / gridSize

        val paletteColors = if (palette.colors.isNotEmpty()) palette.colors else listOf(0xFF8B5CF6.toInt(), 0xFFEC4899.toInt())

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val u = (ix + 0.5f) / gridSize - 0.5f
                val v = (iy + 0.5f) / gridSize - 0.5f

                // Polar coordinate conversion
                val r = sqrt(u * u + v * v + 1e-5f)
                val theta = atan2(v, u)

                // Logarithmic spiral depth and angular rotation
                val logR = ln(r + 0.001f)
                val tunnelZ = 1f / (r + 0.05f) - time * 2f
                val spiralAngle = theta + logR * 1.5f + time * 0.5f

                // Combined wave pattern in tunnel space
                val ringVal = sin(tunnelZ * 4f) * cos(spiralAngle * 6f)
                val intensity = ((ringVal + 1f) / 2f).coerceIn(0f, 1f)

                val colorIdx = ((tunnelZ * 0.1f + theta / (2f * PI.toFloat())) % 1f + 1f) % 1f * paletteColors.size
                val i0 = (colorIdx.toInt() % paletteColors.size).coerceIn(0, paletteColors.size - 1)
                val i1 = ((colorIdx.toInt() + 1) % paletteColors.size).coerceIn(0, paletteColors.size - 1)
                val frac = colorIdx - colorIdx.toInt()

                val c0 = paletteColors[i0]
                val c1 = paletteColors[i1]

                val br = if (isActive) (0.8f + 0.4f * pulse) else 0.7f
                val alpha = (intensity * br * (r * 3f).coerceIn(0f, 1f)).coerceIn(0f, 1f)

                val redVal = (Color.red(c0) / 255f * (1f - frac) + Color.red(c1) / 255f * frac) * intensity
                val greenVal = (Color.green(c0) / 255f * (1f - frac) + Color.green(c1) / 255f * frac) * intensity
                val blueVal = (Color.blue(c0) / 255f * (1f - frac) + Color.blue(c1) / 255f * frac) * intensity

                paint.color = Color.argb(
                    (alpha * 255).toInt().coerceIn(0, 255),
                    (redVal.coerceIn(0f, 1f) * 255).toInt(),
                    (greenVal.coerceIn(0f, 1f) * 255).toInt(),
                    (blueVal.coerceIn(0f, 1f) * 255).toInt()
                )

                canvas.drawRect(
                    ix * cellW, iy * cellH,
                    (ix + 1) * cellW + 1f, (iy + 1) * cellH + 1f,
                    paint
                )
            }
        }

        // Concentric glowing rings over center
        val ringCount = 5
        val maxR = minOf(w, h) * 0.45f
        paint.style = Paint.Style.STROKE
        for (i in 1..ringCount) {
            val ringRadius = (i.toFloat() / ringCount) * maxR + sin(time * 2f + i) * 10f
            val ringAlpha = (1f - i.toFloat() / ringCount) * (if (isActive) 0.5f else 0.3f)
            paint.color = Color.argb(
                (ringAlpha * 255).toInt().coerceIn(0, 255),
                Color.red(primaryColor),
                Color.green(primaryColor),
                Color.blue(primaryColor)
            )
            paint.strokeWidth = if (isActive) 3f else 1.5f
            canvas.drawCircle(centerX, centerY, ringRadius, paint)
        }
        paint.style = Paint.Style.FILL
    }
}
