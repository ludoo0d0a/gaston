package fr.geoking.gaston.api.weather

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenMeteoWeatherGridClientTest {

    @Test
    fun parseBoundingBoxArrayResponse() {
        val body = """
            [
              {"latitude":48.85,"longitude":2.35,"current":{"precipitation":0.0}},
              {"latitude":48.90,"longitude":2.40,"current":{"precipitation":1.5}}
            ]
        """.trimIndent()
        val samples = OpenMeteoWeatherGridClient.parseBoundingBoxWeatherResponse(body)
        assertEquals(2, samples.size)
        assertEquals(0.0, samples[0].precipitation)
        assertEquals(1.5, samples[1].precipitation)
        assertEquals(48.85, samples[0].latitude)
        assertEquals(2.40, samples[1].longitude)
    }

    @Test
    fun parseSingleLocationObject() {
        val body = """{"latitude":48.85,"longitude":2.35,"current":{"precipitation":0.2}}"""
        val samples = OpenMeteoWeatherGridClient.parseBoundingBoxWeatherResponse(body)
        assertEquals(1, samples.size)
        assertEquals(0.2, samples[0].precipitation)
    }

    @Test
    fun parsePrecipitationData() {
        val body = """
            [
              {
                "latitude": 48.85,
                "longitude": 2.35,
                "current": {
                  "precipitation": 2.4
                }
              }
            ]
        """.trimIndent()
        val samples = OpenMeteoWeatherGridClient.parseBoundingBoxWeatherResponse(body)
        assertEquals(1, samples.size)
        assertEquals(2.4, samples[0].precipitation)
    }

    @Test
    fun parseErrorObjectReturnsEmpty() {
        val body = """{"error":true,"reason":"Bounding box calls not supported for domain best_match"}"""
        val samples = OpenMeteoWeatherGridClient.parseBoundingBoxWeatherResponse(body)
        assertEquals(emptyList(), samples)
    }

    @Test
    fun maxPointsIsLimitedTo20() {
        assertEquals(20, OpenMeteoWeatherGridClient.MAX_POINTS)
    }
}
