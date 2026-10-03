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
        // Menu on left: loader at former compass slot above zoom buttons — density=1f, edgeMargin=4
        // cx = 360 - 4 - 28 = 328, cy = 280 - 16 - 28 - 2*(56+8) = 108
        val sampleX = 328
        val sampleY = 108
        val pixel = bitmap.getPixel(sampleX, sampleY)
        assertTrue(pixel != Color.BLACK)
    }

    @Test
    fun draw_positionsCorrectly_whenMenuIsOnRight() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        AutoMapQueryLoader.draw(
            canvas = canvas,
            density = 1f,
            visibleArea = Rect(0, 20, 260, 280),
            surfaceWidth = 400,
            surfaceHeight = 300,
            nowMs = 450L,
        )
        // Menu on right: loader at bottom-left slot: cx = 0 + 4 + 28 = 32, cy = 108
        val sampleX = 32
        val sampleY = 108
        val pixel = bitmap.getPixel(sampleX, sampleY)
        assertTrue(pixel != Color.BLACK)
    }
}
