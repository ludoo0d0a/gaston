package fr.geoking.gaston.integration

import fr.geoking.gaston.api.traffic.DatexSituationParser
import fr.geoking.gaston.api.traffic.TipiRestrictedTrafficClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Live HTTP e2e for TIPI restricted Action B / Action C DATEX feeds.
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
        // Feed may be empty outside peak disruption; parse success + DATEX shape is enough.
        println("$label: ${xml.length} chars, ${events.size} parsed event(s)")
    }
}
