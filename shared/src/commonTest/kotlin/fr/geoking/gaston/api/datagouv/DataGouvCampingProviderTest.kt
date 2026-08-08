package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiSearchRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataGouvCampingProviderTest {

    private val mockResponse = """
        {
            "results": [
                {
                    "recordid": "aire1",
                    "titre": "Aire de Camping-Car de Montpellier",
                    "adresse": "Avenue du Montpellier",
                    "code_postal": "34000",
                    "commune": "Montpellier",
                    "type_daire": "Aire municipale",
                    "geopoint": {
                        "lat": 43.611,
                        "lon": 3.877
                    }
                }
            ]
        }
    """.trimIndent()

    @Test
    fun testSearch_insideHerault_callsApiAndReturnsResults() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val campingClient = DataGouvCampingClient(httpClient)
        val provider = DataGouvCampingProvider(campingClient, radiusKm = 15, limit = 50)

        // Montpellier coordinates (inside Hérault)
        val request = PoiSearchRequest(
            latitude = 43.611,
            longitude = 3.877,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val results = provider.search(request)

        assertEquals(1, callCount, "Expected 1 call to the API since Montpellier is in Hérault")
        assertEquals(1, results.size)
        assertEquals("dgouv:aire1", results[0].id)
        assertEquals("Aire de Camping-Car de Montpellier", results[0].name)
    }

    @Test
    fun testSearch_outsideHerault_shortCircuitsAndDoesNotCallApi() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val campingClient = DataGouvCampingClient(httpClient)
        val provider = DataGouvCampingProvider(campingClient, radiusKm = 15, limit = 50)

        // Paris coordinates (outside Hérault)
        val request = PoiSearchRequest(
            latitude = 48.8566,
            longitude = 2.3522,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val results = provider.search(request)

        assertEquals(0, callCount, "Expected 0 calls to the API since Paris is outside Hérault")
        assertTrue(results.isEmpty(), "Expected no results returned for Paris")
    }
}
