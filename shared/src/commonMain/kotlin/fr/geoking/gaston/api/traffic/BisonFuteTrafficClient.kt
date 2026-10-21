package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream

/**
 * Fetches Bison Futé / TIPI open DATEX II SituationPublication for the French national
 * non-concessioned road network and parses/stores events into [TrafficRepository].
 */
class BisonFuteTrafficClient(
    private val client: HttpClient,
    private val trafficRepository: TrafficRepository? = null,
    private val contentUrl: String = DEFAULT_CONTENT_URL,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun fetchAndStoreTrafficEvents(): List<TrafficEvent> {
        val repository = trafficRepository
        if (repository != null && repository.isSourceFresh(SOURCE_ID, cacheTtlMs)) {
            return emptyList()
        }

        return try {
            val response = client.get(contentUrl)
            if (response.status == HttpStatusCode.OK) {
                val channel = response.bodyAsChannel()
                val inputStream = channel.toInputStream()
                val events = DatexSituationParser.parseStream(
                    inputStream = inputStream,
                    nowMs = nowMs(),
                    defaultRoadRef = "FR"
                )
                repository?.replaceEventsForSource(
                    sourceId = SOURCE_ID,
                    events = events,
                    fetchedAtMs = nowMs()
                )
                events
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchDatexXml(): String? {
        val events = fetchAndStoreTrafficEvents()
        return if (events.isNotEmpty() || (trafficRepository?.isSourceFresh(SOURCE_ID, cacheTtlMs) == true)) "OK" else null
    }

    suspend fun clearCache() {
        trafficRepository?.clear(SOURCE_ID)
    }

    companion object {
        const val SOURCE_ID = "bison_fute_rrn"
        const val DEFAULT_CONTENT_URL =
            "https://tipi.bison-fute.gouv.fr/bison-fute-ouvert/publicationsDIR/Evenementiel-DIR/grt/RRN/content.xml"
        const val DEFAULT_CACHE_TTL_MS = 60 * 60_000L // 1 hour
    }
}
