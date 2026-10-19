package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.aac.CachedText
import fr.geoking.gaston.aac.TextFileCache
import fr.geoking.gaston.shared.logging.DebugLogStore
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

class BisonFuteTrafficClientCacheTest {

    @BeforeTest
    fun clearLogs() {
        DebugLogStore.clearAll()
    }

    @Test
    fun memoryHit_skipsNetwork_andLogsCached() = runBlocking {
        var networkCalls = 0
        val engine = MockEngine {
            networkCalls++
            respond(
                "<xml>ok</xml>",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ETag, listOf("\"v1\""))
            )
        }
        var now = 1_000L
        val client = BisonFuteTrafficClient(
            client = HttpClient(engine),
            cacheTtlMs = 60_000L,
            nowMs = { now },
        )
        assertEquals("<xml>ok</xml>", client.fetchDatexXml())
        assertEquals(1, networkCalls)

        now = 10_000L
        assertEquals("<xml>ok</xml>", client.fetchDatexXml())
        assertEquals(1, networkCalls)

        val cachedLogs = DebugLogStore.logs.value.filter { it.fromCache }
        assertEquals(1, cachedLogs.size)
        assertTrue(cachedLogs[0].responseHeaders?.get("X-Cache")?.contains("memory") == true)
    }

    @Test
    fun diskHit_skipsNetwork_andLogsCached() = runBlocking {
        var networkCalls = 0
        val disk = FakeTextFileCache().apply {
            write(
                BisonFuteTrafficClient.DISK_CACHE_KEY,
                "<xml>from-disk</xml>",
                "\"disk-etag\"",
            )
            // Force storedAt to a known time via re-write after controlling clock in client.
        }
        // Seed with known timestamp through a first client write path is awkward; write meta via fake.
        disk.entries[BisonFuteTrafficClient.DISK_CACHE_KEY] = CachedText(
            body = "<xml>from-disk</xml>",
            storedAtEpochMs = 1_000L,
            version = "\"disk-etag\"",
        )

        val engine = MockEngine {
            networkCalls++
            respond("should-not", HttpStatusCode.OK)
        }
        val client = BisonFuteTrafficClient(
            client = HttpClient(engine),
            cacheTtlMs = 60_000L,
            nowMs = { 5_000L },
            diskCache = disk,
        )
        assertEquals("<xml>from-disk</xml>", client.fetchDatexXml())
        assertEquals(0, networkCalls)
        val cached = DebugLogStore.logs.value.single { it.fromCache }
        assertTrue(cached.responseHeaders?.get("X-Cache")?.contains("disk") == true)
        assertTrue(cached.host.contains("bison-fute"))
    }

    private class FakeTextFileCache : TextFileCache {
        val entries = mutableMapOf<String, CachedText>()

        override fun read(key: String): CachedText? = entries[key]

        override fun write(key: String, value: String, version: String?) {
            entries[key] = CachedText(
                body = value,
                storedAtEpochMs = System.currentTimeMillis(),
                version = version,
            )
        }

        override fun clear(key: String) {
            entries.remove(key)
        }
    }
}
