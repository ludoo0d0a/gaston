package fr.geoking.gaston.api.datagouv

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataGouvNationalFuelClientTest {

    private val client = DataGouvNationalFuelClient(HttpClient())

    @Test
    fun parseAggregatedHistory_groupsByFuelAndDay() {
        val body = """
            {
              "total_count": 2,
              "results": [
                {
                  "prix_nom": "Gazole",
                  "year(prix_maj)": 2026,
                  "month(prix_maj)": 5,
                  "day(prix_maj)": 24,
                  "avg(prix_valeur)": 2.10
                },
                {
                  "prix_nom": "SP95",
                  "year(prix_maj)": 2026,
                  "month(prix_maj)": 5,
                  "day(prix_maj)": 23,
                  "avg(prix_valeur)": 2.05
                }
              ]
            }
        """.trimIndent()

        val parsed = client.parseAggregatedHistory(body)
        assertEquals(1, parsed["gazole"]?.size)
        assertEquals("2026-05-24", parsed["gazole"]?.single()?.day)
        assertEquals(2.10, parsed["gazole"]?.single()?.priceEurPerL)
        assertEquals("2026-05-23", parsed["sp95"]?.single()?.day)
    }

    @Test
    fun parseAggregatedHistory_skipsUnknownFuels() {
        val body = """
            {
              "results": [
                {
                  "prix_nom": "UnknownFuel",
                  "year(prix_maj)": 2026,
                  "month(prix_maj)": 5,
                  "day(prix_maj)": 20,
                  "avg(prix_valeur)": 9.99
                }
              ]
            }
        """.trimIndent()
        assertTrue(client.parseAggregatedHistory(body).isEmpty())
    }
}
