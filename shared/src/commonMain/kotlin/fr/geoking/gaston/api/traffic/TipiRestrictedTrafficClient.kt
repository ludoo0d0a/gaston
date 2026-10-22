package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Fetches TIPI restricted DATEX SituationPublication feeds (Action B / Action C) with HTTP Basic auth
 * and stores/parses events into [TrafficRepository].
 */
class TipiRestrictedTrafficClient(
    private val client: HttpClient,
    private val contentUrl: String,
    private val username: String,
    private val password: String,
    private val trafficRepository: TrafficRepository? = null,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    val sourceId: String = sourceIdForUrl(contentUrl),
) {
    private val fetchMutex = Mutex()

    val hasCredentials: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchAndStoreTrafficEvents(): List<TrafficEvent> {
        if (!hasCredentials) return emptyList()

        val repository = trafficRepository
        if (repository != null && repository.isSourceFresh(sourceId, cacheTtlMs)) {
            return emptyList()
        }

        val basic = Base64.Default.encode("$username:$password".encodeToByteArray())
        return fetchMutex.withLock {
            if (repository != null && repository.isSourceFresh(sourceId, cacheTtlMs)) {
                return@withLock emptyList()
            }
            try {
                val response = client.get(contentUrl) {
                    header(HttpHeaders.Authorization, "Basic $basic")
                }
                if (response.status == HttpStatusCode.OK) {
                    val channel = response.bodyAsChannel()
                    val inputStream = channel.toInputStream()
                    val events = DatexSituationParser.parseStream(
                        inputStream = inputStream,
                        nowMs = nowMs(),
                        defaultRoadRef = "FR"
                    )
                    repository?.replaceEventsForSource(
                        sourceId = sourceId,
                        events = events,
                        fetchedAtMs = nowMs()
                    )
                    events
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
        }
    }

    suspend fun fetchDatexXml(): String? {
        val events = fetchAndStoreTrafficEvents()
        return if (events.isNotEmpty() || (trafficRepository?.isSourceFresh(sourceId, cacheTtlMs) == true)) "OK" else null
    }

    suspend fun clearCache() {
        trafficRepository?.clear(sourceId)
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 60 * 60_000L // 1 hour
        const val ACTION_C_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-C/content.xml"
        const val ACTION_B_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-B/content.xml"
        const val SOURCE_ID_ACTION_C = "tipi_action_c"
        const val SOURCE_ID_ACTION_B = "tipi_action_b"

        fun sourceIdForUrl(url: String): String = when (url) {
            ACTION_C_URL -> SOURCE_ID_ACTION_C
            ACTION_B_URL -> SOURCE_ID_ACTION_B
            else -> "tipi_" + url.hashCode().toUInt().toString(16)
        }
    }
}
