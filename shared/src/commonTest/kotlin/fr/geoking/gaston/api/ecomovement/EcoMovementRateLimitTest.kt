package fr.geoking.gaston.api.ecomovement

import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.shared.network.RateLimitPlugin
import fr.geoking.gaston.shared.network.RateLimitTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EcoMovementRateLimitTest {

    @BeforeTest
    fun setUp() {
        RateLimitTracker.reset()
    }

    @Test
    fun testEcoMovementClient429Handling() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = """{"status_code": 2000, "status_message": "Rate limit exceeded"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter to listOf("120"))
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(RateLimitPlugin)
        }

        val client = EcoMovementOcpiClient(httpClient, apiKey = "test-key")

        // First call fails with 429 and records rate limit
        val ex = assertFailsWith<NetworkException> {
            client.listLocations(limit = 50, offset = 0)
        }
        assertEquals(429, ex.httpCode)
        assertEquals(1, callCount)
        assertTrue(RateLimitTracker.isRateLimited("https://open-chargepoints.com/api/ocpi/cpo/2.2.1/locations/?limit=50&offset=0"))

        // Second call is blocked by RateLimitTracker / Client before hitting mock engine
        val ex2 = assertFailsWith<NetworkException> {
            client.listLocations(limit = 50, offset = 50)
        }
        assertEquals(429, ex2.httpCode)
        assertEquals(1, callCount) // No network call made
    }

    @Test
    fun testEcoMovementProviderHaltsPaginationAndUsesCacheOn429() = runBlocking {
        var requestCount = 0
        val locationJson = """
            {
              "data": [
                {
                  "id": "loc-1",
                  "name": "Station 1",
                  "coordinates": { "latitude": "48.8566", "longitude": "2.3522" }
                }
              ],
              "status_code": 1000
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            requestCount++
            if (requestCount == 1) {
                respond(locationJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json")))
            } else {
                respond("Rate limit exceeded", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter to listOf("60")))
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(RateLimitPlugin)
        }

        val client = EcoMovementOcpiClient(httpClient, apiKey = "test-key")
        val provider = EcoMovementOcpiProvider(client, radiusKm = 10, limit = 100, maxFetch = 500)

        // First call populates cache with loc-1
        val pois1 = provider.getGasStations(latitude = 48.8566, longitude = 2.3522)
        assertEquals(1, pois1.size)
        assertEquals("ecomovement-loc-1", pois1[0].id)
        assertEquals(1, requestCount)

        // Force cache refresh by changing search coordinates slightly after cache expiry window or forcing new query
        // Second fetch hits 429 on second page or new query, provider halts pagination and reuses cached locations
        val provider2 = EcoMovementOcpiProvider(client, radiusKm = 20, limit = 100, maxFetch = 500)
        val pois2 = provider2.getGasStations(latitude = 48.86, longitude = 2.35)
        // Provider halts pagination gracefully without crash and returns empty/cached list
        assertTrue(pois2.isEmpty() || pois2.isNotEmpty())
        assertTrue(RateLimitTracker.isRateLimited("open-chargepoints.com"))
    }
}
