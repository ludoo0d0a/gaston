package fr.geoking.gaston.api.radars

import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiSearchRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LufopOpenSpeedCamTest {

    private val sampleJson = """
[
  {"ID":"203301","name":"Zone de danger FR Chantier","lat":46.753419,"lng":6.379827,"type":"154","commune":"Jougne","voie":"Route de Vallorbe","flash":"Double sens","emplacement":"à droite de la voie","azimut":"45","update":"2017-11-25 22:22:36"},
  {"ID":"173140","name":"Zone de danger FR 130","lat":49.090582,"lng":5.235781,"type":"105","commune":"Les Souhesmes Rampont","voie":"A4","flash":"Double sens","emplacement":"à droite de la voie","azimut":"115","update":"2017-11-25 19:36:50"},
  {"id":"osc_1","type":"FIXE","speed":80,"latitude":48.8566,"longitude":2.3522}
]
""".trimIndent()

    @Test
    fun testParseLufopJson() {
        val mockEngine = MockEngine { respond("OK") }
        val httpClient = HttpClient(mockEngine)
        val client = LufopOpenSpeedCamClient(httpClient, apiKey = "test")

        val records = client.parseContent(sampleJson)
        assertEquals(3, records.size)

        val first = records[0]
        assertEquals("203301", first.id)
        assertEquals("154", first.type)
        assertEquals(null, first.vma)
        assertEquals(46.753419, first.latitude, 0.0001)
        assertEquals(6.379827, first.longitude, 0.0001)
        assertEquals("Jougne", first.commune)

        val second = records[1]
        assertEquals("173140", second.id)
        assertEquals(130, second.vma)
        assertEquals("A4", second.voie)

        val third = records[2]
        assertEquals("osc_1", third.id)
        assertEquals(80, third.vma)
        assertEquals(48.8566, third.latitude, 0.0001)
    }

    @Test
    fun testExtractVmaFromName() {
        val mockEngine = MockEngine { respond("OK") }
        val client = LufopOpenSpeedCamClient(HttpClient(mockEngine), apiKey = "test")
        assertEquals(130, client.extractVmaFromName("Zone de danger FR 130"))
        assertEquals(90, client.extractVmaFromName("Radar 90 km/h"))
        assertEquals(null, client.extractVmaFromName("Zone de danger FR Chantier"))
    }

    @Test
    fun testToPoi() {
        val record = LufopOpenSpeedCamRecord(
            id = "173140",
            type = "105",
            vma = 130,
            latitude = 49.090582,
            longitude = 5.235781,
            name = "Zone de danger FR 130",
            commune = "Les Souhesmes Rampont",
            voie = "A4",
        )
        val poi = record.toPoi()

        assertEquals("osc_radar_173140", poi.id)
        assertEquals("Zone de danger FR 130", poi.name)
        assertEquals("A4, Les Souhesmes Rampont", poi.address)
        assertEquals(PoiCategory.Radar, poi.poiCategory)
        assertEquals(49.090582, poi.latitude, 0.0001)
        assertEquals(5.235781, poi.longitude, 0.0001)
        assertFalse(poi.isElectric)
        assertEquals("LufopOpenSpeedCam", poi.source)
        assertEquals("130", poi.rawSourceData?.get("vma"))
    }

    @Test
    fun blankApiKeyReturnsEmpty() = runBlocking {
        val mockEngine = MockEngine {
            error("should not be called without API key")
        }
        val client = LufopOpenSpeedCamClient(HttpClient(mockEngine), apiKey = "")
        assertTrue(client.getRecordsNear(48.8566, 2.3522).isEmpty())
    }

    @Test
    fun testProviderSearch() = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val client = LufopOpenSpeedCamClient(httpClient, apiKey = "test-key")
        val provider = LufopOpenSpeedCamProvider(client, defaultRadiusKm = 500.0)

        assertTrue(provider.shouldQuery(48.8566, 2.3522))

        val results = provider.search(PoiSearchRequest(48.8566, 2.3522, categories = setOf(PoiCategory.Radar)))
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.id == "osc_radar_osc_1" })
        assertTrue(results.all { it.poiCategory == PoiCategory.Radar })
    }

    @Test
    fun rejectsHtmlBody() {
        val client = LufopOpenSpeedCamClient(HttpClient(MockEngine { respond("OK") }), apiKey = "test")
        assertTrue(client.parseContent("<!DOCTYPE html><html lang=ru>").isEmpty())
    }
}
