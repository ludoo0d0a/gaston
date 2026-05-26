package fr.geoking.gaston.api.us

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
import kotlin.test.assertTrue

class EiaPetroleumClientTest {

    @Test
    fun getStateRetailPrices_parsesLatestWeeklyRows() = runBlocking {
        val mockJson = """
            {
              "response": {
                "data": [
                  {
                    "period": "2026-05-18",
                    "product": "EPM0",
                    "product-name": "Total Gasoline",
                    "value": "3.123",
                    "units": "${'$'}/GAL"
                  },
                  {
                    "period": "2026-05-18",
                    "product": "EPD2D",
                    "product-name": "No 2 Diesel",
                    "value": "3.456",
                    "units": "${'$'}/GAL"
                  },
                  {
                    "period": "2026-05-11",
                    "product": "EPM0",
                    "value": "3.100"
                  }
                ]
              }
            }
        """.trimIndent()

        val engine = MockEngine { _ ->
            respond(
                content = mockJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = EiaPetroleumClient(HttpClient(engine))
        val prices = client.getStateRetailPrices("SCA", "test-key")

        assertEquals(2, prices.size)
        assertEquals(3.123, prices.find { it.fuelName == "SP95" }?.price)
        assertEquals(3.456, prices.find { it.fuelName == "Gazole" }?.price)
        assertEquals("2026-05-18", prices.first().updatedAt)
    }

    @Test
    fun getStateRetailPrices_blankApiKey_returnsEmpty() = runBlocking {
        val client = EiaPetroleumClient(HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }))
        assertEquals(emptyList(), client.getStateRetailPrices("SCA", ""))
    }

    @Test
    fun buildDataUrl_constructsCorrectUrl() = runBlocking {
        var capturedUrl = ""
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = "{\"response\":{\"data\":[]}}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = EiaPetroleumClient(HttpClient(engine))
        client.getStateRetailPrices("SNY", "key-123")

        // Expected order might vary but parameters must be present.
        // We check the base and key parameters.
        assertTrue(capturedUrl.startsWith("https://api.eia.gov/v2/petroleum/pri/gnd/data"))
        assertTrue(capturedUrl.contains("api_key=key-123"))
        assertTrue(capturedUrl.contains("facets%5Bduoarea%5D%5B%5D=SNY"))
        assertTrue(capturedUrl.contains("facets%5Bproduct%5D%5B%5D=EPM0"))
        assertTrue(capturedUrl.contains("facets%5Bproduct%5D%5B%5D=EPD2D"))
    }
}
