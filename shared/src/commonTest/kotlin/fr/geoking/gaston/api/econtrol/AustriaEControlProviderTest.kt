package fr.geoking.gaston.api.econtrol

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

class AustriaEControlProviderTest {

    @Test
    fun testAustriaEControlProvider_Success() = runBlocking {
        val mockJson = """
            [
              {
                "id": 123,
                "name": "Turmöl Wien",
                "location": {
                  "address": "Hauptstr. 1",
                  "postalCode": "1010",
                  "city": "Wien",
                  "latitude": 48.21,
                  "longitude": 16.37
                },
                "prices": [
                  { "fuelType": "DIE", "amount": 1.689, "label": "Diesel" },
                  { "fuelType": "SUP", "amount": 1.749, "label": "Super" }
                ]
              }
            ]
        """.trimIndent()

        val engine = MockEngine { _ ->
            respond(
                content = mockJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine)
        val provider = AustriaEControlProvider(client)

        val pois = provider.getGasStations(48.21, 16.37)

        assertEquals(1, pois.size)
        val station = pois[0]
        assertEquals("Turmöl Wien", station.name)
        assertEquals("econtrol:123", station.id)
        assertEquals(2, station.fuelPrices?.size)
        assertEquals(1.689, station.fuelPrices?.find { it.fuelName == "Gazole" }?.price)
        assertEquals("E-Control (Austria)", station.source)
    }
}
