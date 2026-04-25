package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiSearchRequest
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

class DataGouvSemecourtTest {

    @Test
    fun testAuchanSemecourtParsing() = runBlocking {
        // Station 57280001 (Auchan Semécourt) with null brand from ODS API
        val mockBody = """
            {
                "results": [
                    {
                        "id": "57280001",
                        "adresse": "VOIE ROMAINE",
                        "ville": "SEMéCOURT",
                        "cp": "57280",
                        "marque": null,
                        "geom": { "lon": 6.15, "lat": 49.199 },
                        "prix_nom": "E10",
                        "prix_valeur": 1.969
                    },
                    {
                        "id": "57280001",
                        "adresse": "VOIE ROMAINE",
                        "ville": "SEMéCOURT",
                        "cp": "57280",
                        "marque": null,
                        "geom": { "lon": 6.15, "lat": 49.199 },
                        "prix_nom": "Gazole",
                        "prix_valeur": 2.31
                    }
                ]
            }
        """.trimIndent()

        val engine = MockEngine { _ ->
            respond(
                content = mockBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine)
        val provider = DataGouvProvider(client)

        val pois = provider.getGasStations(49.199, 6.15, null)

        assertEquals(1, pois.size, "Should merge records with same ID")
        val poi = pois[0]
        assertEquals("57280001", poi.id)
        assertEquals("Station SEMéCOURT", poi.name)
        assertEquals("VOIE ROMAINE, 57280, SEMéCOURT", poi.address)
        val prices = poi.fuelPrices
        assertNotNull(prices)
        assertEquals(2, prices.size)
        assertTrue(prices.any { it.fuelName == "E10" })
        assertTrue(prices.any { it.fuelName == "Gazole" })
    }
}
