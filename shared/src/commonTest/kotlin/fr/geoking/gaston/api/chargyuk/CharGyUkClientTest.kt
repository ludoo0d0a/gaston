package fr.geoking.gaston.api.chargyuk

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
import kotlin.test.assertNotNull

class CharGyUkClientTest {

    private fun buildClient(responseJson: String, status: HttpStatusCode = HttpStatusCode.OK): CharGyUkClient {
        val engine = MockEngine {
            respond(
                content = responseJson,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return CharGyUkClient(
            client = HttpClient(engine),
            baseUrl = "https://char.gy/open-ocpi"
        )
    }

    @Test
    fun getLocations_parsesOcpiEnvelopeAndLocations() = runBlocking {
        val client = buildClient(LOCATIONS_RESPONSE)
        val locations = client.getLocations()

        assertEquals(1, locations.size)
        val loc = locations.first()
        assertEquals("CHARGY-UK-001", loc.id)
        assertEquals("High Street Lamp Post #12", loc.name)
        assertEquals("London", loc.city)
        assertNotNull(loc.coordinates)
        assertEquals("51.5074", loc.coordinates?.latitude)
        assertEquals("-0.1278", loc.coordinates?.longitude)
        assertEquals(1, loc.evses?.size)

        val evse = loc.evses?.first()
        assertEquals("AVAILABLE", evse?.status)
        val conn = evse?.connectors?.first()
        assertEquals("IEC_62196_T2", conn?.standard)
        assertEquals(7000.0, conn?.maxElectricPower)
    }

    @Test
    fun getLocations_throwsNetworkException_onHttpError() = runBlocking {
        val client = buildClient("""{"status_code": 3000, "status_message": "Server error", "data": []}""", HttpStatusCode.InternalServerError)
        assertFailsWith<NetworkException> {
            client.getLocations()
        }
        Unit
    }

    companion object {
        private val LOCATIONS_RESPONSE = """
            {
              "data": [
                {
                  "id": "CHARGY-UK-001",
                  "name": "High Street Lamp Post #12",
                  "address": "12 High Street",
                  "city": "London",
                  "postal_code": "EC1A 1BB",
                  "coordinates": {
                    "latitude": "51.5074",
                    "longitude": "-0.1278"
                  },
                  "evses": [
                    {
                      "uid": "EVSE-101",
                      "status": "AVAILABLE",
                      "connectors": [
                        {
                          "id": "1",
                          "standard": "IEC_62196_T2",
                          "format": "SOCKET",
                          "power_type": "AC_1_PHASE",
                          "max_electric_power": 7000.0
                        }
                      ]
                    }
                  ]
                }
              ],
              "status_code": 1000,
              "status_message": "Success"
            }
        """.trimIndent()
    }
}
