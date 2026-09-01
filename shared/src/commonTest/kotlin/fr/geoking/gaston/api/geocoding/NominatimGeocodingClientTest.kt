package fr.geoking.gaston.api.geocoding

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
import kotlin.test.assertTrue

class NominatimGeocodingClientTest {

    @Test
    fun geocode_formatsDetailedAddressAndParsesCoordinates() = runBlocking {
        val engine = MockEngine { request ->
            assertTrue(request.url.toString().contains("addressdetails=1"))
            assertTrue(request.url.toString().contains("q=10%20High%20Street"))
            respond(
                content = """
                    [
                      {
                        "lat": "51.5074",
                        "lon": "-0.1278",
                        "display_name": "10, High Street, Westminster, London, UK",
                        "address": {
                          "house_number": "10",
                          "road": "High Street",
                          "suburb": "Westminster",
                          "city": "London"
                        }
                      }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = NominatimGeocodingClient(HttpClient(engine))
        val results = client.geocode("10 High Street", limit = 1)

        assertEquals(1, results.size)
        val place = results.first()
        assertEquals(51.5074, place.latitude, 0.0001)
        assertEquals(-0.1278, place.longitude, 0.0001)
        assertEquals("10 High Street, Westminster, London", place.label)
    }

    @Test
    fun geocode_emptyQuery_returnsEmptyList() = runBlocking {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val client = NominatimGeocodingClient(HttpClient(engine))
        val results = client.geocode("   ", limit = 1)
        assertTrue(results.isEmpty())
    }
}
