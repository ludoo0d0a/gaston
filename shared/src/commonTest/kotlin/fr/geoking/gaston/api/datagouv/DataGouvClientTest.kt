package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.genericStationName
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DataGouvClientTest {

    private val client = DataGouvClient(HttpClient())

    @Test
    fun parseRecords_withFields_parsesStation() {
        // ODS API v2.1 /records returns fields at top level (results is array of objects)
        val body = """
            {
                "results": [
                    {
                        "id": "1",
                        "adresse": "123 Rue de Paris",
                        "ville": "Paris",
                        "cp": "75001",
                        "nom": "Station Paris",
                        "marque": "Total",
                        "latitude": 48.8566,
                        "longitude": 2.3522,
                        "prix": [
                            {"nom": "Gazole", "valeur": 1.8, "maj": "2023-10-27T12:00:00Z"}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        val station = stations[0]
        assertEquals("1", station.id)
        assertEquals("Station Paris", station.name)
        assertEquals("123 Rue de Paris, 75001, Paris", station.address)
        assertEquals(48.8566, station.latitude)
        assertEquals(2.3522, station.longitude)
        assertEquals("Total", station.brand)
        assertEquals(1, station.prices.size)
        assertEquals("Gazole", station.prices[0].fuelName)
        assertEquals(1.8, station.prices[0].price)
    }

    @Test
    fun parseRecords_handlesResultsAsSingleObject() {
        // API may return "results" as a single object instead of array
        val body = """
            {
                "results": {
                    "id": "3",
                    "adresse": "5 Avenue des Champs",
                    "ville": "Marseille",
                    "cp": "13001",
                    "nom": "Station Marseille",
                    "marque": "Shell",
                    "latitude": 43.2965,
                    "longitude": 5.3698,
                    "prix": [
                        {"nom": "E85", "valeur": 0.89, "maj": "2023-10-28T10:00:00Z"}
                    ]
                }
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        assertEquals("3", stations[0].id)
        assertEquals("Station Marseille", stations[0].name)
        assertEquals("Shell", stations[0].brand)
        assertEquals(1, stations[0].prices.size)
        assertEquals("E85", stations[0].prices[0].fuelName)
        assertEquals(0.89, stations[0].prices[0].price)
    }

    @Test
    fun parseGeo_prefersGeom() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement("""
            {
                "geom": {
                    "type": "Point",
                    "coordinates": [2.3522, 48.8566]
                },
                "geolocation": {
                    "type": "Point",
                    "coordinates": [0.0, 0.0]
                }
            }
        """).jsonObject

        val coords = client.parseGeo(record)
        assertNotNull(coords)
        assertEquals(48.8566, coords.first)
        assertEquals(2.3522, coords.second)
    }

    @Test
    fun parseStationFromRecord_usesBrandWhenNameMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement("""
            {
                "id": "123",
                "marque": "TotalEnergies",
                "ville": "Paris",
                "latitude": 48.0,
                "longitude": 2.0
            }
        """).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals("TotalEnergies", station.name)
    }

    @Test
    fun parseStationFromRecord_usesCityFallbackWhenNameAndBrandMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement("""
            {
                "id": "456",
                "ville": "Lyon",
                "latitude": 45.0,
                "longitude": 4.0
            }
        """).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals(genericStationName("Lyon"), station.name)
    }

    @Test
    fun parseStationFromRecord_usesGenericFallbackWhenAllMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement("""
            {
                "id": "789",
                "latitude": 44.0,
                "longitude": 3.0
            }
        """).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals(genericStationName(), station.name)
    }
}
