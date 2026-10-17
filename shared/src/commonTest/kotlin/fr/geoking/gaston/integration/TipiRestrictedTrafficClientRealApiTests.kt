package fr.geoking.gaston.integration

import fr.geoking.gaston.api.traffic.Bbox
import fr.geoking.gaston.api.traffic.BisonFuteTrafficClient
import fr.geoking.gaston.api.traffic.DatexSituationParser
import fr.geoking.gaston.api.traffic.TipiRestrictedTrafficClient
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.api.traffic.intersects
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Live HTTP e2e for TIPI restricted Action B / Action C and open Bison Futé DATEX feeds.
 *
 * Requires `TIPI_USER` + `TIPI_PASSWORD` (from `local.properties` or env). Excluded from default CI
 * via `*RealApiTests*` filter; run on the station-load-integration workflow (or locally with
 * `-PrunRealApiTests=true`).
 */
class TipiRestrictedTrafficClientRealApiTests {

    private val client by lazy { createRealApiHttpClient() }

    private val credentials by lazy {
        requireIntegrationEnvs(
            "TIPI_USER" to "TIPI Action B/C Basic-auth username",
            "TIPI_PASSWORD" to "TIPI Action B/C Basic-auth password",
        )
    }

    @Test
    fun openBisonFute_fetchesParseableDatexXmlAndSupportsBboxFiltering() = runBlocking {
        val bison = BisonFuteTrafficClient(client = client)
        val xml = try {
            withTimeout(90_000) { bison.fetchDatexXml() }
        } catch (e: Exception) {
            fail("Open Bison Futé: ${e::class.simpleName}: ${e.message}")
        }

        assertNotNull(xml, "Open Bison Futé: fetchDatexXml returned null")
        assertTrue(xml.isNotBlank(), "Open Bison Futé: DATEX body is blank")
        assertTrue(
            xml.contains("SituationPublication", ignoreCase = true) ||
                xml.contains("situationRecord", ignoreCase = true),
            "Open Bison Futé: body does not look like DATEX SituationPublication (${xml.length} chars)",
        )

        val events = DatexSituationParser.parse(xml, nowMs = System.currentTimeMillis(), defaultRoadRef = "FR")
        println("Open Bison Futé: ${xml.length} chars, ${events.size} parsed event(s)")

        verifyBboxFilteringAndParkingEvents(events, label = "Open Bison Futé")
    }

    @Test
    fun actionC_fetchesParseableDatexXml() = runBlocking {
        fetchAndAssert(TipiRestrictedTrafficClient.ACTION_C_URL, label = "Action C")
    }

    @Test
    fun actionB_fetchesParseableDatexXml() = runBlocking {
        fetchAndAssert(TipiRestrictedTrafficClient.ACTION_B_URL, label = "Action B")
    }

    private suspend fun fetchAndAssert(contentUrl: String, label: String) {
        val tipi = TipiRestrictedTrafficClient(
            client = client,
            contentUrl = contentUrl,
            username = credentials.getValue("TIPI_USER"),
            password = credentials.getValue("TIPI_PASSWORD"),
        )
        assertTrue(tipi.hasCredentials, "$label: credentials should be non-blank")

        val xml = try {
            withTimeout(90_000) { tipi.fetchDatexXml() }
        } catch (e: Exception) {
            fail("$label: ${e::class.simpleName}: ${e.message}")
        }

        assertNotNull(xml, "$label: fetchDatexXml returned null (auth failure or empty upstream?)")
        assertTrue(xml.isNotBlank(), "$label: DATEX body is blank")
        assertTrue(
            xml.contains("SituationPublication", ignoreCase = true) ||
                xml.contains("situationRecord", ignoreCase = true),
            "$label: body does not look like DATEX SituationPublication (${xml.length} chars)",
        )

        val events = DatexSituationParser.parse(xml, nowMs = System.currentTimeMillis(), defaultRoadRef = "FR")
        println("$label: ${xml.length} chars, ${events.size} parsed event(s)")

        verifyBboxFilteringAndParkingEvents(events, label = label)
    }

    private fun verifyBboxFilteringAndParkingEvents(events: List<TrafficEvent>, label: String) {
        val parkingServiceEvents = events.filter {
            val msg = it.message.orEmpty().lowercase()
            msg.contains("servicearea") || msg.contains("parking") || msg.contains("aire")
        }
        println("$label: ${parkingServiceEvents.size} parking/service area event(s) detected out of ${events.size} total")

        val targetEvent = events.firstOrNull { it.bbox != null }
        if (targetEvent != null) {
            val eb = targetEvent.bbox!!
            // Query matching the target event's bbox (slightly expanded)
            val matchingQuery = Bbox(
                latMin = eb.latMin - 0.01,
                lonMin = eb.lonMin - 0.01,
                latMax = eb.latMax + 0.01,
                lonMax = eb.lonMax + 0.01,
            )
            val filteredMatching = events.filter { e -> e.bbox?.intersects(matchingQuery) == true }
            assertTrue(
                filteredMatching.any { it.sourceId == targetEvent.sourceId },
                "$label: target event ${targetEvent.sourceId} should be present in matching bbox query",
            )

            // Query completely disjoint from target event (e.g., far off in the Indian Ocean)
            val disjointQuery = Bbox(latMin = -50.0, lonMin = 70.0, latMax = -49.0, lonMax = 71.0)
            val filteredDisjoint = events.filter { e -> e.bbox?.intersects(disjointQuery) == true }
            assertTrue(
                filteredDisjoint.none { it.sourceId == targetEvent.sourceId },
                "$label: target event ${targetEvent.sourceId} should not be present in disjoint bbox query",
            )
        }
    }
}
