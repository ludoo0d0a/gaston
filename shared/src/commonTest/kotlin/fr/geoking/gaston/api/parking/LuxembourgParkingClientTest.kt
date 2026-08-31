package fr.geoking.gaston.api.parking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LuxembourgParkingClientTest {

    private val sampleJson = """
        {
            "last_build_date": "Mon, 31 Aug 2026 20:04:00 +0200",
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
                        "quartier": {
                            "fr": "Kirchberg",
                            "en": "Kirchberg"
                        },
                        "latitude": 49.630363,
                        "longitude": 6.157175,
                        "entree": [
                            {
                                "adresse": "Boulevard Konrad Adenauer"
                            }
                        ]
                    },
                    "paiement": {
                        "tarif": {
                            "fr": "Payant",
                            "en": "Paid"
                        }
                    },
                    "ouverture": {
                        "fr": "24/24h",
                        "en": "24/7"
                    }
                },
                "14": {
                    "id": 14,
                    "titre": "Auchan",
                    "total": 500,
                    "actuel": 0,
                    "ouvert": true,
                    "complet": true,
                    "panne": false,
                    "localisation": {
                        "latitude": 49.633296,
                        "longitude": 6.159885
                    }
                }
            }
        }
    """.trimIndent()

    @Test
    fun parseParkings_correctlyParsesFields() {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = LuxembourgParkingClient(HttpClient(mockEngine))

        val parkings = client.parseParkings(sampleJson)
        assertEquals(2, parkings.size)

        val adenauer = parkings.find { it.id == "20" }
        assertNotNull(adenauer)
        assertEquals("Adenauer", adenauer.title)
        assertEquals(49.630363, adenauer.latitude)
        assertEquals(6.157175, adenauer.longitude)
        assertEquals(406, adenauer.totalCapacity)
        assertEquals(350, adenauer.availableSpaces)
        assertEquals("open", adenauer.status)
        assertEquals("Boulevard Konrad Adenauer", adenauer.address)
        assertEquals("Kirchberg", adenauer.quartier)
        assertEquals("Paid", adenauer.priceInfo)
        assertEquals("24/7", adenauer.openingHours)

        val auchan = parkings.find { it.id == "14" }
        assertNotNull(auchan)
        assertEquals("full", auchan.status)
        assertEquals(0, auchan.availableSpaces)
    }

    @Test
    fun getParkings_fetchesFromNetwork() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals("https://feed.vdl.lu/circulation/parking/feed.json", request.url.toString())
            respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = LuxembourgParkingClient(HttpClient(mockEngine))
        val list = client.getParkings()
        assertEquals(2, list.size)
    }
}
