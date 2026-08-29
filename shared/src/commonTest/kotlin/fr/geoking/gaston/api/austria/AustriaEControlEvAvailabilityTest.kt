package fr.geoking.gaston.api.austria

import fr.geoking.gaston.api.belib.AvailabilityStatus
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AustriaEControlEvAvailabilityTest {

    private val client = AustriaEControlEvClient(
        client = HttpClient(),
        apiKey = "test-key",
        refererDomain = "example.at",
    )

    private val sampleJson = """
        [
          {
            "countryId": "AT",
            "operatorId": "ECT",
            "stationId": "E5487EA07",
            "stationStatus": "ACTIVE",
            "label": "Test Station Wien",
            "postCode": "1010",
            "city": "Wien",
            "street": "Rudolfsplatz 13a",
            "distance": 0.5,
            "points": [
              {
                "evseId": "AT*ECT*E5487EA07*1",
                "status": "AVAILABLE",
                "location": { "lat": 48.2100, "lon": 16.3700 }
              },
              {
                "evseId": "AT*ECT*E5487EA07*2",
                "status": "CHARGING",
                "location": { "lat": 48.2100, "lon": 16.3700 }
              },
              {
                "evseId": "AT*ECT*E5487EA07*3",
                "status": "OCCUPIED",
                "location": { "lat": 48.2100, "lon": 16.3700 }
              },
              {
                "evseId": "AT*ECT*E5487EA07*4",
                "status": "REMOVED",
                "location": { "lat": 48.2100, "lon": 16.3700 }
              }
            ]
          },
          {
            "countryId": "AT",
            "operatorId": "ECT",
            "stationId": "FAR001",
            "stationStatus": "ACTIVE",
            "distance": 80.0,
            "city": "Graz",
            "points": [
              {
                "evseId": "AT*ECT*FAR001*1",
                "status": "AVAILABLE",
                "latitude": 47.0707,
                "longitude": 15.4395
              }
            ]
          },
          {
            "countryId": "AT",
            "operatorId": "ECT",
            "stationId": "INACTIVE1",
            "stationStatus": "INACTIVE",
            "distance": 1.0,
            "points": [
              {
                "evseId": "AT*ECT*INACTIVE*1",
                "status": "AVAILABLE",
                "location": { "lat": 48.2100, "lon": 16.3700 }
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun parseAndFilter_skipsRemovedInactiveAndFarStations() {
        val stations = client.parseSearchJson(sampleJson)
        assertEquals(3, stations.size)

        val nearby = client.filterAvailability(
            stations = stations,
            latitude = 48.21,
            longitude = 16.37,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(3, nearby.size)
        assertEquals(
            setOf(
                "AT*ECT*E5487EA07*1",
                "AT*ECT*E5487EA07*2",
                "AT*ECT*E5487EA07*3",
            ),
            nearby.map { it.id }.toSet(),
        )
        assertEquals("AT/ECT/E5487EA07", nearby.first().stationId)
        assertTrue(nearby.first().address!!.contains("Wien"))
        assertEquals(
            AvailabilityStatus.Available,
            client.mapStatus(nearby.first { it.id.endsWith("*1") }.statusRaw),
        )
        assertEquals(
            AvailabilityStatus.Occupied,
            client.mapStatus(nearby.first { it.id.endsWith("*2") }.statusRaw),
        )
        assertEquals(
            AvailabilityStatus.Occupied,
            client.mapStatus(nearby.first { it.id.endsWith("*3") }.statusRaw),
        )
    }

    @Test
    fun mapStatus_mapsEControlAndOcpiLikeStatuses() {
        assertEquals(AvailabilityStatus.Available, client.mapStatus("AVAILABLE"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("CHARGING"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("OCCUPIED"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("BLOCKED"))
        assertEquals(AvailabilityStatus.Reserved, client.mapStatus("RESERVED"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("OUTOFORDER"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("OUT_OF_ORDER"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("INOPERATIVE"))
        assertEquals(AvailabilityStatus.Removed, client.mapStatus("REMOVED"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("UNKNOWN"))
    }

    @Test
    fun blankCredentials_returnEmptyWithoutNetwork() = kotlinx.coroutines.runBlocking {
        val blank = AustriaEControlEvClient(
            client = HttpClient(),
            apiKey = "",
            refererDomain = "example.at",
        )
        assertTrue(
            blank.getAvailability(48.21, 16.37, radiusKm = 15).isEmpty(),
        )
        val blankDomain = AustriaEControlEvClient(
            client = HttpClient(),
            apiKey = "key",
            refererDomain = "",
        )
        assertTrue(
            blankDomain.getAvailability(48.21, 16.37, radiusKm = 15).isEmpty(),
        )
    }
}
