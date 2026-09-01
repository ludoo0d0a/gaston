package fr.geoking.gaston.api.overpass

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

class OverpassClientTest {

    @Test
    fun queryNodes_sendsIdentifyingUserAgent_andWildcardAccept() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals(OVERPASS_USER_AGENT, request.headers[HttpHeaders.UserAgent])
            assertEquals("*/*", request.headers[HttpHeaders.Accept])
            respond(
                content = NODE_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = OverpassClient(HttpClient(engine), baseUrl = "https://overpass-api.de/api/interpreter")
        val elements = client.queryNodes(
            latitude = 48.85,
            longitude = 2.35,
            radiusKm = 1,
            amenityValues = setOf("fuel"),
            limit = 1
        )
        assertEquals(1, elements.size)
        assertEquals(1L, elements.first().id)
        assertEquals("Test Pump", elements.first().name())
    }

    @Test
    fun overpassElement_streetAndAddressExtraction_formatsDetailedAddress() {
        val element = OverpassElement(
            id = 100L,
            lat = 48.85,
            lon = 2.35,
            tags = mapOf(
                "addr:housenumber" to "12",
                "addr:street" to "Rue de Rivoli",
                "addr:postcode" to "75001",
                "addr:city" to "Paris"
            )
        )
        assertEquals("Rue de Rivoli", element.street())
        assertEquals("12 Rue de Rivoli, 75001 Paris", element.address())
    }

    companion object {
        private val NODE_RESPONSE = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 48.85,
                  "lon": 2.35,
                  "tags": { "amenity": "fuel", "name": "Test Pump" }
                }
              ]
            }
        """.trimIndent()
    }
}
