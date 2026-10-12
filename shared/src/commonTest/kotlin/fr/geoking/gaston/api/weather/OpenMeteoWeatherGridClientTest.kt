package fr.geoking.gaston.api.weather

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenMeteoWeatherGridClientTest {

    @Test
    fun parseMultiLocationArrayResponse() {
        val body = """
            [
              {"latitude":48.85,"longitude":2.35,"current":{"weather_code":3}},
              {"latitude":48.90,"longitude":2.40,"current":{"weather_code":61}}
            ]
        """.trimIndent()
        val requested = listOf(48.85 to 2.35, 48.90 to 2.40)
        val samples = OpenMeteoWeatherGridClient.parseMultiPointWeatherResponse(body, requested)
        assertEquals(2, samples.size)
        assertEquals(3, samples[0].weatherCode)
        assertEquals(61, samples[1].weatherCode)
        assertEquals(48.85, samples[0].latitude)
        assertEquals(2.40, samples[1].longitude)
    }

    @Test
    fun parseSingleLocationObject() {
        val body = """{"latitude":48.85,"longitude":2.35,"current":{"weather_code":45}}"""
        val samples = OpenMeteoWeatherGridClient.parseMultiPointWeatherResponse(
            body,
            listOf(48.85 to 2.35),
        )
        assertEquals(1, samples.size)
        assertEquals(45, samples[0].weatherCode)
    }

    @Test
    fun parsePrecipitationData() {
        val body = """
            [
              {
                "latitude": 48.85,
                "longitude": 2.35,
                "current": {
                  "weather_code": 61,
                  "precipitation_probability": 85.0,
                  "precipitation": 2.4,
                  "rain": 1.5,
                  "showers": 0.9,
                  "snowfall": 0.0
                }
              }
            ]
        """.trimIndent()
        val requested = listOf(48.85 to 2.35)
        val samples = OpenMeteoWeatherGridClient.parseMultiPointWeatherResponse(body, requested)
        assertEquals(1, samples.size)
        assertEquals(61, samples[0].weatherCode)
        assertEquals(85.0, samples[0].precipitationProbability)
        assertEquals(2.4, samples[0].precipitation)
        assertEquals(1.5, samples[0].rain)
        assertEquals(0.9, samples[0].showers)
        assertEquals(0.0, samples[0].snow)
    }

    @Test
    fun maxPointsIsLimitedTo20() {
        assertEquals(20, OpenMeteoWeatherGridClient.MAX_POINTS)
    }
}
