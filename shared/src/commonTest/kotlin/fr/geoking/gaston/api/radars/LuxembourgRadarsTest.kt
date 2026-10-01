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
import kotlin.test.assertTrue

class LuxembourgRadarsTest {

    private val sampleGeoJson = """
{
  "type": "FeatureCollection",
  "name": "radars",
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [6.120465985298601, 49.613748873437885] },
      "properties": {
        "ID": "68",
        "TRANCON": "Place de l'étoile",
        "DIR": "Place de l'étoile",
        "DIR_": "Place de l'étoile",
        "OBJECTID_1": "651",
        "YEAR": "2021"
      }
    },
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [6.1319, 49.6116] },
      "properties": {
        "ID": "1",
        "TRANCON": "N12 Emeschbach",
        "DIR": "Wincrange",
        "DIR_": "Asselborn",
        "OBJECTID_1": "1",
        "YEAR": "2016"
      }
    }
  ]
}
""".trimIndent()

    @Test
    fun testParseGeoJson() {
        val client = LuxembourgRadarsClient(HttpClient(MockEngine { respond("OK") }))
        val records = client.parseGeoJson(sampleGeoJson)
        assertEquals(2, records.size)

        val first = records[0]
        assertEquals("68", first.id)
        assertEquals(49.613748873437885, first.latitude, 0.0001)
        assertEquals(6.120465985298601, first.longitude, 0.0001)
        assertEquals("Place de l'étoile", first.tranche)
        assertEquals("2021", first.year)
    }

    @Test
    fun testToPoi() {
        val record = LuxembourgRadarRecord(
            id = "68",
            latitude = 49.6137,
            longitude = 6.1205,
            tranche = "Place de l'étoile",
            direction = "A",
            oppositeDirection = "B",
            year = "2021",
        )
        val poi = record.toPoi()
        assertEquals("lu_radar_68", poi.id)
        assertEquals("Place de l'étoile", poi.name)
        assertEquals("A / B", poi.address)
        assertEquals(PoiCategory.Radar, poi.poiCategory)
        assertEquals("LuxembourgRadars", poi.source)
        assertEquals("true", poi.rawSourceData?.get("aac_zone"))
    }

    @Test
    fun testProviderSearchNearLuxembourgCity() = runBlocking {
        val mockEngine = MockEngine {
            respond(
                content = sampleGeoJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = LuxembourgRadarsClient(HttpClient(mockEngine))
        val provider = LuxembourgRadarsProvider(client, defaultRadiusKm = 25.0)

        assertTrue(provider.shouldQuery(49.6116, 6.1319))

        val results = provider.search(
            PoiSearchRequest(49.6116, 6.1319, categories = setOf(PoiCategory.Radar))
        )
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.id == "lu_radar_68" })
        assertTrue(results.all { it.poiCategory == PoiCategory.Radar })
    }

    @Test
    fun testProviderSkipsOutsideLuxembourg() = runBlocking {
        val mockEngine = MockEngine {
            error("should not fetch outside LU")
        }
        val provider = LuxembourgRadarsProvider(
            LuxembourgRadarsClient(HttpClient(mockEngine)),
            defaultRadiusKm = 25.0
        )
        assertTrue(!provider.shouldQuery(48.8566, 2.3522))
        val results = provider.search(
            PoiSearchRequest(48.8566, 2.3522, categories = setOf(PoiCategory.Radar))
        )
        assertTrue(results.isEmpty())
    }
}
