package fr.geoking.gaston.api.parking

import fr.geoking.gaston.parking.ParkingRegion
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuxembourgParkingProviderTest {

    private val sampleJson = """
        {
            "parking": {
                "20": {
                    "id": 20,
                    "titre": "Adenauer",
                    "total": 406,
                    "actuel": 350,
                    "ouvert": true,
                    "complet": false,
                    "panne": false,
                    "localisation": {
                        "latitude": 49.630363,
                        "longitude": 6.157175
                    }
                }
            }
        }
    """.trimIndent()

    @Test
    fun covers_luxembourgCoordinates_returnsTrue() {
        val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
        val client = LuxembourgParkingClient(HttpClient(mockEngine))
        val provider = LuxembourgParkingProvider(client)

        // Luxembourg City
        assertTrue(provider.covers(49.6116, 6.1319))
    }

    @Test
    fun covers_parisCoordinates_returnsFalse() {
        val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
        val client = LuxembourgParkingClient(HttpClient(mockEngine))
        val provider = LuxembourgParkingProvider(client)

        // Paris
        assertFalse(provider.covers(48.8566, 2.3522))
    }

    @Test
    fun servedRegions_containsLuxembourg() {
        val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
        val client = LuxembourgParkingClient(HttpClient(mockEngine))
        val provider = LuxembourgParkingProvider(client)

        assertEquals(setOf(ParkingRegion.Luxembourg), provider.servedRegions())
    }

    @Test
    fun getParkingNearby_returnsPoiWithinRadius() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = LuxembourgParkingClient(HttpClient(mockEngine))
        val provider = LuxembourgParkingProvider(client)

        // Query near Adenauer (49.630363, 6.157175) with 5km radius
        val pois = provider.getParkingNearby(49.6300, 6.1570, radiusMeters = 5000)
        assertEquals(1, pois.size)

        val poi = pois.first()
        assertEquals("luxembourg_vdl_20", poi.id)
        assertEquals("Adenauer", poi.name)
        assertEquals(406, poi.capacity)
        assertEquals(350, poi.available)
        assertEquals("luxembourg_vdl", poi.providerId)
        assertEquals("open", poi.state)

        // Query far away with 100m radius
        val emptyPois = provider.getParkingNearby(49.0000, 6.0000, radiusMeters = 100)
        assertTrue(emptyPois.isEmpty())
    }
}
