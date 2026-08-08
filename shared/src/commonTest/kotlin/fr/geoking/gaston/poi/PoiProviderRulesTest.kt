package fr.geoking.gaston.poi

import fr.geoking.gaston.api.tankerkoenig.GermanyTankerkoenigProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoiProviderRulesTest {

    @Test
    fun testCountryRule_GermanyMatchesBerlinButNotParis() {
        val rules = PoiProviderRules(countries = setOf("DE"))

        // Berlin, Germany (approx 52.52, 13.40)
        assertTrue(rules.isSatisfiedBy(52.52, 13.40))

        // Paris, France (approx 48.8566, 2.3522)
        assertFalse(rules.isSatisfiedBy(48.8566, 2.3522))
    }

    @Test
    fun testCountryRule_MultipleCountriesMatch() {
        val rules = PoiProviderRules(countries = setOf("FR", "BE"))

        // Paris, France
        assertTrue(rules.isSatisfiedBy(48.8566, 2.3522))

        // Brussels, Belgium (approx 50.85, 4.35)
        assertTrue(rules.isSatisfiedBy(50.85, 4.35))

        // Berlin, Germany
        assertFalse(rules.isSatisfiedBy(52.52, 13.40))
    }

    @Test
    fun testGermanyTankerkoenigProvider_OutsideGermanyShortCircuits() = runBlocking {
        // Mock engine that would succeed if queried, but should NOT be queried
        var engineCalled = false
        val engine = MockEngine { _ ->
            engineCalled = true
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine)
        val provider = GermanyTankerkoenigProvider(client, apiKey = "demo-test-key")

        // Query in Paris (France)
        val pois = provider.getGasStations(48.8566, 2.3522)

        assertFalse(engineCalled, "Engine should not be queried for coordinates outside Germany")
        assertTrue(pois.isEmpty())
        assertFalse(provider.shouldQuery(48.8566, 2.3522))
    }

    @Test
    fun testGermanyTankerkoenigProvider_InsideGermanySucceeds() = runBlocking {
        val mockJson = """
            {
              "ok": true,
              "stations": [
                {
                  "id": "abc-123",
                  "name": "Shell Berlin",
                  "lat": 52.52,
                  "lng": 13.40,
                  "diesel": 1.759
                }
              ]
            }
        """.trimIndent()

        var engineCalled = false
        val engine = MockEngine { _ ->
            engineCalled = true
            respond(
                content = mockJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine)
        val provider = GermanyTankerkoenigProvider(client, apiKey = "demo-test-key")

        // Query in Berlin (Germany)
        assertTrue(provider.shouldQuery(52.52, 13.40))
        val pois = provider.getGasStations(52.52, 13.40)

        assertTrue(engineCalled, "Engine should be queried for coordinates inside Germany")
        assertEquals(1, pois.size)
        assertEquals("Shell Berlin", pois[0].name)
    }
}
