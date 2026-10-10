package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetches Bison Futé / TIPI open DATEX II SituationPublication for the French national
 * non-concessioned road network. No API key. Licence Ouverte 2.0.
 *
 * Caches the national dump in memory (TTL) and uses ETag / If-None-Match when available.
 *
 * @see <a href="https://transport.data.gouv.fr/datasets/evenements-routiers-sur-le-reseau-routier-national-non-concede">transport.data.gouv.fr</a>
 */
class BisonFuteTrafficClient(
    private val client: HttpClient,
    private val contentUrl: String = DEFAULT_CONTENT_URL,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private var cache: CachedBody? = null

    /**
     * Returns the raw DATEX II XML body, or null on network/HTTP failure.
     * Serves the in-memory cache when still fresh; otherwise revalidates with ETag.
     */
    suspend fun fetchDatexXml(): String? = mutex.withLock {
        val cached = cache
        val now = nowMs()
        if (cached != null && now - cached.atMs < cacheTtlMs) {
            return@withLock cached.body
        }
        return@withLock try {
            val response = client.get(contentUrl) {
                cached?.etag?.let { header(HttpHeaders.IfNoneMatch, it) }
            }
            when (response.status) {
                HttpStatusCode.NotModified -> {
                    if (cached != null) {
                        cache = cached.copy(atMs = now)
                        cached.body
                    } else {
                        null
                    }
                }
                HttpStatusCode.OK -> {
                    val body = response.bodyAsText()
                    if (body.isBlank()) return@withLock cached?.body
                    val etag = response.headers[HttpHeaders.ETag]
                    cache = CachedBody(body = body, etag = etag, atMs = now)
                    body
                }
                else -> cached?.body
            }
        } catch (_: Exception) {
            cached?.body
        }
    }

    companion object {
        const val DEFAULT_CONTENT_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-ouvert/publicationsDIR/Evenementiel-DIR/grt/RRN/content.xml"
        const val DEFAULT_CACHE_TTL_MS = 10 * 60_000L
    }

    private data class CachedBody(
        val body: String,
        val etag: String?,
        val atMs: Long
    )
}
