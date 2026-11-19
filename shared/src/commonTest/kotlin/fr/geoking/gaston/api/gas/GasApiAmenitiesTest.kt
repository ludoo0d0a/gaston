package fr.geoking.gaston.api.gas

import fr.geoking.gaston.api.routex.PoiAmenities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GasApiAmenitiesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mapServiceNames_mapsKnownFrenchLabels() {
        val amenities = GasApiAmenities.mapServiceNames(
            listOf(
                "Lavage automatique",
                "Toilettes publiques",
                "Boutique alimentaire",
                "Automate CB 24/24",
                "Restauration à emporter",
                "Piste poids lourds",
                "AdBlue",
            ),
        )
        assertEquals(true, amenities.carWash)
        assertEquals(true, amenities.toilets)
        assertEquals(true, amenities.shop)
        assertEquals(true, amenities.automat)
        assertEquals(true, amenities.open24h)
        assertEquals(true, amenities.food)
        assertEquals(true, amenities.snackbar)
        assertEquals(true, amenities.truckLane)
        assertEquals(true, amenities.adBluePump)
    }

    @Test
    fun isMappedServiceName_gonflageIsUnmapped() {
        assertFalse(GasApiAmenities.isMappedServiceName("Station de gonflage"))
        assertTrue(GasApiAmenities.isMappedServiceName("Toilettes publiques"))
    }

    @Test
    fun fromStationJson_mapsServicesSchedulesAndFlags() {
        val body = """
            {
              "id": "1",
              "name": "Test",
              "latitude": "48.0",
              "longitude": "2.0",
              "freeway": true,
              "allDayService": true,
              "services": [
                { "id": "a", "name": "Lavage manuel" },
                { "id": "b", "name": "Station de gonflage" }
              ],
              "schedules": [
                {
                  "day": 1,
                  "name": "Lundi",
                  "opening_time": "06:00:00",
                  "closing_time": "22:00:00"
                }
              ]
            }
        """.trimIndent()
        val obj = json.parseToJsonElement(body).jsonObject
        val amenities = GasApiAmenities.fromStationJson(obj)
        assertEquals(true, amenities.motorwayIndicator)
        assertEquals(true, amenities.open24h)
        assertEquals(true, amenities.carWash)
        assertEquals("06:00", amenities.monOpenFuel)
        assertEquals("22:00", amenities.monCloseFuel)
        assertTrue(amenities.openingHoursFuel.any { it.contains("gonflage", ignoreCase = true) })
    }

    @Test
    fun parseStationsResponse_populatesAmenitiesOnStation() {
        val body = """
            [{
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
                { "id": "1", "name": "Station de gonflage" },
                { "id": "2", "name": "Toilettes publiques" }
              ],
              "brand": { "id": "x", "name": "Avia" }
            }]
        """.trimIndent()
        val station = GasApiClient(io.ktor.client.HttpClient()).parseStationsResponse(body).single()
        assertTrue(station.isOnHighway)
        val amenities = assertNotNull(station.amenities)
        assertEquals(true, amenities.toilets)
        assertTrue(amenities.openingHoursFuel.any { it.contains("gonflage", ignoreCase = true) })
        assertFalse(GasApiAmenities.isThin(amenities))
    }

    @Test
    fun isThin_nullOrEmptyIsThin() {
        assertTrue(GasApiAmenities.isThin(null))
        assertTrue(GasApiAmenities.isThin(PoiAmenities()))
        assertFalse(GasApiAmenities.isThin(PoiAmenities(toilets = true)))
        assertFalse(GasApiAmenities.isThin(PoiAmenities(openingHoursFuel = listOf("Lundi: 08:00 – 20:00"))))
    }
}
