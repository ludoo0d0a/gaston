package fr.geoking.gaston.api.afdc

import fr.geoking.gaston.api.belib.AvailabilityStatus
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AfdcAvailabilityTest {

    private val client = AfdcAvailabilityClient(HttpClient(), apiKey = "")

    private val sampleJson = """
        {
          "fuel_stations": [
            {
              "id": 196750,
              "station_name": "Potomac",
              "latitude": 38.907192,
              "longitude": -77.036871,
              "status_code": "E",
              "access_code": "public",
              "street_address": "1600 Penn Ave",
              "city": "Washington",
              "state": "DC",
              "country": "US",
              "distance": 0.3
            },
            {
              "id": 1,
              "station_name": "Far",
              "latitude": 39.5,
              "longitude": -77.0,
              "status_code": "E",
              "distance": 80.0
            },
            {
              "id": 2,
              "station_name": "Temp down",
              "latitude": 38.91,
              "longitude": -77.04,
              "status_code": "T",
              "distance": 0.6
            },
            {
              "id": 3,
              "station_name": "Planned",
              "latitude": 38.90,
              "longitude": -77.03,
              "status_code": "P",
              "distance": 0.75
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseAndFilter_keepsNearbyAndMapsStatus() {
        val stations = client.parseNearestJson(sampleJson)
        assertEquals(4, stations.size)

        val nearby = client.filterAvailability(
            stations = stations,
            latitude = 38.9072,
            longitude = -77.0369,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(3, nearby.size)
        assertEquals(setOf("196750", "2", "3"), nearby.map { it.id }.toSet())
        assertEquals(AvailabilityStatus.Available, client.mapStatus(nearby.first { it.id == "196750" }.statusRaw))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("T"))
        assertEquals(AvailabilityStatus.PlannedIntoService, client.mapStatus("P"))
    }

    @Test
    fun blankApiKey_returnsEmptyWithoutFetch() = kotlinx.coroutines.runBlocking {
        val empty = client.getAvailability(38.9, -77.0, radiusKm = 10, limit = 10)
        assertTrue(empty.isEmpty())
    }
}
