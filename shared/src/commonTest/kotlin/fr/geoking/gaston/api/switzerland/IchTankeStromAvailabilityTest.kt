package fr.geoking.gaston.api.switzerland

import fr.geoking.gaston.api.belib.AvailabilityStatus
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IchTankeStromAvailabilityTest {

    private val client = IchTankeStromAvailabilityClient(HttpClient())

    private val sampleStaticJson = """
        {
          "EVSEData": [
            {
              "OperatorID": "CH*TST",
              "OperatorName": "Test CPO",
              "EVSEDataRecord": [
                {
                  "EvseID": "CH*TST*E1",
                  "ChargingStationId": "CH*TST*S1",
                  "GeoCoordinates": { "Google": "47.3769 8.5417" },
                  "Address": { "Street": "Bahnhofstrasse 1", "City": "Zürich" }
                },
                {
                  "EvseID": "CH*TST*E2",
                  "ChargingStationId": "CH*TST*S1",
                  "GeoCoordinates": { "Google": "47.3769 8.5417" },
                  "Address": { "Street": "Bahnhofstrasse 1", "City": "Zürich" }
                },
                {
                  "EvseID": "CH*TST*EFAR",
                  "ChargingStationId": "CH*TST*SFAR",
                  "GeoCoordinates": { "Google": "46.2044 6.1432" },
                  "Address": { "Street": "Rue du Rhône 1", "City": "Genève" }
                },
                {
                  "EvseID": "CH*TST*EBAD",
                  "GeoCoordinates": { "Google": "None None" }
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private val sampleStatusJson = """
        {
          "EVSEStatuses": [
            {
              "OperatorID": "CH*TST",
              "OperatorName": "Test CPO",
              "EVSEStatusRecord": [
                { "EvseID": "CH*TST*E1", "EVSEStatus": "Available" },
                { "EvseID": "CH*TST*E2", "EVSEStatus": "Occupied" },
                { "EvseID": "CH*TST*EFAR", "EVSEStatus": "Available" },
                { "EvseID": "CH*TST*EGONE", "EVSEStatus": "EvseNotFound" }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseAndFilter_skipsFarBadGeoAndEvseNotFound() {
        val staticByEvse = client.parseStaticJson(sampleStaticJson)
        val statusByEvse = client.parseStatusJson(sampleStatusJson)

        assertEquals(3, staticByEvse.size)
        assertTrue("CH*TST*EBAD" !in staticByEvse)
        assertEquals(4, statusByEvse.size)

        val nearby = client.filterAvailability(
            staticByEvse = staticByEvse,
            statusByEvse = statusByEvse,
            latitude = 47.3769,
            longitude = 8.5417,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(2, nearby.size)
        assertEquals(setOf("CH*TST*E1", "CH*TST*E2"), nearby.map { it.id }.toSet())
        assertEquals("CH*TST*S1", nearby.first().stationId)
        assertEquals("Bahnhofstrasse 1, Zürich", nearby.first().address)
        assertEquals(
            AvailabilityStatus.Available,
            client.mapStatus(nearby.first { it.id.endsWith("E1") }.statusRaw),
        )
        assertEquals(
            AvailabilityStatus.Occupied,
            client.mapStatus(nearby.first { it.id.endsWith("E2") }.statusRaw),
        )
    }

    @Test
    fun filterAvailability_skipsEvseNotFoundEvenIfInStatic() {
        val staticByEvse = mapOf(
            "CH*TST*EGONE" to IchTankeStromStaticGeo(
                stationId = "S",
                latitude = 47.3769,
                longitude = 8.5417,
            ),
        )
        val statusByEvse = mapOf("CH*TST*EGONE" to "EvseNotFound")
        val nearby = client.filterAvailability(
            staticByEvse = staticByEvse,
            statusByEvse = statusByEvse,
            latitude = 47.3769,
            longitude = 8.5417,
            radiusKm = 5,
            limit = 50,
        )
        assertTrue(nearby.isEmpty())
    }

    @Test
    fun mapStatus_mapsOicpStatuses() {
        assertEquals(AvailabilityStatus.Available, client.mapStatus("Available"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("Occupied"))
        assertEquals(AvailabilityStatus.Reserved, client.mapStatus("Reserved"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("OutOfService"))
        assertEquals(AvailabilityStatus.Removed, client.mapStatus("EvseNotFound"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("Unknown"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus(""))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("SomethingElse"))
    }

    @Test
    fun parseGoogleCoords_parsesLatLon() {
        assertEquals(47.3769 to 8.5417, IchTankeStromAvailabilityClient.parseGoogleCoords("47.3769 8.5417"))
        assertNull(IchTankeStromAvailabilityClient.parseGoogleCoords("None None"))
        assertNull(IchTankeStromAvailabilityClient.parseGoogleCoords(null))
        assertNull(IchTankeStromAvailabilityClient.parseGoogleCoords(""))
    }
}
