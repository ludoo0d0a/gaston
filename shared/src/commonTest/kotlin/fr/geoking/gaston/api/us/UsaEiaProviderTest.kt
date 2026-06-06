package fr.geoking.gaston.api.us

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassElement
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UsaEiaProviderTest {

    @Test
    fun getGasStations_perStationMetroAndStatePrices() = runBlocking {
        val eiaEngine = MockEngine { request ->
            val url = request.url.toString()
            val value = when {
                "YNYC" in url -> "4.50"
                "SNJ" in url -> "3.90"
                else -> "0.00"
            }
            val body = """
                {
                  "response": {
                    "data": [
                      {
                        "period": "2026-05-18",
                        "product": "EPM0",
                        "value": "$value"
                      }
                    ]
                  }
                }
            """.trimIndent()
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val eiaClient = EiaPetroleumClient(HttpClient(eiaEngine))

        val overpass = object : OverpassClient(HttpClient(MockEngine { error("unused") })) {
            override suspend fun queryNodesAndWaysWithTagFilters(
                latitude: Double,
                longitude: Double,
                radiusKm: Int,
                tagFilters: List<Pair<String, Set<String>>>,
                limit: Int,
                minLat: Double?,
                maxLat: Double?,
                minLng: Double?,
                maxLng: Double?
            ): List<OverpassElement> = listOf(
                OverpassElement(1, 40.7580, -73.9855, mapOf("amenity" to "fuel", "name" to "NYC Pump")),
                OverpassElement(2, 40.2204, -74.4115, mapOf("amenity" to "fuel", "name" to "NJ Pump")),
            )
        }

        val provider = UsaEiaProvider(
            eiaClient = eiaClient,
            overpassClient = overpass,
            apiKey = "test-key",
        )

        val pois = provider.getGasStations(40.5, -74.0)

        assertEquals(2, pois.size)
        val nyc = pois.first { it.name == "NYC Pump" }
        val nj = pois.first { it.name == "NJ Pump" }

        assertEquals(4.50, nyc.fuelPrices?.first()?.price)
        assertTrue(nyc.source.orEmpty().contains("NYC metro"))
        assertEquals(3.90, nj.fuelPrices?.first()?.price)
        assertTrue(nj.source.orEmpty().contains("NJ state"))
    }

    @Test
    fun getGasStations_blankApiKey_returnsOsmWithoutPrices() = runBlocking {
        val overpass = object : OverpassClient(HttpClient(MockEngine { error("unused") })) {
            override suspend fun queryNodesAndWaysWithTagFilters(
                latitude: Double,
                longitude: Double,
                radiusKm: Int,
                tagFilters: List<Pair<String, Set<String>>>,
                limit: Int,
                minLat: Double?,
                maxLat: Double?,
                minLng: Double?,
                maxLng: Double?
            ): List<OverpassElement> = listOf(
                OverpassElement(1, 42.3601, -71.0589, mapOf("amenity" to "fuel")),
            )
        }
        val provider = UsaEiaProvider(
            eiaClient = EiaPetroleumClient(HttpClient(MockEngine { error("unused") })),
            overpassClient = overpass,
            apiKey = "",
        )

        val pois = provider.getGasStations(42.36, -71.06)
        assertEquals(1, pois.size)
        assertEquals(null, pois[0].fuelPrices)
        assertEquals("OpenStreetMap", pois[0].source)
    }

    @Test
    fun getGasStations_eiaFailure_stillReturnsOsmStations() = runBlocking {
        val eiaEngine = MockEngine {
            respond("error", HttpStatusCode.InternalServerError)
        }
        val overpass = object : OverpassClient(HttpClient(MockEngine { error("unused") })) {
            override suspend fun queryNodesAndWaysWithTagFilters(
                latitude: Double,
                longitude: Double,
                radiusKm: Int,
                tagFilters: List<Pair<String, Set<String>>>,
                limit: Int,
                minLat: Double?,
                maxLat: Double?,
                minLng: Double?,
                maxLng: Double?
            ): List<OverpassElement> = listOf(
                OverpassElement(1, 42.58, -72.85, mapOf("amenity" to "fuel", "name" to "Rural")),
            )
        }
        val provider = UsaEiaProvider(
            eiaClient = EiaPetroleumClient(HttpClient(eiaEngine)),
            overpassClient = overpass,
            apiKey = "test-key",
        )

        val pois = provider.getGasStations(42.58, -72.85)
        assertEquals(1, pois.size)
        assertNotNull(pois[0].name)
        assertEquals(null, pois[0].fuelPrices)
        assertEquals("OpenStreetMap", pois[0].source)
    }
}
