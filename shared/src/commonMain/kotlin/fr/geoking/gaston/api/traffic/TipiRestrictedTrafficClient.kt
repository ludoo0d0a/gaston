package fr.geoking.gaston.api.traffic

import fr.geoking.gaston.aac.TextFileCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Fetches TIPI restricted DATEX SituationPublication feeds (Action B / Action C) with HTTP Basic auth.
 * Credentials blank → [fetchDatexXml] returns null.
 *
 * Uses a dedicated [TipiDatexBodyCache] (memory + optional disk under `filesDir/tipi/`) — Action
 * dumps are large national XML files. Cache is TTL-only (Action C ignores If-None-Match).
 *
 * Request access: diffusion-numerique@info-routiere.gouv.fr
 */
class TipiRestrictedTrafficClient(
    private val client: HttpClient,
    private val contentUrl: String,
    private val username: String,
    private val password: String,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    diskCache: TextFileCache? = null,
    diskKey: String = diskKeyForUrl(contentUrl),
) {
    private val bodyCache = TipiDatexBodyCache(
        diskCache = diskCache,
        diskKey = diskKey,
        contentUrl = contentUrl,
        cacheTtlMs = cacheTtlMs,
        nowMs = nowMs,
    )

    val hasCredentials: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchDatexXml(): String? {
        if (!hasCredentials) return null
        bodyCache.getIfFresh()?.let { return it.body }
        val stale = bodyCache.peekMemory()
        val basic = Base64.Default.encode("$username:$password".encodeToByteArray())
        return try {
            val response = client.get(contentUrl) {
                header(HttpHeaders.Authorization, "Basic $basic")
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val body = response.bodyAsText()
                    if (body.isBlank()) return stale?.body
                    bodyCache.put(body = body, etag = null)
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
        const val DEFAULT_CACHE_TTL_MS = 10 * 60_000L
        const val ACTION_C_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-C/content.xml"
        const val ACTION_B_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-B/content.xml"
        const val DISK_CACHE_KEY_ACTION_C = "tipi_action_c"
        const val DISK_CACHE_KEY_ACTION_B = "tipi_action_b"

        fun diskKeyForUrl(url: String): String = when (url) {
            ACTION_C_URL -> DISK_CACHE_KEY_ACTION_C
            ACTION_B_URL -> DISK_CACHE_KEY_ACTION_B
            else -> "tipi_" + url.hashCode().toUInt().toString(16)
        }
    }
}
