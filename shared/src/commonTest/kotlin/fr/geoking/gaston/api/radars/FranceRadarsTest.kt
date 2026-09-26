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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FranceRadarsTest {

    private val sampleCsv = """
Numéro de radar;Type de radar;Date de mise en service;VMA ;Latitude; Longitude
12001;ETD;31/10/2011 00:00;70;+45.361;+4.2526
12008;FIXE;31/10/2011 00:00;80;+46.413340;+4.436502
12014;DISCRIMINANT;21/03/2012 00:00;130;+48.8566;+2.3522
12015;ETD;28/03/2012 00:00;NA;+48.8600;+2.3500
""".trimIndent()

    @Test
    fun testParseCsv() {
        val mockEngine = MockEngine { respond("OK") }
        val httpClient = HttpClient(mockEngine)
        val client = FranceRadarsClient(httpClient)

        val records = client.parseCsv(sampleCsv)

        assertEquals(4, records.size)

        val first = records[0]
        assertEquals("12001", first.id)
        assertEquals("ETD", first.type)
        assertEquals(70, first.vma)
        assertEquals(45.361, first.latitude, 0.0001)
        assertEquals(4.2526, first.longitude, 0.0001)

        val third = records[2]
        assertEquals("12014", third.id)
        assertEquals(130, third.vma)
        assertEquals(48.8566, third.latitude, 0.0001)
        assertEquals(2.3522, third.longitude, 0.0001)

        val fourth = records[3]
        assertEquals("12015", fourth.id)
        assertEquals(null, fourth.vma)
    }

    @Test
    fun testToPoi() {
        val record = FranceRadarRecord("12014", "FIXE", 110, 48.8566, 2.3522)
        val poi = record.toPoi()

        assertEquals("fr_radar_12014", poi.id)
        assertEquals("Radar 110 km/h", poi.name)
        assertEquals(PoiCategory.Radar, poi.poiCategory)
        assertEquals(48.8566, poi.latitude, 0.0001)
        assertEquals(2.3522, poi.longitude, 0.0001)
        assertFalse(poi.isElectric)
        assertEquals("FranceRadars", poi.source)
        assertEquals("110", poi.rawSourceData?.get("vma"))
    }

    @Test
    fun testProviderScopingAndSearch() = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = sampleCsv,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/csv")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val client = FranceRadarsClient(httpClient)
        val provider = FranceRadarsProvider(client, defaultRadiusKm = 10.0)

        // Scoped to FR
        assertTrue(provider.shouldQuery(48.8566, 2.3522))

        val results = provider.search(PoiSearchRequest(48.8566, 2.3522, categories = setOf(PoiCategory.Radar)))

        // Near Paris (48.8566, 2.3522), records 12014 and 12015 are close
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.id == "fr_radar_12014" })
        assertTrue(results.all { it.poiCategory == PoiCategory.Radar })
    }
}
