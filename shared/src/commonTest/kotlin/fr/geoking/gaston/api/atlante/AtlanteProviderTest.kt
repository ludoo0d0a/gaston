package fr.geoking.gaston.api.atlante

import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AtlanteProviderTest {

    private val sampleGeodataJson = """
    [
      {
        "id": "Station-1-41.137683--8.6301725",
        "name": "Atlante Porto Center",
        "site_status": "ONLINE",
        "address": "Rua do Candal 123",
        "province": "Norte",
        "region": "Porto",
        "country": "PT",
        "latitude": "41.137683",
        "longitude": "-8.6301725",
        "coordinates": {
          "latitude": 41.137683,
          "longitude": -8.6301725
        },
        "evses": [
          {
            "connectors": [
              {
                "standard": "CCS 2",
                "format": "Cable",
                "power_type": "DC",
                "max_electric_power": 150000,
                "max_voltage": 800,
                "max_amperage": 200
              },
              {
                "standard": "Type 2",
                "format": "Socket",
                "power_type": "AC",
                "max_electric_power": 22000,
                "max_voltage": 400,
                "max_amperage": 32
              }
            ]
          }
        ]
      },
      {
        "id": "Station-2-45.4642--9.1900",
        "name": "Atlante Milano Duomo",
        "site_status": "ONLINE",
        "address": "Piazza del Duomo",
        "country": "IT",
        "latitude": "45.4642",
        "longitude": "9.1900",
        "coordinates": {
          "latitude": 45.4642,
          "longitude": 9.1900
        },
        "evses": [
          {
            "connectors": [
              {
                "standard": "CCS",
                "max_electric_power": 300000
              }
            ]
          }
        ]
      }
    ]
    """.trimIndent()

    private fun createMockClient(): AtlanteClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleGeodataJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine)
        return AtlanteClient(httpClient)
    }

    @Test
    fun getGasStations_returnsFilteredStationsNearCoordinates() = runBlocking {
        val client = createMockClient()
        val provider = AtlanteProvider(client, radiusKm = 20)

        // Query near Porto station (41.137683, -8.6301725)
        val pois = provider.getGasStations(41.137, -8.630, viewport = null)

        assertEquals(1, pois.size)
        val poi = pois.first()
        assertEquals("atlante_Station-1-41.137683--8.6301725", poi.id)
        assertEquals("Atlante Porto Center", poi.name)
        assertEquals("atlante", poi.brand)
        assertTrue(poi.isElectric)
        assertEquals(PoiCategory.Irve, poi.poiCategory)
        assertEquals(150.0, poi.powerKw) // 150000 W -> 150 kW
        assertEquals(2, poi.chargePointCount)

        assertNotNull(poi.irveDetails)
        assertEquals(setOf("combo_ccs", "type_2"), poi.irveDetails?.connectorTypes)
    }

    @Test
    fun mapAtlanteStandard_mapsKnownStandardsCorrectly() {
        assertEquals("combo_ccs", mapAtlanteStandard("CCS 2"))
        assertEquals("combo_ccs", mapAtlanteStandard("CCS"))
        assertEquals("type_2", mapAtlanteStandard("Type 2"))
        assertEquals("chademo", mapAtlanteStandard("CHAdeMO"))
        assertEquals("tesla_s", mapAtlanteStandard("TESLA"))
    }
}
