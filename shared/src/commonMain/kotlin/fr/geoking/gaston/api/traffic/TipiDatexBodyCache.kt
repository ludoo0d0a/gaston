package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.aac.TextFileCache
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.shared.logging.NetworkLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Dedicated memory + disk cache for large TIPI / Bison Futé DATEX national dumps.
 * Disk keys live under a TIPI-specific [TextFileCache] directory (not the AAC radar cache).
 */
class TipiDatexBodyCache(
    private val diskCache: TextFileCache?,
    private val diskKey: String,
    private val contentUrl: String,
    private val cacheTtlMs: Long,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private var memory: Entry? = null

    data class Entry(
        val body: String,
        val etag: String?,
        val atMs: Long,
    )

    /**
     * Returns a fresh entry from memory or disk without hitting the network.
     * Logs a debug-bar entry with [NetworkLog.fromCache] on hit.
     */
    suspend fun getIfFresh(): Entry? = mutex.withLock {
        val now = nowMs()
        val mem = memory
        if (mem != null && now - mem.atMs < cacheTtlMs) {
            logCacheHit(mem.body.length, source = "memory")
            return@withLock mem
        }
        val disk = diskCache?.read(diskKey) ?: return@withLock null
        if (now - disk.storedAtEpochMs >= cacheTtlMs) return@withLock null
        val entry = Entry(
            body = disk.body,
            etag = disk.version?.takeIf { it.isNotBlank() },
            atMs = disk.storedAtEpochMs,
        )
        memory = entry
        logCacheHit(entry.body.length, source = "disk")
        entry
    }

    /** Stale memory entry kept for If-None-Match / offline fallback (may be past TTL). */
    suspend fun peekMemory(): Entry? = mutex.withLock { memory }

    suspend fun put(body: String, etag: String?) = mutex.withLock {
        val now = nowMs()
        memory = Entry(body = body, etag = etag, atMs = now)
        diskCache?.write(diskKey, body, etag)
    }

    /** Refresh TTL after a 304 Not Modified without rewriting the body. */
    suspend fun touch(etag: String? = null) = mutex.withLock {
        val current = memory ?: return@withLock
        val now = nowMs()
        val nextEtag = etag ?: current.etag
        memory = current.copy(atMs = now, etag = nextEtag)
        diskCache?.write(diskKey, current.body, nextEtag)
    }

    suspend fun clear() = mutex.withLock {
        memory = null
        diskCache?.clear(diskKey)
    }

    private fun logCacheHit(bodyLength: Int, source: String) {
        val host = hostFromUrl(contentUrl)
        val now = nowMs()
        DebugLogStore.addLog(
            NetworkLog(
                id = "tipi-cache-$diskKey-$now-${Random.nextInt(1000, 9999)}",
                url = contentUrl,
                host = host,
                method = "GET",
                requestHeaders = emptyMap(),
                requestBody = null,
                responseHeaders = mapOf("X-Cache" to listOf(source)),
                responseBody = "[cached $source, ${bodyLength} bytes]",
                statusCode = 200,
                durationMs = 0L,
                timestamp = now,
                requestSizeBytes = 0L,
                // Do not count cached bytes toward Data Usage totals.
                responseSizeBytes = 0L,
                fromCache = true,
            )
        )
    }

    private fun hostFromUrl(url: String): String {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = url)
        return afterScheme.substringBefore('/').substringBefore('?')
    }
}
