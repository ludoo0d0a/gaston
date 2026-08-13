package fr.geoking.gaston.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoMapQueryLoaderTest {

    @Test
    fun draw_doesNotThrow_withVisibleArea() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        AutoMapQueryLoader.draw(
            canvas = canvas,
            density = 1f,
            visibleArea = Rect(40, 20, 360, 280),
            surfaceWidth = 400,
            surfaceHeight = 300,
            nowMs = 450L,
        )
        // Loader sits to the left of the compass — with density=1f, cx = 360 - 16 - 48 - 12 - 14 = 270, cy = 20 + 16 + 24 = 60
        val sampleX = 270
        val sampleY = 60
        val pixel = bitmap.getPixel(sampleX, sampleY)
        assertTrue(pixel != Color.BLACK)
    }
}
