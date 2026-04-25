package fr.geoking.gaston.api.ecomovement

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EcoMovementOcpiClientTest {

    @Test
    fun listLocations_sendsTokenHeader_andParsesEnvelope() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertTrue(request.url.encodedPath.endsWith("/locations"))
            assertEquals("Token test-key-123", request.headers[HttpHeaders.Authorization])

            respond(
                content = """
                {
                  "data": [
                    {
                      "id": "LOC-1",
                      "name": "Test Location",
                      "address": "1 Main St",
                      "city": "Paris",
                      "country": "FR",
                      "coordinates": { "latitude": "48.8566", "longitude": "2.3522" },
                      "operator": { "name": "Eco Operator" },
                      "evses": [
                        { "uid": "1", "evse_id": "FR*ABC*E1", "status": "AVAILABLE" }
                      ]
                    }
                  ],
                  "status_code": 1000,
                  "status_message": "Success",
                  "timestamp": "2026-04-25T10:00:00Z"
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = EcoMovementOcpiClient(
            client = HttpClient(engine),
            apiKey = "test-key-123",
            baseUrl = "https://api.eco-movement.com/api/ocpi/cpo/2.2"
        )

        val locations = client.listLocations(limit = 10, offset = 0)
        assertEquals(1, locations.size)
        val loc = locations.first()
        assertEquals("LOC-1", loc.id)
        assertEquals("Test Location", loc.name)
        assertNotNull(loc.coordinates)
        assertEquals("48.8566", loc.coordinates.latitude)
        assertEquals("2.3522", loc.coordinates.longitude)
        assertEquals("Eco Operator", loc.operator?.name)
        assertEquals("FR*ABC*E1", loc.evses?.firstOrNull()?.evse_id)
    }
}

