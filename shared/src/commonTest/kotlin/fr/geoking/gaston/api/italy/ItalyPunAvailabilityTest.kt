package fr.geoking.gaston.api.italy

import fr.geoking.gaston.api.belib.AvailabilityStatus
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItalyPunAvailabilityTest {

    private val client = ItalyPunAvailabilityClient(HttpClient())

    private val sampleJson = """
        {
          "features": [
            {
              "attributes": {
                "ID_EVSE": "IT*ENX*E1*1",
                "ID_univoco_EVSE": "loc1_1",
                "ID_location": "loc-rome",
                "Stato": "AVAILABLE",
                "Latitudine_EVSE": 41.9028,
                "Longitudine_EVSE": 12.4964,
                "Indirizzo": "Via del Corso 1",
                "Città": "Roma"
              }
            },
            {
              "attributes": {
                "ID_EVSE": "IT*ENX*E1*2",
                "ID_univoco_EVSE": "loc1_2",
                "ID_location": "loc-rome",
                "Stato": "CHARGING",
                "Latitudine_EVSE": 41.9028,
                "Longitudine_EVSE": 12.4964,
                "Indirizzo": "Via del Corso 1",
                "Città": "Roma"
              }
            },
            {
              "attributes": {
                "ID_EVSE": "IT*ENX*E1*3",
                "ID_location": "loc-rome",
                "Stato": "REMOVED",
                "Latitudine_EVSE": 41.9028,
                "Longitudine_EVSE": 12.4964
              }
            },
            {
              "attributes": {
                "ID_EVSE": "IT*ENX*EFAR*1",
                "ID_location": "loc-milan",
                "Stato": "AVAILABLE",
                "Latitudine_EVSE": 45.4642,
                "Longitudine_EVSE": 9.1900
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseAndFilter_skipsRemovedAndFarStations() {
        val features = client.parseQueryJson(sampleJson)
        assertEquals(4, features.size)

        val nearby = client.filterAvailability(
            features = features,
            latitude = 41.9028,
            longitude = 12.4964,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(2, nearby.size)
        assertEquals(setOf("IT*ENX*E1*1", "IT*ENX*E1*2"), nearby.map { it.id }.toSet())
        assertEquals("loc-rome", nearby.first().stationId)
        assertTrue(nearby.first().address!!.contains("Roma"))
        assertEquals(
            AvailabilityStatus.Available,
            client.mapStatus(nearby.first { it.id.endsWith("*1") }.statusRaw),
        )
        assertEquals(
            AvailabilityStatus.Occupied,
            client.mapStatus(nearby.first { it.id.endsWith("*2") }.statusRaw),
        )
    }

    @Test
    fun mapStatus_mapsOcpiLikeStato() {
        assertEquals(AvailabilityStatus.Available, client.mapStatus("AVAILABLE"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("CHARGING"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("BLOCKED"))
        assertEquals(AvailabilityStatus.Reserved, client.mapStatus("RESERVED"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("OUTOFORDER"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("INOPERATIVE"))
        assertEquals(AvailabilityStatus.PlannedIntoService, client.mapStatus("PLANNED"))
        assertEquals(AvailabilityStatus.Removed, client.mapStatus("REMOVED"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("UNKNOWN"))
    }

    @Test
    fun bboxWhereClause_coversRadiusAroundPoint() {
        val where = client.bboxWhereClause(41.9, 12.5, radiusKm = 10)
        assertTrue(where.contains("Latitudine_EVSE BETWEEN"))
        assertTrue(where.contains("Longitudine_EVSE BETWEEN"))
        // ~10 km ≈ 0.09° latitude
        assertTrue(where.contains("41.8"))
        assertTrue(where.contains("41.9"))
    }

    @Test
    fun parseQueryJson_throwsOnArcGisError() {
        val err = """{"error":{"code":403,"message":"You do not have permissions"}}"""
        try {
            client.parseQueryJson(err)
            throw AssertionError("expected NetworkException")
        } catch (e: fr.geoking.gaston.shared.network.NetworkException) {
            assertEquals(403, e.httpCode)
        }
    }
}
