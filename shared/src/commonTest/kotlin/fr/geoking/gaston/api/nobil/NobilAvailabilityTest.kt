package fr.geoking.gaston.api.nobil

import fr.geoking.gaston.api.belib.AvailabilityStatus
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NobilAvailabilityTest {

    private val client = NobilClient(HttpClient(), apiKey = "test-key")

    private val sampleJson = """
        {
          "Provider": "NOBIL.no",
          "Rights": "Creative Commons",
          "apiver": "3",
          "chargerstations": [
            {
              "csmd": {
                "id": 41,
                "name": "IKEA Slependen",
                "Street": "Nesbruveien",
                "House_number": "40",
                "City": "BILLINGSTAD",
                "Number_charging_points": 2,
                "Position": "(59.87447,10.49982)",
                "Available_charging_points": 1,
                "Station_status": 1,
                "Land_code": "NOR",
                "International_id": "NOR_00041"
              },
              "attr": {
                "st": {
                  "2": {
                    "attrtypeid": "2",
                    "attrname": "Availability",
                    "attrvalid": "1",
                    "trans": "Public",
                    "attrval": ""
                  }
                },
                "conn": {
                  "1": {
                    "4": {
                      "attrtypeid": "4",
                      "attrname": "Connector",
                      "attrvalid": "32",
                      "trans": "Type 2",
                      "attrval": ""
                    },
                    "8": {
                      "attrtypeid": "8",
                      "attrname": "Connector status",
                      "attrvalid": "0",
                      "trans": "Vacant",
                      "attrval": ""
                    },
                    "27": {
                      "attrtypeid": "27",
                      "attrname": "EVSE UID",
                      "attrvalid": "7",
                      "trans": "EVSE UID",
                      "attrval": "uid-available"
                    },
                    "28": {
                      "attrtypeid": "28",
                      "attrname": "EVSE ID",
                      "attrvalid": "8",
                      "trans": "EVSE ID",
                      "attrval": "NO*NOB*E1"
                    }
                  },
                  "2": {
                    "4": {
                      "attrtypeid": "4",
                      "attrname": "Connector",
                      "attrvalid": "39",
                      "trans": "CCS/Combo",
                      "attrval": ""
                    },
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
                      "attrval": "NO*NOB*E2"
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
                      "attrval": "NO*NOB*E3"
                    }
                  }
                }
              }
            },
            {
              "csmd": {
                "id": 99,
                "name": "Far Station",
                "Position": "(70.00000,25.00000)",
                "Station_status": 1,
                "Land_code": "NOR",
                "International_id": "NOR_00099"
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
                      "attrval": "NO*FAR*E1"
                    }
                  }
                }
              }
            },
            {
              "csmd": {
                "id": 50,
                "name": "Inactive",
                "Position": "(59.87500,10.50000)",
                "Station_status": 0,
                "International_id": "NOR_00050"
              },
              "attr": {
                "conn": {
                  "1": {
                    "28": {
                      "attrtypeid": "28",
                      "attrname": "EVSE ID",
                      "attrvalid": "8",
                      "trans": "EVSE ID",
                      "attrval": "NO*OFF*E1"
                    }
                  }
                }
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseAndFilter_skipsRemovedFarAndInactive() {
        val stations = client.parseDatadumpJson(sampleJson)
        assertEquals(3, stations.size)

        val nearby = client.filterAvailability(
            stations = stations,
            latitude = 59.87447,
            longitude = 10.49982,
            radiusKm = 5,
            limit = 50,
        )
        assertEquals(2, nearby.size)
        assertEquals(setOf("NO*NOB*E1", "NO*NOB*E2"), nearby.map { it.id }.toSet())
        assertEquals("NOR_00041", nearby.first().stationId)
        assertEquals(AvailabilityStatus.Available, client.mapStatus(nearby.first { it.id.endsWith("E1") }.statusRaw))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus(nearby.first { it.id.endsWith("E2") }.statusRaw))
    }

    @Test
    fun mapStatus_mapsOcpiAndLegacyStatuses() {
        assertEquals(AvailabilityStatus.Available, client.mapStatus("AVAILABLE"))
        assertEquals(AvailabilityStatus.Available, client.mapStatus("Vacant"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("CHARGING"))
        assertEquals(AvailabilityStatus.Occupied, client.mapStatus("BLOCKED"))
        assertEquals(AvailabilityStatus.Reserved, client.mapStatus("RESERVED"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("OUTOFORDER"))
        assertEquals(AvailabilityStatus.Maintenance, client.mapStatus("INOPERATIVE"))
        assertEquals(AvailabilityStatus.Removed, client.mapStatus("REMOVED"))
        assertEquals(AvailabilityStatus.Unknown, client.mapStatus("UNKNOWN"))
    }

    @Test
    fun connectorStatusRaw_mapsAttr8AndError() {
        val vacant = mapOf("8" to NobilAttr(attrValId = "0", trans = "Vacant"))
        assertEquals("AVAILABLE", NobilClient.connectorStatusRaw(vacant))

        val charging = mapOf("8" to NobilAttr(attrValId = "1", trans = "Busy (charging)"))
        assertEquals("CHARGING", NobilClient.connectorStatusRaw(charging))

        val ocpi = mapOf(
            "8" to NobilAttr(
                attrValId = "0",
                trans = "Vacant",
                attrVal = JsonPrimitive("AVAILABLE"),
            ),
        )
        assertEquals("AVAILABLE", NobilClient.connectorStatusRaw(ocpi))

        val error = mapOf(
            "8" to NobilAttr(attrValId = "0", trans = "Vacant"),
            "9" to NobilAttr(attrValId = "1", trans = "Error - out of service"),
        )
        assertEquals("OUTOFORDER", NobilClient.connectorStatusRaw(error))
    }

    @Test
    fun blankApiKey_returnsEmptyWithoutNetwork() = runBlocking {
        val emptyClient = NobilClient(HttpClient(), apiKey = "")
        assertTrue(emptyClient.getStations().isEmpty())
        assertTrue(emptyClient.getAvailability(latitude = 59.91, longitude = 10.75).isEmpty())
    }

    @Test
    fun parsePosition_readsParenPair() {
        assertEquals(59.87447 to 10.49982, NobilClient.parsePosition("(59.87447,10.49982)"))
        assertEquals(null, NobilClient.parsePosition(null))
        assertEquals(null, NobilClient.parsePosition(""))
    }
}
