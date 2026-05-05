package fr.geoking.gaston.api.datagouv

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DataGouvPrixCarburantClientTest {

    private val client = DataGouvPrixCarburantClient(HttpClient())

    @Test
    fun parseRecords_parsesStation() {
        val body = """
            {
                "results": [
                    {
                        "id": "1",
                        "adresse": "123 Rue de Paris",
                        "ville": "Paris",
                        "cp": "75001",
                        "nom": "Station Paris",
                        "latitude": 48.8566,
                        "longitude": 2.3522,
                        "prix": [
                            {"nom": "Gazole", "valeur": 1.8}
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
        assertEquals(48.8566, station.latitude)
        assertEquals(2.3522, station.longitude)
        assertEquals(1, station.fuels.size)
        assertEquals("Gazole", station.fuels[0].name)
        assertEquals(1.8, station.fuels[0].priceEur)
    }

    @Test
    fun parseRecords_handlesResultsAsSingleObject() {
        val body = """
            {
                "results": {
                    "id": "2",
                    "adresse": "10 Route de Lyon",
                    "ville": "Lyon",
                    "cp": "69001",
                    "nom": "Station Lyon",
                    "latitude": 45.7640,
                    "longitude": 4.8357,
                    "prix": [
                        {"nom": "SP98", "valeur": 1.95}
                    ]
                }
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        assertEquals("2", stations[0].id)
        assertEquals("Station Lyon", stations[0].name)
        assertEquals(1, stations[0].fuels.size)
        assertEquals("SP98", stations[0].fuels[0].name)
        assertEquals(1.95, stations[0].fuels[0].priceEur)
    }

    @Test
    fun parseRecords_scaledLatLon_andAtNomPrix() {
        val body = """
            {
                "results": [
                    {
                        "id": "93170002",
                        "adresse": "44 AVENUE",
                        "ville": "Bagnolet",
                        "cp": "93170",
                        "latitude": "4886205",
                        "longitude": "241650",
                        "pop": "R",
                        "prix": "[{\"@nom\": \"Gazole\", \"@valeur\": \"2.090\"}, {\"@nom\": \"E10\", \"@valeur\": \"1.990\"}]"
                    }
                ]
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        val station = stations[0]
        assertEquals(48.86205, station.latitude, 1e-4)
        assertEquals(2.4165, station.longitude, 1e-4)
        assertEquals(2, station.fuels.size)
        assertEquals("Gazole", station.fuels[0].name)
        assertEquals(2.09, station.fuels[0].priceEur, 1e-6)
        assertEquals("E10", station.fuels[1].name)
    }

    @Test
    fun parseGeo_prefersGeom() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement(
            """
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
            """
        ).jsonObject

        val coords = client.parseGeo(record)
        assertNotNull(coords)
        assertEquals(48.8566, coords.first)
        assertEquals(2.3522, coords.second)
    }

    @Test
    fun parseStationFromRecord_usesBrandWhenNameMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement(
            """
            {
                "id": "123",
                "marque": "TotalEnergies",
                "ville": "Paris",
                "latitude": 48.0,
                "longitude": 2.0
            }
            """
        ).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals("TotalEnergies", station.name)
    }

    @Test
    fun parseStationFromRecord_usesCityFallbackWhenNameAndBrandMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement(
            """
            {
                "id": "456",
                "ville": "Lyon",
                "latitude": 45.0,
                "longitude": 4.0
            }
            """
        ).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals("Station Lyon", station.name)
    }

    @Test
    fun parseStationFromRecord_usesGenericFallbackWhenAllMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement(
            """
            {
                "id": "789",
                "latitude": 44.0,
                "longitude": 3.0
            }
            """
        ).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals("Station", station.name)
    }

    @Test
    fun parseFuels_parsesTopLevelFields() {
        val body = """
            {
                "results": [
                    {
                        "id": "1",
                        "adresse": "123 Rue de Paris",
                        "ville": "Paris",
                        "cp": "75001",
                        "nom": "Station Paris",
                        "latitude": 48.8566,
                        "longitude": 2.3522,
                        "gazole_prix": 1.85,
                        "sp95_prix": 1.95
                    }
                ]
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        val fuels = stations[0].fuels
        assertEquals(2, fuels.size)
        val gazole = fuels.find { it.name == "Gazole" }
        assertNotNull(gazole)
        assertEquals(1.85, gazole.priceEur)
        val sp95 = fuels.find { it.name == "SP95" }
        assertNotNull(sp95)
        assertEquals(1.95, sp95.priceEur)
    }

    @Test
    fun parseStationFromRecord_prefersMarqueOverPop() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement(
            """
            {
                "id": "1",
                "marque": "TotalEnergies",
                "pop": "R",
                "ville": "Paris",
                "latitude": 48.0,
                "longitude": 2.0
            }
            """
        ).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals("TotalEnergies", station.brand)
        assertEquals("TotalEnergies", station.name)
    }

    @Test
    fun parseStationFromRecord_usesPopWhenMarqueMissing() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement(
            """
            {
                "id": "1",
                "pop": "R",
                "ville": "Paris",
                "latitude": 48.0,
                "longitude": 2.0
            }
            """
        ).jsonObject

        val station = client.parseStationFromRecord(record)
        assertNotNull(station)
        assertEquals("Route", station.brand)
        assertEquals("Route", station.name)
    }
}
