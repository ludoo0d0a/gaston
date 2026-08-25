package fr.geoking.gaston.api.chargyuk

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

class CharGyUkProviderTest {

    private fun buildProvider(json: String): CharGyUkProvider {
        val engine = MockEngine {
            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return CharGyUkProvider(client = HttpClient(engine), radiusKm = 20, limit = 50)
    }

    @Test
    fun getGasStations_returnsUkStationsWithinRadius() = runBlocking {
        val provider = buildProvider(UK_LOCATIONS_RESPONSE)
        // London coordinates
        val pois = provider.getGasStations(latitude = 51.5074, longitude = -0.1278)

        assertEquals(1, pois.size)
        val poi = pois.first()
        assertEquals("chargy-uk-CHARGY-UK-001", poi.id)
        assertTrue(poi.name.contains("High Street Lamp Post #12"))
        assertTrue(poi.name.contains("1/1 free"))
        assertEquals("char.gy", poi.brand)
        assertEquals("char.gy", poi.operator)
        assertEquals("char.gy (UK)", poi.source)
        assertEquals(true, poi.isElectric)
        assertEquals(7.0, poi.powerKw)
        assertEquals(1, poi.chargePointCount)
        assertEquals(1, poi.irveDetails?.availableConnectors)
        assertEquals(1, poi.irveDetails?.totalConnectors)
        assertTrue(poi.irveDetails?.connectorTypes?.contains("type_2") == true)
    }

    @Test
    fun getGasStations_returnsEmptyForNonUkCoordinates() = runBlocking {
        val provider = buildProvider(UK_LOCATIONS_RESPONSE)
        // Paris coordinates (outside UK bbox)
        val pois = provider.getGasStations(latitude = 48.8566, longitude = 2.3522)
        assertTrue(pois.isEmpty())
    }

    companion object {
        private val UK_LOCATIONS_RESPONSE = """
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
