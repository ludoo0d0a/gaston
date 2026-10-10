package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.genericStationName
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(genericStationName("Lyon"), station.name)
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
        assertEquals(genericStationName(), station.name)
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

    @Test
    fun parseFuels_parsesRupturesCorrectly() {
        val body = """
            {
                "results": [
                    {
                        "id": "57120005",
                        "latitude": 49.25,
                        "longitude": 6.096,
                        "gazole_prix": 1.85,
                        "gazole_rupture_type": "temporaire",
                        "sp95_prix": 1.95,
                        "carburants_rupture_definitive": "E85;GPLc"
                    }
                ]
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        val fuels = stations[0].fuels
        val gazole = fuels.find { it.name == "Gazole" }
        assertNotNull(gazole)
        assertEquals(true, gazole.outOfStock)
        assertEquals("temporaire", gazole.shortageType)

        val sp95 = fuels.find { it.name == "SP95" }
        assertNotNull(sp95)
        assertEquals(false, sp95.outOfStock)

        // Definitive ruptures without a price are discontinued fuels — do not invent OOS rows.
        assertNull(fuels.find { it.name == "E85" })
        assertNull(fuels.find { it.name == "GPLc" })
    }

    @Test
    fun parseFuels_skipsDefinitiveRuptureWithoutPrice_keepsTemporary() {
        // TotalEnergies Route de Metz, Rombas (57120001): SP95 definitive since 2022,
        // E10 temporary shortage, no current prices in the flux.
        val body = """
            {
                "results": [
                    {
                        "id": "57120001",
                        "adresse": "ROUTE DE METZ",
                        "ville": "Rombas",
                        "cp": "57120",
                        "latitude": 49.236,
                        "longitude": 6.109,
                        "carburants_rupture_temporaire": "E10",
                        "rupture": "[{\"@nom\": \"E85\", \"@id\": \"3\", \"@debut\": \"2017-09-01 07:01:44\", \"@fin\": \"\", \"@type\": \"definitive\"}, {\"@nom\": \"GPLc\", \"@id\": \"4\", \"@debut\": \"2017-09-01 07:01:44\", \"@fin\": \"\", \"@type\": \"definitive\"}, {\"@nom\": \"SP95\", \"@id\": \"2\", \"@debut\": \"2022-10-21 06:43:59\", \"@fin\": \"\", \"@type\": \"definitive\"}, {\"@nom\": \"E10\", \"@id\": \"5\", \"@debut\": \"2026-10-09 12:37:02\", \"@fin\": \"\", \"@type\": \"temporaire\"}]",
                        "sp95_rupture_type": "definitive",
                        "sp95_rupture_debut": "2022-10-21T06:43:59+00:00",
                        "e10_rupture_type": "temporaire",
                        "e10_rupture_debut": "2026-10-09T12:37:02+00:00",
                        "e85_rupture_type": "definitive",
                        "e85_rupture_debut": "2017-09-01T07:01:44+00:00",
                        "gplc_rupture_type": "definitive",
                        "gplc_rupture_debut": "2017-09-01T07:01:44+00:00"
                    }
                ]
            }
        """.trimIndent()

        val fuels = client.parseRecords(body).single().fuels
        assertNull(fuels.find { it.name.equals("SP95", ignoreCase = true) })
        assertNull(fuels.find { it.name.equals("E85", ignoreCase = true) })
        assertNull(fuels.find { it.name.equals("GPLc", ignoreCase = true) })

        val e10 = fuels.find { it.name.equals("E10", ignoreCase = true) }
        assertNotNull(e10)
        assertTrue(e10.outOfStock)
        assertTrue(e10.shortageType?.contains("temp", ignoreCase = true) == true)
        assertEquals("2026-10-09T12:37:02+00:00", e10.shortageStart)
    }
}
