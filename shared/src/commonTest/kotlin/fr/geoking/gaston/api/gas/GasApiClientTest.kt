package fr.geoking.gaston.api.gas

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiMerger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GasApiClientTest {

    @Test
    fun testParseGasApiStationRawJson() {
        val rawStationJson = """
            {
              "id": "2ff643b2-0ccb-482b-bc2a-e75e126946fe",
              "name": "BMCV SARL",
              "latitude": "49.253910",
              "longitude": "6.170030",
              "address": "20 -22 Rue de Metz",
              "city": "HAGONDANGE",
              "postCode": "57300",
              "freeway": true,
              "allDayService": false,
              "schedules": [],
              "prices": [],
              "services": [
                {
                  "id": "6400a641-96bc-4910-8205-98dfb847ef0c",
                  "name": "Station de gonflage"
                }
              ],
              "brand": {
                "id": "dd7358ac-c518-470d-918f-0725b01fe31e",
                "name": "Avia"
              }
            }
        """.trimIndent()

        val jsonArrayBody = "[$rawStationJson]"

        val client = GasApiClient(io.ktor.client.HttpClient())
        val stations = client.parseStationsResponse(jsonArrayBody)
        val gasApiStation = stations.firstOrNull()

        assertNotNull(gasApiStation, "Station should be parsed")
        assertEquals("BMCV SARL", gasApiStation.name)
        assertEquals("Avia", gasApiStation.brand)
        val rawJson = gasApiStation.rawJson
        assertNotNull(rawJson, "rawJson must be captured")
        assertTrue(rawJson.contains("BMCV SARL"))
        assertTrue(rawJson.contains("20 -22 Rue de Metz"))

        val poi = Poi(
            id = gasApiStation.id,
            name = gasApiStation.name,
            address = gasApiStation.address,
            latitude = gasApiStation.latitude,
            longitude = gasApiStation.longitude,
            brand = gasApiStation.brand,
            source = "GasAPI",
            rawSourceData = mapOf("GasAPI" to rawJson)
        )

        val rawMap = poi.rawSourceData
        assertNotNull(rawMap)
        assertEquals(rawJson, rawMap["GasAPI"])

        // Test merge preserves rawSourceData
        val osmPoi = Poi(
            id = "osm_123",
            name = "Avia Hagondange",
            address = "20-22 Rue de Metz",
            latitude = 49.253910,
            longitude = 6.170030,
            brand = "Avia",
            source = "Overpass",
            rawSourceData = mapOf("Overpass" to """{"type":"node","id":123,"tags":{"amenity":"fuel","brand":"Avia"}}""")
        )

        val mergedList = PoiMerger.mergePois(listOf(poi, osmPoi))
        assertEquals(1, mergedList.size)
        val mergedPoi = mergedList.first()
        val mergedRawMap = mergedPoi.rawSourceData
        assertNotNull(mergedRawMap)
        assertTrue("GasAPI" in mergedRawMap)
        assertTrue("Overpass" in mergedRawMap)
    }
}
