package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetches TIPI restricted DATEX SituationPublication feeds (Action B / Action C) with HTTP Basic auth.
 * Credentials blank → [fetchDatexXml] returns null. Cache is TTL-only (Action C ignores If-None-Match).
 *
 * Request access: diffusion-numerique@info-routiere.gouv.fr
 */
class TipiRestrictedTrafficClient(
    private val client: HttpClient,
    private val contentUrl: String,
    private val username: String,
    private val password: String,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private var cache: CachedBody? = null

    val hasCredentials: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchDatexXml(): String? {
        if (!hasCredentials) return null
        return mutex.withLock {
            val cached = cache
            val now = nowMs()
            if (cached != null && now - cached.atMs < cacheTtlMs) {
                return@withLock cached.body
            }
            val basic = Base64.Default.encode("$username:$password".encodeToByteArray())
            try {
                val response = client.get(contentUrl) {
                    header(HttpHeaders.Authorization, "Basic $basic")
                }
                when (response.status) {
                    HttpStatusCode.OK -> {
                        val body = response.bodyAsText()
                        if (body.isBlank()) return@withLock cached?.body
                        cache = CachedBody(body = body, atMs = now)
                        body
                    }
                    else -> cached?.body
                }
            } catch (_: Exception) {
                cached?.body
            }
        }
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 10 * 60_000L
        const val ACTION_C_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-C/content.xml"
        const val ACTION_B_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-B/content.xml"
    }

    private data class CachedBody(val body: String, val atMs: Long)
}
