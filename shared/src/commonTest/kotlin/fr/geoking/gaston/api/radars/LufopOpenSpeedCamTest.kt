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

    private val sampleCsv = """
Numéro;Type;VMA;Latitude;Longitude
1001;FIXE;90;48.8566;2.3522
1002;DISCRIMINANT;110;48.8600;2.3500
1003;MOBILE;NA;48.8700;2.3600
""".trimIndent()

    private val sampleJson = """
[
  {"id": "osc_1", "type": "FIXE", "speed": 80, "latitude": 48.8566, "longitude": 2.3522},
  {"id": "osc_2", "type": "RED_LIGHT", "speed": 50, "latitude": 48.8600, "longitude": 2.3500}
]
""".trimIndent()

    @Test
    fun testParseCsv() {
        val mockEngine = MockEngine { respond("OK") }
        val httpClient = HttpClient(mockEngine)
        val client = LufopOpenSpeedCamClient(httpClient)

        val records = client.parseContent(sampleCsv)
        assertEquals(3, records.size)

        val first = records[0]
        assertEquals("1001", first.id)
        assertEquals("FIXE", first.type)
        assertEquals(90, first.vma)
        assertEquals(48.8566, first.latitude, 0.0001)
        assertEquals(2.3522, first.longitude, 0.0001)

        val third = records[2]
        assertEquals("1003", third.id)
        assertEquals(null, third.vma)
    }

    @Test
    fun testParseJson() {
        val mockEngine = MockEngine { respond("OK") }
        val httpClient = HttpClient(mockEngine)
        val client = LufopOpenSpeedCamClient(httpClient)

        val records = client.parseContent(sampleJson)
        assertEquals(2, records.size)

        val first = records[0]
        assertEquals("osc_1", first.id)
        assertEquals("FIXE", first.type)
        assertEquals(80, first.vma)
        assertEquals(48.8566, first.latitude, 0.0001)
        assertEquals(2.3522, first.longitude, 0.0001)
    }

    @Test
    fun testToPoi() {
        val record = LufopOpenSpeedCamRecord("1001", "FIXE", 90, 48.8566, 2.3522)
        val poi = record.toPoi()

        assertEquals("osc_radar_1001", poi.id)
        assertEquals("Zone 90 km/h", poi.name)
        assertEquals(PoiCategory.Radar, poi.poiCategory)
        assertEquals(48.8566, poi.latitude, 0.0001)
        assertEquals(2.3522, poi.longitude, 0.0001)
        assertFalse(poi.isElectric)
        assertEquals("LufopOpenSpeedCam", poi.source)
        assertEquals("90", poi.rawSourceData?.get("vma"))
    }

    @Test
    fun testProviderSearch() = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = sampleCsv,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/csv")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val client = LufopOpenSpeedCamClient(httpClient)
        val provider = LufopOpenSpeedCamProvider(client, defaultRadiusKm = 10.0)

        assertTrue(provider.shouldQuery(48.8566, 2.3522))

        val results = provider.search(PoiSearchRequest(48.8566, 2.3522, categories = setOf(PoiCategory.Radar)))
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.id == "osc_radar_1001" })
        assertTrue(results.all { it.poiCategory == PoiCategory.Radar })
    }
}
