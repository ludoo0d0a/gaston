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
        val xmlFixture = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d2LogicalModel xmlns="http://datex2.eu/schema/2/2_0">
                <payloadPublication>
                    <situation>
                        <situationRecord id="ACTION_C_1" type="Accident">
                            <latitude>48.85</latitude>
                            <longitude>2.35</longitude>
                        </situationRecord>
                    </situation>
                </payloadPublication>
            </d2LogicalModel>
        """.trimIndent()

        val engine = MockEngine { request ->
            auth = request.headers[HttpHeaders.Authorization]
            respond(
                xmlFixture,
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
        val xml = client.fetchDatexXml()
        assertEquals("OK", xml)
        assertTrue(auth!!.startsWith("Basic "))
    }
}
