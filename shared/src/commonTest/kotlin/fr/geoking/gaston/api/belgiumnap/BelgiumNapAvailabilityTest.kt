package fr.geoking.gaston.api.belgiumnap

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.api.belib.PdcAvailability
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BelgiumNapAvailabilityTest {

    private val client = BelgiumNapAvailabilityClient(HttpClient())

    private val sampleJson = """
        [
          {
            "country_code": "BE",
            "party_id": "EFL",
            "id": "loc-brussels",
            "name": "Test Station",
            "address": "Rue de la Loi 1",
            "city": "Bruxelles",
            "coordinates": { "latitude": "50.8500", "longitude": "4.3500" },
            "evses": [
              {
                "uid": "u1",
                "evse_id": "BE*EFL*EV1*C1",
                "status": "AVAILABLE"
              },
              {
                "uid": "u2",
                "evse_id": "BE*EFL*EV1*C2",
                "status": "CHARGING"
              },
              {
                "uid": "u3",
                "evse_id": "BE*EFL*EV1*C3",
                "status": "REMOVED"
              }
            ]
          },
          {
            "id": "loc-far",
            "coordinates": { "latitude": "51.2000", "longitude": "4.4000" },
            "evses": [
              { "evse_id": "BE*EFL*FAR*C1", "status": "AVAILABLE" }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun parseAndFilter_skipsRemovedAndFarStations() {
        val locations = client.parseLocationsJson(sampleJson)
        assertEquals(2, locations.size)

        val nearby = client.filterAvailability(
            locations = locations,
            latitude = 50.85,
            longitude = 4.35,
            radiusKm = 5,
            limit = 50
        )
        assertEquals(2, nearby.size)
        assertEquals(setOf("BE*EFL*EV1*C1", "BE*EFL*EV1*C2"), nearby.map { it.id }.toSet())
        assertEquals("loc-brussels", nearby.first().stationId)
        assertEquals(AvailabilityStatus.Available, client.mapStatus(nearby.first { it.id.endsWith("C1") }.statusRaw))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus(nearby.first { it.id.endsWith("C2") }.statusRaw))
    }

    @Test
    fun mapStatus_mapsOcpiStatuses() {
        assertEquals(AvailabilityStatus.Available, client.mapStatus("AVAILABLE"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("CHARGING"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("BLOCKED"))
        assertEquals(AvailabilityStatus.Reserved, client.mapStatus("RESERVED"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("OUTOFORDER"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("INOPERATIVE"))
        assertEquals(AvailabilityStatus.Removed, client.mapStatus("REMOVED"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("UNKNOWN"))
    }

    @Test
    fun factory_returnsBelgiumNapInBelgium() {
        val belib = object : fr.geoking.gaston.api.belib.BorneAvailabilityProvider {
            override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int) =
                emptyList<PdcAvailability>()
        }
        val quali = object : fr.geoking.gaston.api.belib.BorneAvailabilityProvider {
            override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int) =
                emptyList<PdcAvailability>()
        }
        val belgium = object : fr.geoking.gaston.api.belib.BorneAvailabilityProvider {
            override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int) =
                emptyList<PdcAvailability>()
        }
        val factory = fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory(
            belibProvider = belib,
            qualiChargeProvider = quali,
            belgiumNapProvider = belgium,
        )
        // Brussels → Belgium NAP (not QualiCharge despite FR bbox overlap)
        assertEquals(belgium, factory.getProvider(50.85, 4.35))
        assertEquals(quali, factory.getProvider(45.75, 4.85))
        assertTrue(factory.getProvider(48.85, 2.35) !== belib)
        assertTrue(factory.getProvider(48.85, 2.35) !== quali)
    }
}
