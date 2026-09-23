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
        // Loader sits to the left of compass above zoom buttons — density=1f, cx = 360 - 16 - 28 - 28 - 12 - 14 = 262, cy = 280 - 16 - 28 - 2*(56+8) = 108
        val sampleX = 262
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
        // Menu on right: compass at bottom-left, loader to its right: cx = 0 + 16 + 28 + 28 + 12 + 14 = 98, cy = 108
        val sampleX = 98
        val sampleY = 108
        val pixel = bitmap.getPixel(sampleX, sampleY)
        assertTrue(pixel != Color.BLACK)
    }
}
