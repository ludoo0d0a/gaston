package fr.geoking.gaston.api.overpass

import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.shared.network.RateLimitTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OverpassClientTest {

    @BeforeTest
    fun setUp() {
        RateLimitTracker.reset()
    }

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
        val client = OverpassClient(
            HttpClient(engine),
            baseUrl = "https://overpass-api.de/api/interpreter",
            minIntervalMs = 0L,
        )
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
    fun executeQuery_on429_recordsCooldownFromBody_andBlocksNextCall() = runBlocking {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(
                content = "Rate limited (cooldown 59 s remaining). Try again later.",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val client = OverpassClient(
            HttpClient(engine),
            baseUrl = "https://overpass-api.de/api/interpreter",
            minIntervalMs = 0L,
        )

        val first = assertFailsWith<NetworkException> {
            client.queryNodes(
                latitude = 48.85,
                longitude = 2.35,
                radiusKm = 1,
                amenityValues = setOf("fuel"),
                limit = 1,
            )
        }
        assertEquals(429, first.httpCode)
        assertTrue(first.message!!.contains("cooldown 59 s remaining"))
        assertTrue(RateLimitTracker.isRateLimited("overpass-api.de"))
        assertEquals(1, callCount)

        val second = assertFailsWith<NetworkException> {
            client.queryNodes(
                latitude = 48.85,
                longitude = 2.35,
                radiusKm = 1,
                amenityValues = setOf("fuel"),
                limit = 1,
            )
        }
        assertEquals(429, second.httpCode)
        assertTrue(second.message!!.contains("cooldown"))
        assertTrue(second.message!!.contains("s remaining"))
        assertEquals(1, callCount) // no network flood while cooling down
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
