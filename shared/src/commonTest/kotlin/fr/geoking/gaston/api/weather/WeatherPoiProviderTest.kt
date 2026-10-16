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

    private fun mockGridClient(onQuery: ((String) -> Unit)? = null): OpenMeteoWeatherGridClient {
        val engine = MockEngine { request ->
            onQuery?.invoke(request.url.toString())
            val bbox = request.url.parameters["bounding_box"]?.split(",") ?: emptyList()
            val body = if (bbox.size == 4) {
                val latMin = bbox[0].toDouble()
                val lonMin = bbox[1].toDouble()
                val latMax = bbox[2].toDouble()
                val lonMax = bbox[3].toDouble()
                val cells = listOf(
                    (latMin + latMax) / 2 to (lonMin + lonMax) / 2,
                    latMin to lonMin,
                    latMax to lonMax,
                    latMin to lonMax,
                )
                cells.joinToString(separator = ",", prefix = "[", postfix = "]") { (lat, lon) ->
                    """{"latitude":$lat,"longitude":$lon,"current":{"precipitation":1.2}}"""
                }
            } else {
                "[]"
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
        val provider = WeatherPoiProvider(client = mockGridClient(), maxPoiCount = 10)
        val result = provider.search(weatherRequest(categories = setOf(PoiCategory.Gas)))
        assertEquals(emptyList(), result)
    }

    @Test
    fun searchReturnsWeatherPoisWhenRequested() = runBlocking {
        val provider = WeatherPoiProvider(client = mockGridClient(), maxPoiCount = 10)
        val result = provider.search(weatherRequest())
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.poiCategory == PoiCategory.Weather })
    }

    @Test
    fun searchRespectsMaxPoiCount() = runBlocking {
        val provider = WeatherPoiProvider(client = mockGridClient(), maxPoiCount = 3)
        val result = provider.search(weatherRequest())
        assertTrue(result.size <= 3)
    }

    @Test
    fun searchUsesBoundingBoxAndPrecipitationOnly() = runBlocking {
        var queriedUrl = ""
        val provider = WeatherPoiProvider(
            client = mockGridClient { queriedUrl = it },
            maxPoiCount = 20,
        )
        val result = provider.search(weatherRequest())

        assertTrue(queriedUrl.contains("bounding_box="), queriedUrl)
        assertTrue(queriedUrl.contains("forecast_days=1"))
        assertTrue(queriedUrl.contains("timezone=GMT"))
        assertTrue(queriedUrl.contains("models="))
        assertTrue(
            queriedUrl.contains("current=precipitation"),
            queriedUrl,
        )
        assertTrue(!queriedUrl.contains("weather_code"), queriedUrl)

        assertTrue(result.isNotEmpty())
        val firstPoi = result.first()
        assertEquals("1.2", firstPoi.rawSourceData?.get(WeatherPoiRawKeys.PRECIPITATION))
        assertEquals("1.2 mm", firstPoi.name)
    }

    @Test
    fun searchFiltersOutDryCells() = runBlocking {
        val engine = MockEngine { _ ->
            val body = """[
                {"latitude":48.8,"longitude":2.3,"current":{"precipitation":0.0}},
                {"latitude":48.9,"longitude":2.4,"current":{"precipitation":0.0}},
                {"latitude":49.0,"longitude":2.5,"current":{"precipitation":1.2}},
                {"latitude":49.1,"longitude":2.6,"current":{"precipitation":3.5}}
            ]"""
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val provider = WeatherPoiProvider(client = OpenMeteoWeatherGridClient(HttpClient(engine)), maxPoiCount = 10)
        val result = provider.search(weatherRequest())

        assertEquals(2, result.size)
        assertTrue(result.all { poi ->
            val mm = poi.rawSourceData?.get(WeatherPoiRawKeys.PRECIPITATION)?.toDoubleOrNull()
            mm != null && mm > 0.0
        })
    }
}
