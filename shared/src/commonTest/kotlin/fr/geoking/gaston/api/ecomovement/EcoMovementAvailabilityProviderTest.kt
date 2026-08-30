package fr.geoking.gaston.api.ecomovement

import fr.geoking.gaston.api.belib.AvailabilityStatus
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

class EcoMovementAvailabilityProviderTest {

    @Test
    fun getAvailability_keepsOnlyNearby_andRespectsMaxFetch() = runBlocking {
        var listCalls = 0
        val engine = MockEngine { request ->
            listCalls++
            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
            val locations = when (offset) {
                0 -> (0 until 50).joinToString(",") { i ->
                    """{"id":"FAR-$i","coordinates":{"latitude":"40.0","longitude":"0.0"},"evses":[{"uid":"f$i","status":"AVAILABLE"}]}"""
                }
                50 -> """{"id":"NEAR-1","coordinates":{"latitude":"48.8566","longitude":"2.3522"},"evses":[{"uid":"n1","status":"AVAILABLE"},{"uid":"n2","status":"CHARGING"}]}"""
                else -> ""
            }
            respond(
                content = """{"data":[$locations],"status_code":1000}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = EcoMovementOcpiClient(HttpClient(engine), "key")
        val provider = EcoMovementAvailabilityProvider(
            client = client,
            radiusKm = 15,
            limit = 50,
            maxFetch = 100,
        )

        val result = provider.getAvailability(48.8566, 2.3522, 15)

        assertTrue(listCalls >= 1)
        assertTrue(listCalls <= 2, "should stop after maxFetch (100) with page size 50")
        assertEquals(2, result.size)
        assertTrue(result.all { it.stationId == "NEAR-1" })
        assertEquals(AvailabilityStatus.Available, result[0].status)
        assertEquals(AvailabilityStatus.Occupied, result[1].status)
    }

    @Test
    fun getAvailability_doesNotRetainFullCatalogAcrossQueries() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                {"data":[
                  {"id":"A","coordinates":{"latitude":"48.8566","longitude":"2.3522"},"evses":[{"uid":"1","status":"AVAILABLE"}]},
                  {"id":"B","coordinates":{"latitude":"52.0","longitude":"5.0"},"evses":[{"uid":"2","status":"AVAILABLE"}]}
                ],"status_code":1000}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val provider = EcoMovementAvailabilityProvider(
            EcoMovementOcpiClient(HttpClient(engine), "key"),
            radiusKm = 10,
        )
        val paris = provider.getAvailability(48.8566, 2.3522, 10)
        assertEquals(1, paris.size)
        assertEquals("A", paris.first().stationId)
    }
}
