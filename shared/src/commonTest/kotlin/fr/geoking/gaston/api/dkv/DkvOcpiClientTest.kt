package fr.geoking.gaston.api.dkv

import fr.geoking.gaston.shared.network.NetworkException
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
import kotlin.test.assertFailsWith

class DkvOcpiClientTest {

    @Test
    fun listLocations_sendsSubscriptionKeyHeader_andParsesOcpiEnvelope() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("test-sub-key", request.headers["Ocp-Apim-Subscription-Key"])
            assertEquals("Token abc", request.headers[HttpHeaders.Authorization])
            assertEquals(true, request.url.encodedPath.endsWith("/locations"))
            respond(
                content = LOCATIONS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = DkvOcpiClient(
            client = HttpClient(engine),
            subscriptionKey = "test-sub-key",
            authorization = "Token abc",
            baseUrl = "https://api-portal.dkv-mobility.com/ocpi/cpo/2.2.1"
        )

        val locations = client.listLocations(limit = 10, offset = 0)
        assertEquals(1, locations.size)
        assertEquals("DKV-LOC-1", locations.first().id)
    }

    @Test
    fun listLocations_throwsNetworkException_onHttpError() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"status_code": 3000, "status_message": "Server Error", "data": []}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = DkvOcpiClient(HttpClient(engine), subscriptionKey = "k")
        assertFailsWith<NetworkException> {
            client.listLocations()
        }
        Unit
    }

    @Test
    fun listLocations_throwsNetworkException_onOcpiStatusCodeError() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"status_code": 2001, "status_message": "Invalid token", "data": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = DkvOcpiClient(HttpClient(engine), subscriptionKey = "k")
        assertFailsWith<NetworkException> {
            client.listLocations()
        }
        Unit
    }

    companion object {
        private val LOCATIONS_RESPONSE = """
            {
              "data": [
                {
                  "id": "DKV-LOC-1",
                  "name": "DKV Test Location",
                  "address": "Somewhere 1",
                  "city": "Berlin",
                  "postal_code": "10115",
                  "country_code": "DE",
                  "coordinates": { "latitude": "52.5200", "longitude": "13.4050" },
                  "operator": { "name": "DKV Mobility" },
                  "evses": [
                    {
                      "uid": "EVSE-1",
                      "status": "AVAILABLE",
                      "connectors": [
                        { "id": "1", "standard": "IEC_62196_T2_COMBO", "max_electric_power": 150000 }
                      ]
                    }
                  ]
                }
              ],
              "status_code": 1000,
              "status_message": "OK",
              "timestamp": "2026-04-28T10:00:00Z"
            }
        """.trimIndent()
    }
}

