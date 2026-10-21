package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.shared.logging.DebugLogStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BisonFuteTrafficClientCacheTest {

    @BeforeTest
    fun clearLogs() {
        DebugLogStore.clearAll()
    }

    @Test
    fun fetchAndStoreTrafficEvents_storesEventsInRepo() = runBlocking {
        var networkCalls = 0
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d2LogicalModel xmlns="http://datex2.eu/schema/2/2_0">
                <payloadPublication>
                    <situation>
                        <situationRecord id="FR_999" type="Accident">
                            <situationRecordVersionTime>2026-10-21T10:00:00Z</situationRecordVersionTime>
                            <latitude>48.85</latitude>
                            <longitude>2.35</longitude>
                            <value>A1</value>
                            <tpegOtherPointDescriptorType>linkName</tpegOtherPointDescriptorType>
                        </situationRecord>
                    </situation>
                </payloadPublication>
            </d2LogicalModel>
        """.trimIndent()

        val engine = MockEngine {
            networkCalls++
            respond(xml, HttpStatusCode.OK)
        }

        var clock = 1000L
        val repo = FakeTrafficRepository(clockMs = { clock })
        val client = BisonFuteTrafficClient(
            client = HttpClient(engine),
            trafficRepository = repo,
            cacheTtlMs = 3600_000L,
            nowMs = { clock }
        )

        val events = client.fetchAndStoreTrafficEvents()
        assertEquals(1, events.size)
        assertEquals(1, networkCalls)

        // Second call while fresh should return emptyList (because repo is fresh, network not fetched)
        val cachedEvents = client.fetchAndStoreTrafficEvents()
        assertEquals(0, cachedEvents.size)
        assertEquals(1, networkCalls)

        // Expire cache for the source so next call fetches fresh data
        repo.clear("bison_fute_rrn")
        val refreshedEvents = client.fetchAndStoreTrafficEvents()
        assertEquals(1, refreshedEvents.size)
        assertEquals(2, networkCalls)

        val inBbox = repo.getTrafficInBbox(48.0, 2.0, 49.0, 3.0)
        assertEquals(1, inBbox.size)
        assertEquals("FR_999", inBbox[0].sourceId)
    }
}
