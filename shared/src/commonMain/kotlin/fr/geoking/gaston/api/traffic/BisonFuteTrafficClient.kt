package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.aac.TextFileCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Fetches Bison Futé / TIPI open DATEX II SituationPublication for the French national
 * non-concessioned road network. No API key. Licence Ouverte 2.0.
 *
 * Uses a dedicated [TipiDatexBodyCache] (memory + optional disk under `filesDir/tipi/`) because
 * the national dump is multi‑MB. Revalidates with ETag / If-None-Match when the TTL expires.
 *
 * @see <a href="https://transport.data.gouv.fr/datasets/evenements-routiers-sur-le-reseau-routier-national-non-concede">transport.data.gouv.fr</a>
 */
class BisonFuteTrafficClient(
    private val client: HttpClient,
    private val contentUrl: String = DEFAULT_CONTENT_URL,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    diskCache: TextFileCache? = null,
) {
    private val bodyCache = TipiDatexBodyCache(
        diskCache = diskCache,
        diskKey = DISK_CACHE_KEY,
        contentUrl = contentUrl,
        cacheTtlMs = cacheTtlMs,
        nowMs = nowMs,
    )

    /**
     * Returns the raw DATEX II XML body, or null on network/HTTP failure with no cache.
     * Serves the dedicated TIPI cache when still fresh; otherwise revalidates with ETag.
     */
    suspend fun fetchDatexXml(): String? {
        bodyCache.getIfFresh()?.let { return it.body }
        val stale = bodyCache.peekMemory()
        return try {
            val response = client.get(contentUrl) {
                stale?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            }
            when (response.status) {
                HttpStatusCode.NotModified -> {
                    if (stale != null) {
                        bodyCache.touch(stale.etag)
                        stale.body
                    } else {
                        null
                    }
                }
                HttpStatusCode.OK -> {
                    val body = response.bodyAsText()
                    if (body.isBlank()) return stale?.body
                    val etag = response.headers[HttpHeaders.ETag]
                    bodyCache.put(body = body, etag = etag)
                    body
                }
                else -> stale?.body
            }
        } catch (_: Exception) {
            stale?.body
        }
    }

    suspend fun clearCache() {
        bodyCache.clear()
    }

    companion object {
        const val DEFAULT_CONTENT_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-ouvert/publicationsDIR/Evenementiel-DIR/grt/RRN/content.xml"
        const val DEFAULT_CACHE_TTL_MS = 10 * 60_000L
        const val DISK_CACHE_KEY = "bison_fute_rrn"
    }
}
