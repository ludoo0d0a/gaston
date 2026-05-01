package fr.geoking.gaston.api.evpricesfr

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EvPricesFrClientTest {

    @Test
    fun `parses Electra FR dynamic range`() = runBlocking {
        val html = """
            <div>
              <span>Price varies with demand between</span>
              <span>0.39-0.61€</span>
              <span>/ kWh incl. VAT</span>
            </div>
        """.trimIndent()

        val engine = MockEngine { req ->
            val url = req.url.toString()
            respond(
                content = when {
                    url.contains("go-electra.com") -> html
                    // Minimal valid responses for other providers invoked by fetchBaselines()
                    url.contains("fastned.nl") -> "Nouveaux prix au kWh en France 0,61 € 0,55 € 0,43 €"
                    url.contains("allego.eu") -> "France Chargement ultra-rapide €0,730/kWh Chargement rapide €0,630/kWh Chargement régulier €0,600/kWh"
                    url.contains("totalenergies") -> "0,52 € TTC/kWh 0,62 € TTC/kWh"
                    url.contains("ionity.eu") -> "À partir de 0,39 €/kWh"
                    url.contains("raw.githubusercontent.com") -> """{"1":{"name":"Lille, France - Lesquin"}}"""
                    url.contains("api.github.com") -> """[{"commit":{"committer":{"date":"2026-04-28T00:00:00Z"}}}]"""
                    else -> ""
                },
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
            )
        }

        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val client = EvPricesFrClient(http)
        val baseline = client.run {
            // call private via public path: create a new instance pointing to our mock by using getText() URLs
            // We'll just call fetchBaselines() and read the Electra result.
            fetchBaselines().first { it.provider == EvPriceProvider.Electra }
        }

        assertEquals("kwh_range_dynamic", baseline.priceModel)
        assertEquals(0.39, baseline.values["dynamic_min_eur_per_kwh"])
        assertEquals(0.61, baseline.values["dynamic_max_eur_per_kwh"])
    }

    @Test
    fun `parses Allego FR ultra fast fast regular`() = runBlocking {
        val html = """
            France
            Chargement ultra-rapide
            €0,730/kWh
            Chargement rapide
            €0,630/kWh
            Chargement régulier
            €0,600/kWh
        """.trimIndent()

        val engine = MockEngine { req ->
            val url = req.url.toString()
            respond(
                content = when {
                    url.contains("allego.eu") -> html
                    url.contains("fastned.nl") -> "Nouveaux prix au kWh en France 0,61 € 0,55 € 0,43 €"
                    url.contains("go-electra.com") -> "Price varies with demand between 0.39-0.61€ / kWh"
                    url.contains("totalenergies") -> "0,52 € TTC/kWh 0,62 € TTC/kWh"
                    url.contains("ionity.eu") -> "À partir de 0,39 €/kWh"
                    url.contains("raw.githubusercontent.com") -> """{"1":{"name":"Lille, France - Lesquin"}}"""
                    url.contains("api.github.com") -> """[{"commit":{"committer":{"date":"2026-04-28T00:00:00Z"}}}]"""
                    else -> ""
                },
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=utf-8")
            )
        }

        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val client = EvPricesFrClient(http)
        val baseline = client.fetchBaselines().first { it.provider == EvPriceProvider.Allego }

        assertEquals(0.600, baseline.values["regular_eur_per_kwh"])
        assertEquals(0.630, baseline.values["fast_eur_per_kwh"])
        assertEquals(0.730, baseline.values["ultra_fast_eur_per_kwh"])
    }
}

