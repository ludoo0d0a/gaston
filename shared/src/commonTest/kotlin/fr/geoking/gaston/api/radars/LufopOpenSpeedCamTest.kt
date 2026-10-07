package fr.geoking.gaston.api.radars

import fr.geoking.gaston.aac.CachedText
import fr.geoking.gaston.aac.TextFileCache
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.shared.network.RateLimitTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LufopOpenSpeedCamTest {

    @BeforeTest
    fun resetRateLimits() {
        RateLimitTracker.reset()
    }

    private class InMemoryTextFileCache : TextFileCache {
        private val store = mutableMapOf<String, CachedText>()

        override fun read(key: String): CachedText? = store[key]

        override fun write(key: String, value: String, version: String?) {
            store[key] = CachedText(
                body = value,
                storedAtEpochMs = System.currentTimeMillis(),
                version = version,
            )
        }

        override fun clear(key: String) {
            store.remove(key)
        }

        fun seed(key: String, body: String, version: String, storedAtEpochMs: Long = System.currentTimeMillis()) {
            store[key] = CachedText(body = body, storedAtEpochMs = storedAtEpochMs, version = version)
        }
    }

    private val sampleJson = """
[
  {"ID":"203301","name":"Zone de danger FR Chantier","lat":46.753419,"lng":6.379827,"type":"154","commune":"Jougne","voie":"Route de Vallorbe","flash":"Double sens","emplacement":"à droite de la voie","azimut":"45","update":"2017-11-25 22:22:36"},
  {"ID":"173140","name":"Zone de danger FR 130","lat":49.090582,"lng":5.235781,"type":"105","commune":"Les Souhesmes Rampont","voie":"A4","flash":"Double sens","emplacement":"à droite de la voie","azimut":"115","update":"2017-11-25 19:36:50"},
  {"id":"osc_1","type":"FIXE","speed":80,"latitude":48.8566,"longitude":2.3522},
  {"ID":"235323","name":"Radar Fixe FR 50","lat":48.9011723,"lng":2.3497122,"type":"15","commune":"Paris","voie":"Quai","flash":"F","azimut":"270","vitesse":"50"}
]
""".trimIndent()

    @Test
    fun testParseLufopJson() {
        val mockEngine = MockEngine { respond("OK") }
        val httpClient = HttpClient(mockEngine)
        val client = LufopOpenSpeedCamClient(httpClient, apiKey = "test")

        val records = client.parseContent(sampleJson)
        assertEquals(4, records.size)

        val first = records[0]
        assertEquals("203301", first.id)
        assertEquals("154", first.type)
        assertEquals(null, first.vma)
        assertEquals(46.753419, first.latitude, 0.0001)
        assertEquals(6.379827, first.longitude, 0.0001)
        assertEquals("Jougne", first.commune)
        assertEquals(45.0, first.azimut)
        assertEquals("Double sens", first.flash)

        val second = records[1]
        assertEquals("173140", second.id)
        assertEquals(130, second.vma)
        assertEquals("A4", second.voie)

        val third = records[2]
        assertEquals("osc_1", third.id)
        assertEquals(80, third.vma)
        assertEquals(48.8566, third.latitude, 0.0001)

        val liveApiShape = records[3]
        assertEquals("235323", liveApiShape.id)
        assertEquals(50, liveApiShape.vma)
        assertEquals(270.0, liveApiShape.azimut)
        assertEquals("F", liveApiShape.flash)
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
            azimut = 115.0,
            flash = "F",
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
        assertEquals("115.0", poi.rawSourceData?.get("monitored_bearing"))
    }

    @Test
    fun toPoi_doubleSensIsBidirectional() {
        val poi = LufopOpenSpeedCamRecord(
            id = "1",
            type = "154",
            vma = null,
            latitude = 46.0,
            longitude = 6.0,
            flash = "Double sens",
            azimut = 45.0,
        ).toPoi()
        assertEquals("true", poi.rawSourceData?.get("bidirectional"))
        assertEquals("true", poi.rawSourceData?.get("direction_bidirectional"))
        assertEquals("both", poi.rawSourceData?.get("direction"))
        assertEquals(null, poi.rawSourceData?.get("monitored_bearing"))
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
    fun blankApiKey_searchResultExposesError() = runBlocking {
        val client = LufopOpenSpeedCamClient(HttpClient(MockEngine { respond("OK") }), apiKey = "")
        val provider = LufopOpenSpeedCamProvider(client)
        val result = provider.searchResult(
            PoiSearchRequest(48.8566, 2.3522, categories = setOf(PoiCategory.Radar))
        )
        assertTrue(result.pois.isEmpty())
        assertTrue(result.errors.any { it.providerName == "LufopOpenSpeedCam" && it.message.contains("LUFOP_API_KEY") })
    }

    @Test
    fun emptyResponseIsNotCached_retriesOnNextCall() = runBlocking {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            when (calls) {
                1 -> respond(
                    content = "[]",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    content = sampleJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val client = LufopOpenSpeedCamClient(
            HttpClient(mockEngine),
            apiKey = "test-key",
            minNetworkIntervalMs = 0L,
        )
        assertTrue(client.getRecordsNear(48.8566, 2.3522, radiusKm = 500.0).isEmpty())
        val second = client.getRecordsNear(48.8566, 2.3522, radiusKm = 500.0)
        assertEquals(2, calls)
        assertTrue(second.isNotEmpty())
    }

    @Test
    fun panWithinPrefetchRadius_reusesMemoryCache() = runBlocking {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = LufopOpenSpeedCamClient(HttpClient(mockEngine), apiKey = "test-key")
        // ~20 km north of Paris — still inside 40% of 100 km prefetch.
        assertTrue(client.getRecordsNear(48.8566, 2.3522, radiusKm = 15.0).isNotEmpty())
        assertTrue(client.getRecordsNear(49.03, 2.3522, radiusKm = 15.0).isNotEmpty())
        assertEquals(1, calls)
    }

    @Test
    fun diskCacheHit_skipsNetwork() = runBlocking {
        val disk = InMemoryTextFileCache()
        disk.seed(
            LufopOpenSpeedCamClient.DISK_CACHE_KEY,
            sampleJson,
            version = "48.8566,2.3522,100.0",
        )
        val mockEngine = MockEngine {
            error("network should not be called when disk cache is fresh")
        }
        val client = LufopOpenSpeedCamClient(
            HttpClient(mockEngine),
            apiKey = "test-key",
            diskCache = disk,
        )
        val records = client.getRecordsNear(48.8566, 2.3522, radiusKm = 15.0)
        assertTrue(records.isNotEmpty())
        assertTrue(records.any { it.id == "osc_1" })
    }

    @Test
    fun throttle_reusesStaleCacheWithinInterval() = runBlocking {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = LufopOpenSpeedCamClient(
            HttpClient(mockEngine),
            apiKey = "test-key",
            minNetworkIntervalMs = 8_000L,
        )
        assertTrue(client.getRecordsNear(48.8566, 2.3522, radiusKm = 15.0).isNotEmpty())
        // Far from Paris — geo miss, but throttle returns existing cache instead of a 2nd HTTP.
        val far = client.getRecordsNear(0.0, 0.0, radiusKm = 15.0)
        assertEquals(1, calls)
        assertTrue(far.isEmpty()) // filtered to equator; cache blob still served internally
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
