package fr.geoking.gaston.api.belgium

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BelgiumPetrolPricesClientTest {

    private val mockHtml = """
        <html xml:lang="fr" lang="fr"><head id="j_id_3">...</head><body><div id="petrolTable" class="ui-datatable ui-widget" style="width:100% !important;font-family: Arial, sans-serif;border:none!important;border:0!important;"><div class="ui-datatable-header ui-widget-header ui-corner-top"><span class="mylabel">Petroleum products: rate applicable from </span><span class="mylabel">10.04.2026</span>
                    <br /></div><div class="ui-datatable-tablewrapper"><table role="grid"><thead id="petrolTable_head"><tr role="row">...</tr></thead><tbody id="petrolTable_data" class="ui-datatable-data ui-widget-content"><tr data-ri="0" class="ui-widget-content ui-datatable-even first-row" role="row"><td role="gridcell" style="width:50%;border:none!important;font-weight: bold;">Euro Super 95 E10</td><td role="gridcell" style="text-align: center;width:25%;border:none!important;">1.8480  euro/l</td><td role="gridcell" style="text-align: center;width:25%;border:none!important;"><img src="img/fleche-bas.png" alt="---" /></td></tr><tr data-ri="1" class="ui-widget-content ui-datatable-odd second-row" role="row"><td role="gridcell" style="width:50%;border:none!important;font-weight: bold;">Super Plus 98 E5</td><td role="gridcell" style="text-align: center;width:25%;border:none!important;">1.9210  euro/l</td><td role="gridcell" style="text-align: center;width:25%;border:none!important;"><img src="img/fleche-bas.png" alt="---" /></td></tr><tr data-ri="2" class="ui-widget-content ui-datatable-even first-row" role="row"><td role="gridcell" style="width:50%;border:none!important;font-weight: bold;">Road Diesel B7</td><td role="gridcell" style="text-align: center;width:25%;border:none!important;">2.1800  euro/l</td><td role="gridcell" style="text-align: center;width:25%;border:none!important;"><img src="img/fleche-bas.png" alt="---" /></td></tr>...</tbody></table></div></div>...</body>
        </html>
    """.trimIndent()

    @Test
    fun testParseFuelPrices() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = mockHtml,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "text/html")
            )
        }
        val client = HttpClient(mockEngine)
        val belgiumClient = BelgiumPetrolPricesClient(client)

        val prices = belgiumClient.getFuelPrices()

        assertEquals(3, prices.size)

        val sp95 = prices.find { it.fuelName == "SP95 E10" }
        assertEquals(1.8480, sp95?.price)
        assertEquals("10.04.2026", sp95?.updatedAt)

        val sp98 = prices.find { it.fuelName == "SP98" }
        assertEquals(1.9210, sp98?.price)
        assertEquals("10.04.2026", sp98?.updatedAt)

        val gazole = prices.find { it.fuelName == "Gazole" }
        assertEquals(2.1800, gazole?.price)
        assertEquals("10.04.2026", gazole?.updatedAt)
    }
}
