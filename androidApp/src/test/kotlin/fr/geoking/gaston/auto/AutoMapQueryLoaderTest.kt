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
    fun draw_onScreenRight_whenMenuIsOnLeft() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        AutoMapQueryLoader.draw(
            canvas = canvas,
            density = 1f,
            // Map on right; visibleArea.right inset from screen (host chrome).
            visibleArea = Rect(40, 20, 360, 280),
            surfaceWidth = 400,
            surfaceHeight = 300,
            nowMs = 450L,
        )
        // Screen-right zoom column: cx = 400 - 4 - 28 = 368, cy = 108
        val sampleX = 368
        val sampleY = 108
        val pixel = bitmap.getPixel(sampleX, sampleY)
        assertTrue(pixel != Color.BLACK)
    }

    @Test
    fun draw_onScreenLeft_whenMenuIsOnRight() {
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
        // Screen-left zoom column: cx = 4 + 28 = 32, cy = 108
        val sampleX = 32
        val sampleY = 108
        val pixel = bitmap.getPixel(sampleX, sampleY)
        assertTrue(pixel != Color.BLACK)
    }
}
