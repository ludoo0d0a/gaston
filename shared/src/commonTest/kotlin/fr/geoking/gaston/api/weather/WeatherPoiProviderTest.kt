package fr.geoking.gaston.api.weather

import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiSearchRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherPoiProviderTest {

    private fun mockGridClient(): OpenMeteoWeatherGridClient {
        val engine = MockEngine { request ->
            val lats = request.url.parameters["latitude"]?.split(",") ?: emptyList()
            val lons = request.url.parameters["longitude"]?.split(",") ?: emptyList()
            val body = lats.zip(lons).joinToString(separator = ",", prefix = "[", postfix = "]") { (lat, lon) ->
                """{"latitude":$lat,"longitude":$lon,"current":{"weather_code":3}}"""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return OpenMeteoWeatherGridClient(HttpClient(engine))
    }

    private fun weatherRequest(
        latitude: Double = 48.9,
        longitude: Double = 2.3,
        categories: Set<PoiCategory> = setOf(PoiCategory.Weather),
    ): PoiSearchRequest = PoiSearchRequest(
        latitude = latitude,
        longitude = longitude,
        viewport = MapViewport(mapWidthPx = 100, mapHeightPx = 100, zoom = 6f),
        categories = categories,
    )

    @Test
    fun searchReturnsEmptyWhenWeatherNotRequested() = runBlocking {
        val provider = WeatherPoiProvider(client = mockGridClient(), gridSize = 2, maxPoiCount = 10)
        val result = provider.search(weatherRequest(categories = setOf(PoiCategory.Gas)))
        assertEquals(emptyList(), result)
    }

    @Test
    fun searchReturnsWeatherPoisWhenRequested() = runBlocking {
        val provider = WeatherPoiProvider(client = mockGridClient(), gridSize = 2, maxPoiCount = 10)
        val result = provider.search(weatherRequest())
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.poiCategory == PoiCategory.Weather })
    }

    @Test
    fun searchRespectsMaxPoiCount() = runBlocking {
        val provider = WeatherPoiProvider(client = mockGridClient(), gridSize = 2, maxPoiCount = 3)
        val result = provider.search(weatherRequest())
        assertTrue(result.size <= 3)
    }
}
