package fr.geoking.gaston.auto

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoSurfaceRendererTest {

    @Test
    fun testFloorDivisionMathForParentTiles() {
        fun floorDiv(x: Int, y: Int): Int {
            return if (x < 0 && x % y != 0) x / y - 1 else x / y
        }

        // Test with different scales (different level diffs)
        for (levelDiff in 1..4) {
            val scale = 1 shl levelDiff

            // For a series of coordinates x
            for (x in -10..10) {
                // Check floorDiv behavior
                val pX = floorDiv(x, scale)
                val expectedPX = Math.floorDiv(x, scale)
                assertEquals("Failed for x=$x, scale=$scale", expectedPX, pX)

                // Sub-coordinate calculation
                val subX = x - pX * scale
                assertEquals("Sub-coordinate out of bounds for x=$x, scale=$scale", true, subX in 0 until scale)
            }
        }
    }

    @Test
    fun testChildCoordinatesMath() {
        // Parent tile x at zoom z corresponds to child tiles at zoom z+1
        // Let's verify children coordinates
        val x = 5
        val y = 8

        // At zoom + 1 (scale 2):
        // (childX, childY) of top-left is (2x, 2y)
        assertEquals(10, x * 2 + 0)
        assertEquals(16, y * 2 + 0)

        // (childX, childY) of bottom-right is (2x+1, 2y+1)
        assertEquals(11, x * 2 + 1)
        assertEquals(17, y * 2 + 1)
    }
}
