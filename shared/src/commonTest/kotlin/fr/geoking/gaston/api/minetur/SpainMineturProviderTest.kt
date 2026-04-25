package fr.geoking.gaston.api.minetur

import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiSearchRequest
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

class SpainMineturProviderTest {

    @Test
    fun testSpainMineturProvider_Success() = runBlocking {
        val mockJson = """
            {
              "Fecha": "03/04/2026 10:16:03",
              "ListaEESSPrecio": [
                {
                  "IDEESS": "1234",
                  "Rotulo": "REPSOL",
                  "Direccion": "CALLE MAYOR, 1",
                  "Latitud": "40,4167",
                  "Longitud (WGS84)": "-3,7033",
                  "Precio Gasolina 95 E5": "1,659",
                  "Precio Gasóleo A": "1,559",
                  "Precio Gasolina 98 E5": "1,759"
                }
              ],
              "ResultadoConsulta": "OK"
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
        val provider = SpainMineturProvider(client, radiusKm = 100)

        // Search near Madrid center (approx 40.4, -3.7)
        val pois = provider.getGasStations(40.41, -3.70)

        assertEquals(1, pois.size)
        val repsol = pois[0]
        assertEquals("REPSOL", repsol.name)
        assertEquals("minetur:1234", repsol.id)
        assertEquals(40.4167, repsol.latitude)
        assertEquals(-3.7033, repsol.longitude)
        assertEquals(3, repsol.fuelPrices?.size)

        val sp95 = repsol.fuelPrices?.find { it.fuelName == "SP95 E5" }
        assertEquals(1.659, sp95?.price)

        assertEquals("Minetur (Spain)", repsol.source)
    }

    @Test
    fun testSpainMineturProvider_Filtering() = runBlocking {
        val mockJson = """
            {
              "ListaEESSPrecio": [
                {
                  "IDEESS": "1",
                  "Rotulo": "Madrid Near",
                  "Latitud": "40,41",
                  "Longitud (WGS84)": "-3,70"
                },
                {
                  "IDEESS": "2",
                  "Rotulo": "Barcelona Far",
                  "Latitud": "41,38",
                  "Longitud (WGS84)": "2,17"
                }
              ]
            }
        """.trimIndent()

        val engine = MockEngine { _ ->
            respond(mockJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = HttpClient(engine)
        val provider = SpainMineturProvider(client, radiusKm = 50)

        // Search in Madrid
        val pois = provider.getGasStations(40.41, -3.70)
        assertEquals(1, pois.size)
        assertEquals("Madrid Near", pois[0].name)
    }
}
