package fr.geoking.gaston.api.finland

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.common.OcpiEvseAvailability
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DigitrafficAfirAvailabilityTest {

    private val client = DigitrafficAfirAvailabilityClient(HttpClient())

    private val sampleLocationsJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [24.9400, 60.1700] },
              "properties": {
                "id": "loc-helsinki",
                "name": "Test Station",
                "address": { "street": "Mannerheimintie 1", "city": "Helsinki", "countryCode": "FIN" },
                "evses": [
                  { "id": "FI*TST*E1*C1" },
                  { "id": "FI*TST*E1*C2" },
                  { "id": "FI*TST*E1*C3" }
                ]
              }
            },
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [25.5000, 65.0000] },
              "properties": {
                "id": "loc-far",
                "evses": [ { "id": "FI*TST*FAR*C1" } ]
              }
            }
          ]
        }
    """.trimIndent()

    private val sampleStatusesJson = """
        {
          "statuses": [
            { "evseId": "FI*TST*E1*C1", "status": "AVAILABLE" },
            { "evseId": "FI*TST*E1*C2", "status": "CHARGING" },
            { "evseId": "FI*TST*E1*C3", "status": "REMOVED" },
            { "evseId": "FI*TST*FAR*C1", "status": "AVAILABLE" }
          ]
        }
    """.trimIndent()

    @Test
    fun parseAndFilter_joinsStatusSkipsRemovedAndFar() {
        val locations = client.parseLocationsJson(sampleLocationsJson)
        val statuses = client.parseStatusesJson(sampleStatusesJson)
        assertEquals(2, locations.size)
        assertEquals(4, statuses.size)

        val nearby = client.filterAvailability(
            locations = locations,
            statusByEvseId = statuses,
            latitude = 60.17,
            longitude = 24.94,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(2, nearby.size)
        assertEquals(setOf("FI*TST*E1*C1", "FI*TST*E1*C2"), nearby.map { it.id }.toSet())
        assertEquals("loc-helsinki", nearby.first().stationId)
        assertEquals("Mannerheimintie 1, Helsinki", nearby.first().address)
        assertEquals(
            AvailabilityStatus.Available,
            OcpiEvseAvailability.mapStatus(nearby.first { it.id.endsWith("C1") }.statusRaw),
        )
        assertEquals(
            AvailabilityStatus.Occupied,
            OcpiEvseAvailability.mapStatus(nearby.first { it.id.endsWith("C2") }.statusRaw),
        )
    }

    @Test
    fun parseLocations_geoJsonCoordinatesAreLonLat() {
        val locations = client.parseLocationsJson(sampleLocationsJson)
        val helsinki = locations.first { it.id == "loc-helsinki" }
        assertEquals(60.17, helsinki.latitude!!, 0.0001)
        assertEquals(24.94, helsinki.longitude!!, 0.0001)
        assertEquals(3, helsinki.evseIds.size)
    }

    @Test
    fun missingStatus_defaultsToUnknown() {
        val locations = client.parseLocationsJson(sampleLocationsJson)
        val nearby = client.filterAvailability(
            locations = locations,
            statusByEvseId = emptyMap(),
            latitude = 60.17,
            longitude = 24.94,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(3, nearby.size)
        assertTrue(nearby.all { it.statusRaw == "UNKNOWN" })
        assertEquals(AvailabilityStatus.Unknown, OcpiEvseAvailability.mapStatus(nearby.first().statusRaw))
    }

    @Test
    fun decodeBody_gunzipsWhenMagicPresent() {
        // Re-encode fixture at runtime so hex stays in sync with sample JSON.
        val bytes = java.io.ByteArrayOutputStream().use { baos ->
            java.util.zip.GZIPOutputStream(baos).use { gzip ->
                gzip.write(sampleLocationsJson.encodeToByteArray())
            }
            baos.toByteArray()
        }
        val text = client.decodeBody(bytes)
        assertEquals(2, client.parseLocationsJson(text).size)
        assertEquals("loc-helsinki", client.parseLocationsJson(text).first().id)
    }

    @Test
    fun decodeBody_acceptsPlainJson() {
        val text = client.decodeBody(sampleLocationsJson.encodeToByteArray())
        assertTrue(text.contains("loc-helsinki"))
        assertEquals(2, client.parseLocationsJson(text).size)
    }

    @Test
    fun mapStatus_viaOcpiEvseAvailability() {
        assertEquals(AvailabilityStatus.Available, OcpiEvseAvailability.mapStatus("AVAILABLE"))
        assertEquals(AvailabilityStatus.Occupied, OcpiEvseAvailability.mapStatus("CHARGING"))
        assertEquals(AvailabilityStatus.Occupied, OcpiEvseAvailability.mapStatus("BLOCKED"))
        assertEquals(AvailabilityStatus.Reserved, OcpiEvseAvailability.mapStatus("RESERVED"))
        assertEquals(AvailabilityStatus.Maintenance, OcpiEvseAvailability.mapStatus("OUTOFORDER"))
        assertEquals(AvailabilityStatus.Maintenance, OcpiEvseAvailability.mapStatus("INOPERATIVE"))
        assertEquals(AvailabilityStatus.Removed, OcpiEvseAvailability.mapStatus("REMOVED"))
        assertEquals(AvailabilityStatus.Unknown, OcpiEvseAvailability.mapStatus("UNKNOWN"))
    }
}
