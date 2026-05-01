package fr.geoking.gaston.api.fastned

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
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import fr.geoking.gaston.shared.network.NetworkException

class FastnedOcpiClientTest {

    private fun buildClient(responseJson: String, status: HttpStatusCode = HttpStatusCode.OK): FastnedOcpiClient {
        val engine = MockEngine {
            respond(
                content = responseJson,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return FastnedOcpiClient(
            client = HttpClient(engine),
            apiKey = "test-api-key",
            baseUrl = "https://uk-public.api.fastned.nl/uk-public/ocpi/cpo/2.2.1"
        )
    }

    @Test
    fun listLocations_sendsXApiKeyHeader_andParsesOcpiEnvelope() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("test-api-key", request.headers["x-api-key"])
            assertEquals(true, request.url.encodedPath.endsWith("/locations"))
            respond(
                content = LOCATIONS_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = FastnedOcpiClient(HttpClient(engine), "test-api-key")

        val locations = client.listLocations(limit = 10, offset = 0)
        assertEquals(1, locations.size)
        val loc = locations.first()
        assertEquals("FN-UK-001", loc.id)
        assertEquals("Fastned Services M1 J1", loc.name)
        assertEquals("GB", loc.countryCode)
        assertNotNull(loc.coordinates)
        assertEquals("51.4985", loc.coordinates?.latitude)
        assertEquals("-0.1234", loc.coordinates?.longitude)
        assertEquals("Fastned", loc.operator?.name)
        assertEquals(2, loc.evses?.size)
    }

    @Test
    fun listLocations_parsesEvseConnectors() = runBlocking {
        val client = buildClient(LOCATIONS_RESPONSE)
        val locations = client.listLocations()
        val evses = locations.first().evses.orEmpty()
        assertEquals(2, evses.size)

        val firstEvse = evses.first()
        assertEquals("EVSE-1", firstEvse.uid)
        assertEquals("AVAILABLE", firstEvse.status)

        val connector = firstEvse.connectors?.first()
        assertNotNull(connector)
        assertEquals("IEC_62196_T2_COMBO", connector?.standard)
        assertEquals(150000, connector?.maxElectricPower)
    }

    @Test
    fun listTariffs_parsesPriceComponents() = runBlocking {
        val client = buildClient(TARIFFS_RESPONSE)
        val tariffs = client.listTariffs()
        assertEquals(1, tariffs.size)
        val tariff = tariffs.first()
        assertEquals("FASTNED-T1", tariff.id)
        assertEquals("GBP", tariff.currency)
        val priceComponent = tariff.elements?.firstOrNull()?.priceComponents?.firstOrNull()
        assertNotNull(priceComponent)
        assertEquals("ENERGY", priceComponent?.type)
        assertEquals(0.49, priceComponent?.price)
    }

    @Test
    fun listLocations_throwsNetworkException_onHttpError() = runBlocking {
        val client = buildClient("""{"status_code": 3000, "status_message": "Server Error", "data": []}""", HttpStatusCode.InternalServerError)
        assertFailsWith<NetworkException> {
            client.listLocations()
        }
        Unit
    }

    @Test
    fun listLocations_throwsNetworkException_onOcpiStatusCodeError() = runBlocking {
        val client = buildClient("""{"status_code": 2001, "status_message": "Invalid token", "data": []}""")
        assertFailsWith<NetworkException> {
            client.listLocations()
        }
        Unit
    }

    @Test
    fun mapOcpiStandard_mapsKnownStandards() {
        assertEquals("type_2", mapOcpiStandard("IEC_62196_T2"))
        assertEquals("combo_ccs", mapOcpiStandard("IEC_62196_T2_COMBO"))
        assertEquals("chademo", mapOcpiStandard("CHADEMO"))
        assertEquals("ef", mapOcpiStandard("DOMESTIC_F"))
        assertEquals("ef", mapOcpiStandard("DOMESTIC_E"))
        assertEquals("type_1", mapOcpiStandard("IEC_62196_T1"))
        assertEquals("tesla_s", mapOcpiStandard("TESLA_S"))
    }

    @Test
    fun mapOcpiStandard_lowercasesUnknownStandards() {
        assertEquals("some_unknown_standard", mapOcpiStandard("SOME_UNKNOWN_STANDARD"))
    }

    companion object {
        private val LOCATIONS_RESPONSE = """
            {
              "data": [
                {
                  "id": "FN-UK-001",
                  "name": "Fastned Services M1 J1",
                  "address": "M1 Junction 1, Services",
                  "city": "London",
                  "postal_code": "SW1A 1AA",
                  "country_code": "GB",
                  "coordinates": { "latitude": "51.4985", "longitude": "-0.1234" },
                  "operator": { "name": "Fastned" },
                  "evses": [
                    {
                      "uid": "EVSE-1",
                      "evse_id": "GB*FN*E001",
                      "status": "AVAILABLE",
                      "connectors": [
                        {
                          "id": "1",
                          "standard": "IEC_62196_T2_COMBO",
                          "format": "CABLE",
                          "power_type": "DC",
                          "max_voltage": 920,
                          "max_amperage": 500,
                          "max_electric_power": 150000
                        }
                      ]
                    },
                    {
                      "uid": "EVSE-2",
                      "evse_id": "GB*FN*E002",
                      "status": "CHARGING",
                      "connectors": [
                        {
                          "id": "1",
                          "standard": "IEC_62196_T2",
                          "format": "CABLE",
                          "power_type": "AC_3_PHASE",
                          "max_voltage": 400,
                          "max_amperage": 32,
                          "max_electric_power": 22000
                        }
                      ]
                    }
                  ]
                }
              ],
              "status_code": 1000,
              "status_message": "OK",
              "timestamp": "2026-04-28T10:00:00Z"
            }
        """.trimIndent()

        private val TARIFFS_RESPONSE = """
            {
              "data": [
                {
                  "id": "FASTNED-T1",
                  "currency": "GBP",
                  "elements": [
                    {
                      "price_components": [
                        { "type": "ENERGY", "price": 0.49, "vat": 5.0, "step_size": 1 }
                      ]
                    }
                  ]
                }
              ],
              "status_code": 1000,
              "status_message": "OK",
              "timestamp": "2026-04-28T10:00:00Z"
            }
        """.trimIndent()
    }
}
