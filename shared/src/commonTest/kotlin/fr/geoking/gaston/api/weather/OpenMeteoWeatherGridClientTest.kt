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
}
