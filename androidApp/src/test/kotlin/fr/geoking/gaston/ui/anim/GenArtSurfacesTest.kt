package fr.geoking.gaston.ui.anim

import android.graphics.Bitmap
import android.graphics.Canvas
import fr.geoking.gaston.ui.anim.genart.GenArtCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GenArtSurfacesTest {

    @Test
    fun catalogContainsAllPortedShaders() {
        val effects = GenArtCatalog.allEffects
        assertEquals(8, effects.size)

        val expectedIds = listOf(
            "lwp_plasma",
            "lwp_starfield",
            "lwp_voronoi",
            "shaderlay_neon_grid",
            "shaderlay_vortex_tunnel",
            "ppsspp_crt_scanlines",
            "ppsspp_cel_shader",
            "ppsspp_bloom_vignette"
        )

        expectedIds.forEach { id ->
            val effect = GenArtCatalog.findById(id)
            assertNotNull("Effect $id should be registered in GenArtCatalog", effect)
        }
    }

    @Test
    fun allGenerativeArtSurfacesRenderWithoutError() {
        val width = 200
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val palette = AnimationPalettes.paletteFor(0)

        GenArtCatalog.allEffects.forEach { effect ->
            // Test active state
            effect.draw(
                canvas,
                width,
                height,
                width / 2f,
                height / 2f,
                true,
                12.5f,
                0.5f,
                palette
            )

            // Test inactive state
            effect.draw(
                canvas,
                width,
                height,
                width / 2f,
                height / 2f,
                false,
                12.5f,
                0.0f,
                palette
            )
        }
    }
}
