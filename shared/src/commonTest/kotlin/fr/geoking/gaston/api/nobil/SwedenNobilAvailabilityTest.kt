package fr.geoking.gaston.api.nobil

import fr.geoking.gaston.api.belib.AvailabilityStatus
import fr.geoking.gaston.parking.ParkingRegion
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwedenNobilAvailabilityTest {

    private val client = NobilClient(
        client = HttpClient(),
        apiKey = "test-key",
        countryCode = SwedenNobilAvailabilityProvider.COUNTRY_CODE,
    )

    /** Stockholm-area SWE fixture (same datadump shape as NOR). */
    private val swedenSampleJson = """
        {
          "Provider": "NOBIL.no",
          "Rights": "Creative Commons",
          "apiver": "3",
          "chargerstations": [
            {
              "csmd": {
                "id": 12001,
                "name": "Stockholm Test Station",
                "Street": "Sveavägen",
                "House_number": "1",
                "City": "STOCKHOLM",
                "Number_charging_points": 2,
                "Position": "(59.33400,18.06300)",
                "Available_charging_points": 1,
                "Station_status": 1,
                "Land_code": "SWE",
                "International_id": "SWE_12001"
              },
              "attr": {
                "conn": {
                  "1": {
                    "8": {
                      "attrtypeid": "8",
                      "attrname": "Connector status",
                      "attrvalid": "0",
                      "trans": "Vacant",
                      "attrval": "AVAILABLE"
                    },
                    "28": {
                      "attrtypeid": "28",
                      "attrname": "EVSE ID",
                      "attrvalid": "8",
                      "trans": "EVSE ID",
                      "attrval": "SE*NOB*E1"
                    }
                  },
                  "2": {
                    "8": {
                      "attrtypeid": "8",
                      "attrname": "Connector status",
                      "attrvalid": "1",
                      "trans": "Busy (charging)",
                      "attrval": "CHARGING"
                    },
                    "28": {
                      "attrtypeid": "28",
                      "attrname": "EVSE ID",
                      "attrvalid": "8",
                      "trans": "EVSE ID",
                      "attrval": "SE*NOB*E2"
                    }
                  },
                  "3": {
                    "8": {
                      "attrtypeid": "8",
                      "attrname": "Connector status",
                      "attrvalid": "0",
                      "trans": "REMOVED",
                      "attrval": "REMOVED"
                    },
                    "28": {
                      "attrtypeid": "28",
                      "attrname": "EVSE ID",
                      "attrvalid": "8",
                      "trans": "EVSE ID",
                      "attrval": "SE*NOB*E3"
                    }
                  }
                }
              }
            },
            {
              "csmd": {
                "id": 12999,
                "name": "Gothenburg Far",
                "Position": "(57.70800,11.97500)",
                "Station_status": 1,
                "Land_code": "SWE",
                "International_id": "SWE_12999"
              },
              "attr": {
                "conn": {
                  "1": {
                    "8": {
                      "attrtypeid": "8",
                      "attrname": "Connector status",
                      "attrvalid": "0",
                      "trans": "Vacant",
                      "attrval": ""
                    },
                    "28": {
                      "attrtypeid": "28",
                      "attrname": "EVSE ID",
                      "attrvalid": "8",
                      "trans": "EVSE ID",
                      "attrval": "SE*FAR*E1"
                    }
                  }
                }
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun countryCode_isSwe() {
        assertEquals("SWE", SwedenNobilAvailabilityProvider.COUNTRY_CODE)
        assertEquals(NobilClient.COUNTRY_SWE, SwedenNobilAvailabilityProvider.COUNTRY_CODE)
    }

    @Test
    fun parseAndFilter_swedenFixture_skipsRemovedAndFar() {
        val stations = client.parseDatadumpJson(swedenSampleJson)
        assertEquals(2, stations.size)
        assertEquals("SWE", stations.first().csmd?.landCode)

        val nearby = client.filterAvailability(
            stations = stations,
            latitude = 59.334,
            longitude = 18.063,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(2, nearby.size)
        assertEquals(setOf("SE*NOB*E1", "SE*NOB*E2"), nearby.map { it.id }.toSet())
        assertEquals("SWE_12001", nearby.first().stationId)
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
    fun provider_mapsFilteredRecordsLikeNobilAvailabilityProvider() {
        val stations = client.parseDatadumpJson(swedenSampleJson)
        val nearby = client.filterAvailability(
            stations = stations,
            latitude = 59.334,
            longitude = 18.063,
            radiusKm = 5,
            limit = 50,
        )
        // Same mapping path as [NobilAvailabilityProvider] / [SwedenNobilAvailabilityProvider].
        val mapped = nearby.map { record ->
            fr.geoking.gaston.api.belib.PdcAvailability(
                id = record.id,
                status = client.mapStatus(record.statusRaw),
                latitude = record.latitude,
                longitude = record.longitude,
                address = record.address,
                stationId = record.stationId,
            )
        }
        assertEquals(2, mapped.size)
        assertEquals(AvailabilityStatus.Available, mapped.first { it.id == "SE*NOB*E1" }.status)
        assertEquals(AvailabilityStatus.Occupied, mapped.first { it.id == "SE*NOB*E2" }.status)
        assertEquals("Sveavägen 1, STOCKHOLM", mapped.first().address)
        assertEquals("SWE_12001", mapped.first().stationId)
    }

    @Test
    fun provider_blankApiKey_returnsEmpty() = runBlocking {
        val provider = SwedenNobilAvailabilityProvider(
            NobilClient(
                client = HttpClient(),
                apiKey = "",
                countryCode = SwedenNobilAvailabilityProvider.COUNTRY_CODE,
            ),
        )
        assertTrue(provider.getAvailability(59.334, 18.063, 15).isEmpty())
    }

    @Test
    fun parkingRegion_swedenContainsKalmar() {
        // Kalmar: south of Norway coarse bbox (latMin 57.9), east of Denmark (lonMax ~15.16).
        assertEquals(ParkingRegion.Sweden, ParkingRegion.containing(56.663, 16.357))
    }
}
