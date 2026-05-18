package fr.geoking.gaston.api.tankerkoenig

import fr.geoking.gaston.poi.PoiCategory
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

class GermanyTankerkoenigProviderTest {

    @Test
    fun testGermanyTankerkoenigProvider_Success() = runBlocking {
        val mockJson = """
            {
              "ok": true,
              "status": "ok",
              "stations": [
                {
                  "id": "abc-123",
                  "name": "Shell Berlin",
                  "brand": "Shell",
                  "street": "Musterstr.",
                  "houseNumber": "10",
                  "postCode": 10115,
                  "place": "Berlin",
                  "lat": 52.52,
                  "lng": 13.40,
                  "diesel": 1.759,
                  "e5": 1.859,
                  "e10": 1.799,
                  "isOpen": true
                }
              ]
            }
        """.trimIndent()

        val engine = MockEngine { _ ->
            respond(
                content = mockJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine)
        val provider = GermanyTankerkoenigProvider(client, apiKey = "demo-test-key")

        val pois = provider.getGasStations(52.52, 13.40)

        assertEquals(1, pois.size)
        val station = pois[0]
        assertEquals("Shell Berlin", station.name)
        assertEquals("tankerkoenig:abc-123", station.id)
        assertEquals(3, station.fuelPrices?.size)
        assertEquals(1.759, station.fuelPrices?.find { it.fuelName == "Gazole" }?.price)
        assertEquals("Tankerkönig (Germany)", station.source)
    }
}
