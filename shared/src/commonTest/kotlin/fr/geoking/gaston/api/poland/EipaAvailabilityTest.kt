package fr.geoking.gaston.api.poland

import fr.geoking.gaston.api.belib.AvailabilityStatus
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EipaAvailabilityTest {

    private val client = EipaAvailabilityClient(HttpClient())

    private val dynamicJson = """
        {
          "data": [
            {
              "point_id": 1,
              "code": "PL-TST-EEVP00001",
              "status": { "availability": 1, "status": 1, "ts": "2026-01-01T12:00:00+01:00" }
            },
            {
              "point_id": 2,
              "code": "PL-TST-EEVP00002",
              "status": { "availability": 1, "status": 0, "ts": "2026-01-01T12:00:00+01:00" }
            },
            {
              "point_id": 3,
              "code": "PL-TST-EEVP00003",
              "status": { "availability": 0, "status": 0, "ts": "2026-01-01T12:00:00+01:00" }
            },
            {
              "point_id": 4,
              "code": "PL-TST-EGAS00001",
              "prices": [{ "price": "3.73", "unit": "m3" }]
            },
            {
              "point_id": 5,
              "code": "PL-TST-EEVP00005",
              "status": { "availability": 1, "status": 1, "ts": "2026-01-01T12:00:00+01:00" }
            }
          ],
          "generated": "2026-01-01T12:00:00+01:00"
        }
    """.trimIndent()

    private val pointJson = """
        {
          "data": [
            {
              "id": 1,
              "code": "PL-TST-EEVP00001",
              "station_id": 10,
              "charging_solutions": [{ "mode": 4, "power": 22 }],
              "connectors": [{ "interfaces": [5], "cable_attached": false, "power": 22 }]
            },
            {
              "id": 2,
              "code": "PL-TST-EEVP00002",
              "station_id": 10,
              "charging_solutions": [{ "mode": 4, "power": 50 }],
              "connectors": []
            },
            {
              "id": 3,
              "code": "PL-TST-EEVP00003",
              "station_id": 10,
              "charging_solutions": [{ "mode": 4, "power": 22 }],
              "connectors": []
            },
            {
              "id": 4,
              "code": "PL-TST-EGAS00001",
              "station_id": 11,
              "gas_type": "CNG",
              "charging_solutions": [],
              "connectors": []
            },
            {
              "id": 5,
              "code": "PL-TST-EEVP00005",
              "station_id": 12,
              "charging_solutions": [{ "mode": 4, "power": 22 }],
              "connectors": []
            }
          ],
          "generated": "2026-01-01T12:00:00+01:00"
        }
    """.trimIndent()

    private val stationJson = """
        {
          "data": [
            {
              "id": 10,
              "pool_id": 100,
              "type": "E",
              "latitude": 52.2297,
              "longitude": 21.0122,
              "suspended": false,
              "location": {
                "city": "Warszawa",
                "province": "mazowieckie"
              }
            },
            {
              "id": 11,
              "pool_id": 101,
              "type": "G",
              "latitude": 52.2297,
              "longitude": 21.0122,
              "suspended": false
            },
            {
              "id": 12,
              "pool_id": 102,
              "type": "E",
              "latitude": 50.0614,
              "longitude": 19.9366,
              "suspended": false,
              "location": { "city": "Kraków", "province": "małopolskie" }
            }
          ],
          "generated": "2026-01-01T12:00:00+01:00"
        }
    """.trimIndent()

    @Test
    fun parseAndFilter_skipsGasFarAndMapsStatuses() {
        val snapshot = client.buildSnapshot(
            dynamic = client.parseDynamicJson(dynamicJson),
            points = client.parsePointJson(pointJson),
            stations = client.parseStationJson(stationJson),
        )
        assertEquals(5, snapshot.dynamic.size)
        assertEquals(5, snapshot.pointsById.size)
        assertEquals(3, snapshot.stationsById.size)

        val nearby = client.filterAvailability(
            snapshot = snapshot,
            latitude = 52.2297,
            longitude = 21.0122,
            radiusKm = 5,
            limit = 50,
        )
        // 1 Available, 2 Occupied, 3 Maintenance; gas skipped; Kraków far
        assertEquals(3, nearby.size)
        assertEquals(
            setOf("PL-TST-EEVP00001", "PL-TST-EEVP00002", "PL-TST-EEVP00003"),
            nearby.map { it.id }.toSet(),
        )
        assertEquals("10", nearby.first().stationId)
        assertTrue(nearby.first().address!!.contains("Warszawa"))

        fun statusOf(suffix: String): AvailabilityStatus {
            val record = nearby.first { it.id.endsWith(suffix) }
            return client.mapStatus(record.availability, record.freeStatus)
        }
        assertEquals(AvailabilityStatus.Available, statusOf("00001"))
        assertEquals(AvailabilityStatus.Occupied, statusOf("00002"))
        assertEquals(AvailabilityStatus.Maintenance, statusOf("00003"))
    }

    @Test
    fun mapStatus_mapsEipaFlags() {
        assertEquals(AvailabilityStatus.Available, client.mapStatus(1, 1))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus(1, 0))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus(0, 1))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus(0, 0))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus(1, null))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus(null, null))
    }

    @Test
    fun blankExportKey_returnsEmptyWithoutNetwork() = runBlocking {
        val blank = EipaAvailabilityClient(HttpClient(), exportKey = "")
        assertEquals(emptyList(), blank.getAvailability(52.23, 21.01, radiusKm = 15))
    }

    @Test
    fun exportUrls_matchDocumentedPattern() {
        val base = EipaAvailabilityClient.DEFAULT_EXPORT_BASE_URL
        val key = EipaAvailabilityClient.DEFAULT_EXPORT_KEY
        assertTrue(base.startsWith("https://eipa.udt.gov.pl/reader/export-data"))
        assertTrue(key.isNotBlank())
        assertEquals(
            "https://eipa.udt.gov.pl/reader/export-data/dynamic/$key",
            "$base/dynamic/$key",
        )
    }
}
