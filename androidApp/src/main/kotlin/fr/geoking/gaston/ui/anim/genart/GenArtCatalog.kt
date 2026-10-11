package fr.geoking.gaston.ui.anim.genart

import android.graphics.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import fr.geoking.gaston.ui.anim.AnimationPalette
import fr.geoking.gaston.ui.anim.AnimationPalettes
import fr.geoking.gaston.ui.anim.genart.lwp.LwpPlasmaEffectSurface
import fr.geoking.gaston.ui.anim.genart.lwp.LwpStarfieldEffectSurface
import fr.geoking.gaston.ui.anim.genart.lwp.LwpVoronoiEffectSurface
import fr.geoking.gaston.ui.anim.genart.ppsspp.PpssppBloomVignetteEffectSurface
import fr.geoking.gaston.ui.anim.genart.ppsspp.PpssppCelShaderEffectSurface
import fr.geoking.gaston.ui.anim.genart.ppsspp.PpssppCrtScanlinesEffectSurface
import fr.geoking.gaston.ui.anim.genart.shaderlay.ShaderlayNeonGridEffectSurface
import fr.geoking.gaston.ui.anim.genart.shaderlay.ShaderlayVortexTunnelEffectSurface

/**
 * Representation of a ported generative art shader effect.
 */
data class GenArtEffect(
    val id: String,
    val title: String,
    val sourceRepo: String,
    val draw: (
        canvas: Canvas,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        isActive: Boolean,
        elapsedSec: Float,
        pulse: Float,
        palette: AnimationPalette
    ) -> Unit
)

/**
 * Unified registry of generative art shader ports from cipold/lwp-shaders, mattakins/Shaderlay, and jdgleaver/ppsspp_shaders.
 */
object GenArtCatalog {
    private val starfieldInstance by lazy { LwpStarfieldEffectSurface() }

    val allEffects: List<GenArtEffect> = listOf(
        GenArtEffect(
            id = "lwp_plasma",
            title = "LWP Harmonic Plasma",
            sourceRepo = "cipold/lwp-shaders",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                LwpPlasmaEffectSurface.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        ),
        GenArtEffect(
            id = "lwp_starfield",
            title = "LWP 3D Warp Starfield",
            sourceRepo = "cipold/lwp-shaders",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                starfieldInstance.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        ),
        GenArtEffect(
            id = "lwp_voronoi",
            title = "LWP Voronoi Cellular Caustics",
            sourceRepo = "cipold/lwp-shaders",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                LwpVoronoiEffectSurface.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        ),
        GenArtEffect(
            id = "shaderlay_neon_grid",
            title = "Shaderlay Synthwave Neon Grid",
            sourceRepo = "mattakins/Shaderlay",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                ShaderlayNeonGridEffectSurface.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        ),
        GenArtEffect(
            id = "shaderlay_vortex_tunnel",
            title = "Shaderlay Vortex Warp Tunnel",
            sourceRepo = "mattakins/Shaderlay",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                ShaderlayVortexTunnelEffectSurface.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        ),
        GenArtEffect(
            id = "ppsspp_crt_scanlines",
            title = "PPSSPP CRT Scanlines & Tube",
            sourceRepo = "jdgleaver/ppsspp_shaders",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                PpssppCrtScanlinesEffectSurface.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        ),
        GenArtEffect(
            id = "ppsspp_cel_shader",
            title = "PPSSPP Cel-Shader & Edge Detection",
            sourceRepo = "jdgleaver/ppsspp_shaders",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                PpssppCelShaderEffectSurface.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        ),
        GenArtEffect(
            id = "ppsspp_bloom_vignette",
            title = "PPSSPP Bloom Radiance & Film Grain",
            sourceRepo = "jdgleaver/ppsspp_shaders",
            draw = { canvas, w, h, cx, cy, active, sec, pulse, pal ->
                PpssppBloomVignetteEffectSurface.draw(canvas, w, h, cx, cy, active, sec, pulse, pal)
            }
        )
    )

    fun findById(id: String): GenArtEffect? = allEffects.firstOrNull { it.id == id }
}

/**
 * Reusable Jetpack Compose surface that renders a generative art shader effect.
 */
@Composable
fun GenArtSurface(
    effect: GenArtEffect,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    palette: AnimationPalette = AnimationPalettes.currentPalette()
) {
    val infiniteTransition = rememberInfiniteTransition(label = "genArtTicker")

    val elapsedSec by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "elapsedSec"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val width = size.width.toInt()
            val height = size.height.toInt()
            if (width > 0 && height > 0) {
                effect.draw(
                    nativeCanvas,
                    width,
                    height,
                    width / 2f,
                    height / 2f,
                    isActive,
                    elapsedSec,
                    pulse,
                    palette
                )
            }
        }
    }
}
