package fr.geoking.gaston.api.no

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DrivstoffAppenProviderLocationTest {

    @Test
    fun parsesLatitudeLongitudeLocationFields() = runBlocking {
        val body = """
            [{
              "id": "abc",
              "name": "Fredensborg",
              "location": {
                "latitude": 59.9208,
                "longitude": 10.7510
              },
              "prices": {
                "diesel_price": 18.14,
                "last_updated": "2026-05-18T13:00:21Z"
              }
            }]
        """.trimIndent()
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.OK) })
        val provider = DrivstoffAppenProvider(client, country = "Norway", countryIso2 = "NO")

        val pois = provider.getGasStations(59.9139, 10.7522)

        assertEquals(1, pois.size)
        assertEquals(59.9208, pois[0].latitude)
        assertEquals(10.7510, pois[0].longitude)
    }
}
