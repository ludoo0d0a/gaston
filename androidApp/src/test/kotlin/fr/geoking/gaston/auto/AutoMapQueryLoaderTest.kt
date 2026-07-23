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
            visibleArea = Rect(40, 20, 360, 280),
            surfaceWidth = 400,
            surfaceHeight = 300,
            nowMs = 450L,
        )
        // Spinner sits in the top-right of the visible area — a non-black pixel should appear there.
        val sampleX = 360 - 14 - 14 // right - margin - half size
        val sampleY = 20 + 14 + 14
        val pixel = bitmap.getPixel(sampleX, sampleY)
        assertTrue(pixel != Color.BLACK)
    }
}
