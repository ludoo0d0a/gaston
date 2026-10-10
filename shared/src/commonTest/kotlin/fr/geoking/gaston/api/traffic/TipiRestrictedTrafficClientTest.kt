package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TipiRestrictedTrafficClientTest {

    @Test
    fun blankCredentials_skipsNetwork() = runBlocking {
        var called = false
        val engine = MockEngine {
            called = true
            respond("should-not-call", HttpStatusCode.OK)
        }
        val client = TipiRestrictedTrafficClient(
            client = HttpClient(engine),
            contentUrl = TipiRestrictedTrafficClient.ACTION_C_URL,
            username = "",
            password = ""
        )
        assertFalse(client.hasCredentials)
        assertNull(client.fetchDatexXml())
        assertFalse(called)
    }

    @Test
    fun sendsBasicAuthHeader() = runBlocking {
        var auth: String? = null
        val engine = MockEngine { request ->
            auth = request.headers[HttpHeaders.Authorization]
            respond(
                "<xml/>",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/xml")
            )
        }
        val client = TipiRestrictedTrafficClient(
            client = HttpClient(engine),
            contentUrl = TipiRestrictedTrafficClient.ACTION_C_URL,
            username = "user",
            password = "pass"
        )
        assertTrue(client.hasCredentials)
        assertEquals("<xml/>", client.fetchDatexXml())
        assertTrue(auth!!.startsWith("Basic "))
    }
}
