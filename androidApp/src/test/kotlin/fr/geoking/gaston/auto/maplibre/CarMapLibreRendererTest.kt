package fr.geoking.gaston.auto.maplibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive

@RunWith(RobolectricTestRunner::class)
class CarMapLibreRendererTest {

    @Test
    fun testScaleNumber() {
        val originalValue: Any = 10f
        val scaled = CarMapLibreRenderer.scaleExpressionArray(originalValue, 1.4f)
        assertEquals(14.0f, scaled as Float, 0.001f)
    }

    @Test
    fun testScaleInterpolateExpression() {
        // [ "interpolate", [ "linear" ], [ "zoom" ], 5.0, 10.0, 10.0, 15.0 ]
        val original: List<Any> = listOf(
            "interpolate",
            listOf("linear"),
            listOf("zoom"),
            5.0,
            10.0,
            10.0,
            15.0
        )
        val scaled = CarMapLibreRenderer.scaleExpressionArray(original, 1.4f) as List<*>
        assertEquals("interpolate", scaled[0])
        assertEquals(listOf("linear"), scaled[1])
        assertEquals(listOf("zoom"), scaled[2])
        assertEquals(5.0, scaled[3])
        assertEquals(14.0f, scaled[4] as Float, 0.001f)
        assertEquals(10.0, scaled[5])
        assertEquals(21.0f, scaled[6] as Float, 0.001f)
    }

    @Test
    fun testScaleStepExpression() {
        // [ "step", [ "zoom" ], 12.0, 8.0, 14.0, 12.0, 16.0 ]
        val original: List<Any> = listOf(
            "step",
            listOf("zoom"),
            12.0,
            8.0,
            14.0,
            12.0,
            16.0
        )
        val scaled = CarMapLibreRenderer.scaleExpressionArray(original, 1.4f) as List<*>
        assertEquals("step", scaled[0])
        assertEquals(listOf("zoom"), scaled[1])
        assertEquals(16.8f, scaled[2] as Float, 0.001f) // default output
        assertEquals(8.0, scaled[3])
        assertEquals(19.6f, scaled[4] as Float, 0.001f) // stop 1 output
        assertEquals(12.0, scaled[5])
        assertEquals(22.4f, scaled[6] as Float, 0.001f) // stop 2 output
    }

    @Test
    fun testToJsonElement() {
        val input: List<Any> = listOf("step", 12.0)
        val json = CarMapLibreRenderer.toJsonElement(input)
        assertTrue(json is JsonArray)
        val jsonArray = json as JsonArray
        assertEquals(2, jsonArray.size())
        assertEquals("step", jsonArray[0].asString)
        assertEquals(12.0, jsonArray[1].asDouble, 0.001)
    }
}
