package fr.geoking.gaston.ui.anim.genart.ppsspp

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
 * Ported generative art shader from jdgleaver/ppsspp_shaders: Bloom Radiance & Cinematic Vignette.
 * Simulates high-pass glow extraction, expanded bloom halos, radial lens darkening, and film grain.
 */
object PpssppBloomVignetteEffectSurface {
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
        val time = elapsedSec

        val primaryColor = palette.primary
        val secondaryColor = palette.secondary
        val tertiaryColor = palette.tertiary

        // Deep cinematic background
        paint.color = 0xFF0B0F19.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)

        // Light sources producing bloom highlights
        val lightCount = 4
        for (i in 0 until lightCount) {
            val speed = 0.5f + i * 0.2f
            val lx = centerX + cos(time * speed + i * 1.5f) * w * 0.25f
            val ly = centerY + sin(time * speed * 0.8f + i * 2f) * h * 0.2f
            val baseR = h * 0.12f

            val c = when (i % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }

            // 1. Expanded Bloom Halo (Multi-pass blur simulation)
            val bloomR = baseR * (if (isActive) (2.5f + pulse * 0.5f) else 1.8f)
            paint.shader = RadialGradient(
                lx, ly, bloomR,
                intArrayOf(
                    Color.argb(if (isActive) 180 else 110, Color.red(c), Color.green(c), Color.blue(c)),
                    Color.argb(80, Color.red(c), Color.green(c), Color.blue(c)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(lx, ly, bloomR, paint)
            paint.shader = null

            // 2. High-pass core highlight
            paint.color = Color.WHITE
            paint.alpha = if (isActive) 220 else 160
            canvas.drawCircle(lx, ly, baseR * 0.3f, paint)
            paint.alpha = 255
        }

        // 3. Cinematic Lens Radial Vignette
        paint.shader = RadialGradient(
            centerX, centerY, maxOf(w, h) * 0.75f,
            intArrayOf(Color.TRANSPARENT, Color.argb(140, 0, 0, 0), Color.argb(230, 2, 6, 23)),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 4. Subtle Film Grain Noise
        val grainCount = 150
        paint.color = Color.argb(25, 255, 255, 255)
        val rng = Random(time.hashCode())
        for (i in 0 until grainCount) {
            val gx = rng.nextFloat() * w
            val gy = rng.nextFloat() * h
            canvas.drawRect(gx, gy, gx + 2f, gy + 2f, paint)
        }
    }
}
