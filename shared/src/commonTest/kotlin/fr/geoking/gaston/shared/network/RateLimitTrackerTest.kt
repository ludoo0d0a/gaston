package fr.geoking.gaston.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimitTrackerTest {

    @BeforeTest
    fun setUp() {
        RateLimitTracker.reset()
    }

    @Test
    fun testExtractHost() {
        assertEquals("open-chargepoints.com", RateLimitTracker.extractHost("https://open-chargepoints.com/api/ocpi/cpo/2.2.1/locations/?limit=50&offset=0"))
        assertEquals("open-chargepoints.com", RateLimitTracker.extractHost("http://open-chargepoints.com:8080/test"))
        assertEquals("api.example.com", RateLimitTracker.extractHost("api.example.com"))
    }

    @Test
    fun testParseRetryAfterMs() {
        assertEquals(30_000L, RateLimitTracker.parseRetryAfterMs("30"))
        assertEquals(null, RateLimitTracker.parseRetryAfterMs(null))
        assertEquals(null, RateLimitTracker.parseRetryAfterMs("invalid"))
    }

    @Test
    fun testRecordAndCheckRateLimit() {
        val host = "https://open-chargepoints.com/api/ocpi/cpo/2.2.1/locations/?limit=50&offset=0"
        assertFalse(RateLimitTracker.isRateLimited(host))

        RateLimitTracker.recordRateLimit(host, retryAfterHeader = "10")
        assertTrue(RateLimitTracker.isRateLimited(host))
        assertTrue(RateLimitTracker.getRemainingCooldownMs(host) > 0)

        RateLimitTracker.reset()
        assertFalse(RateLimitTracker.isRateLimited(host))
    }

    @Test
    fun testRateLimitPluginInterceptsRequests() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            if (callCount == 1) {
                respond(
                    content = "Too Many Requests",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter to listOf("60"))
                )
            } else {
                respond("OK", HttpStatusCode.OK)
            }
        }

        val client = HttpClient(mockEngine) {
            install(RateLimitPlugin)
        }

        // First call receives 429 and records rate limit
        client.get("https://open-chargepoints.com/api/ocpi/cpo/2.2.1/locations/?limit=50&offset=0")
        assertEquals(1, callCount)
        assertTrue(RateLimitTracker.isRateLimited("open-chargepoints.com"))

        // Second call should be blocked by RateLimitPlugin before hitting network engine
        val ex = assertFailsWith<NetworkException> {
            client.get("https://open-chargepoints.com/api/ocpi/cpo/2.2.1/locations/?limit=50&offset=50")
        }
        assertEquals(429, ex.httpCode)
        assertEquals(1, callCount) // Call count did not increase because request was blocked
    }
}
