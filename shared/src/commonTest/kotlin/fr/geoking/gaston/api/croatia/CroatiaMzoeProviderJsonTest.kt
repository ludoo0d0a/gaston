package fr.geoking.gaston.api.croatia

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CroatiaMzoeProviderJsonTest {

    @Test
    fun parsesStringCoordinatesFromMzoeJson() = runBlocking {
        val snippet = """
            {
              "postajas": [{
                "id": 1223,
                "naziv": "Test",
                "adresa": "Addr",
                "mjesto": "Zagreb",
                "lat": "15.748905",
                "long": "45.684651",
                "obveznik_id": 1,
                "cjenici": [{"cijena": 1.38, "gorivo_id": 1011}]
              }],
              "gorivos": [{"id": 1011, "naziv": "Eurosuper 95", "vrsta_goriva_id": 2}],
              "obvezniks": [{"id": 1, "naziv": "Test d.o.o."}]
            }
        """.trimIndent()
        val client = HttpClient(MockEngine {
            respond(
                snippet,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val data = CroatiaMzoeClient(client).fetchData()
        assertEquals(1, data.postajas.size)
        assertEquals(45.684651, data.postajas[0].long)
        val provider = CroatiaMzoeProvider(client, radiusKm = 50, limit = 10)
        val pois = provider.getGasStations(45.8150, 15.9819)
        assertTrue(pois.isNotEmpty(), "expected stations near Zagreb")
    }
}
