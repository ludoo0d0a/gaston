package fr.geoking.gaston.api.dotnl

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.common.OcpiEvseAvailability
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DotNlAvailabilityTest {

    private val client = DotNlAvailabilityClient(HttpClient())

    private val sampleJson = """
        [
          {
            "country_code": "NL",
            "party_id": "QWC",
            "id": "loc-amsterdam",
            "name": "Test Station",
            "address": "Damrak 1",
            "city": "Amsterdam",
            "coordinates": { "latitude": "52.3700", "longitude": "4.8900" },
            "evses": [
              {
                "uid": "u1",
                "evse_id": "NL*QWC*EV1*C1",
                "status": "AVAILABLE"
              },
              {
                "uid": "u2",
                "evse_id": "NL*QWC*EV1*C2",
                "status": "CHARGING"
              },
              {
                "uid": "u3",
                "evse_id": "NL*QWC*EV1*C3",
                "status": "REMOVED"
              }
            ]
          },
          {
            "id": "loc-far",
            "coordinates": { "latitude": "53.2000", "longitude": "6.5000" },
            "evses": [
              { "evse_id": "NL*QWC*FAR*C1", "status": "AVAILABLE" }
            ]
          }
        ]
    """.trimIndent()

    /** gzip(sampleJson) — same payload as [sampleJson], for decodeBody coverage. */
    private val sampleGzipHex =
        "1f8b08000000000002ff8d915f4bc33014c5dff7292e792cb3f48fd3cdb7d8d539a8133ba90f63c8a58952b6b690a4820cbfbb49ea2675a3f32181dcc3fddd734f5603809d3e00a460e406c8b6ce2fb0948a0b862519b652852537e233970a960a5551577b0d19135c4a234fb114b8017f2fe585fa3475fa9797d7b56045858a9bbe9d9eaa91aa6176c82870c36bcf234363a67a3fd42fddf14497e1eb07c23fa46d5fd927184cd3eed0f8a6d9e8af6d6191384f2f911367be13594dea1d1aeb9966749ed0db243e803ba4a087147449d13d4d67f3c5ec3428ec01855d501a3f3c66f154732c66ad6f4b3cfaa73714ff4b347403ef44a257eec83b93e8b1e33b9af686f8eb79b0fe06490c5c965e020000"

    @Test
    fun parseAndFilter_skipsRemovedAndFarStations() {
        val locations = client.parseLocationsJson(sampleJson)
        assertEquals(2, locations.size)

        val nearby = client.filterAvailability(
            locations = locations,
            latitude = 52.37,
            longitude = 4.89,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(2, nearby.size)
        assertEquals(setOf("NL*QWC*EV1*C1", "NL*QWC*EV1*C2"), nearby.map { it.id }.toSet())
        assertEquals("loc-amsterdam", nearby.first().stationId)
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
    fun decodeBody_gunzipsOcpiDump() {
        val bytes = sampleGzipHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val text = client.decodeBody(bytes)
        val locations = client.parseLocationsJson(text)
        assertEquals(2, locations.size)
        assertEquals("loc-amsterdam", locations.first().id)
    }

    @Test
    fun decodeBody_acceptsPlainJson() {
        val text = client.decodeBody(sampleJson.encodeToByteArray())
        assertTrue(text.contains("loc-amsterdam"))
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
