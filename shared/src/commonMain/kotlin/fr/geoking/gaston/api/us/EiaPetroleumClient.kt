package fr.geoking.gaston.api.us

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * EIA Open Data API v2 — retail gasoline & diesel (weekly, $/gal) from petroleum/pri/gnd.
 *
 * @see <a href="https://www.eia.gov/opendata/browser/petroleum/pri">EIA petroleum prices</a>
 */
class EiaPetroleumClient(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Latest weekly retail prices for [duoArea] (state-level EIA code, e.g. SCA).
     * Returns empty list when [apiKey] is blank or the API returns no rows.
     */
    suspend fun getStateRetailPrices(duoArea: String, apiKey: String): List<FuelPrice> {
        if (apiKey.isBlank()) return emptyList()

        val url = buildDataUrl(
            apiKey = apiKey,
            duoArea = duoArea,
            products = RETAIL_PRODUCTS,
        )
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "EIA petroleum/pri/gnd error")
        }

        val parsed = json.decodeFromString<EiaDataResponse>(body)
        val rows = parsed.response?.data.orEmpty()
        if (rows.isEmpty()) return emptyList()

        val latestByProduct = linkedMapOf<String, EiaPriceRow>()
        for (row in rows) {
            val product = row.product ?: continue
            val existing = latestByProduct[product]
            if (existing == null || (row.period ?: "") > (existing.period ?: "")) {
                latestByProduct[product] = row
            }
        }

        return latestByProduct.mapNotNull { (product, row) ->
            val fuelName = PRODUCT_FUEL_NAMES[product] ?: return@mapNotNull null
            val price = row.value?.toDoubleOrNull() ?: return@mapNotNull null
            FuelPrice(
                fuelName = fuelName,
                price = price,
                updatedAt = row.period,
            )
        }
    }

    private fun buildDataUrl(apiKey: String, duoArea: String, products: List<String>): String {
        val builder = URLBuilder(baseUrl)
        builder.parameters.append("api_key", apiKey)
        builder.parameters.append("frequency", "weekly")
        builder.parameters.append("data[0]", "value")
        builder.parameters.append("length", "8")
        builder.parameters.append("sort[0][column]", "period")
        builder.parameters.append("sort[0][direction]", "desc")
        builder.parameters.append("facets[duoarea][]", duoArea)
        builder.parameters.append("facets[process][]", "PTE")
        for (product in products) {
            builder.parameters.append("facets[product][]", product)
        }
        return builder.buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.eia.gov/v2/petroleum/pri/gnd/data/"

        /** Total gasoline (all grades) and No. 2 diesel — retail sales (PTE). */
        private val RETAIL_PRODUCTS = listOf("EPM0", "EPD2D")

        private val PRODUCT_FUEL_NAMES = mapOf(
            "EPM0" to "SP95",
            "EPD2D" to "Gazole",
        )
    }
}

@Serializable
private data class EiaDataResponse(
    val response: EiaDataBody? = null,
)

@Serializable
private data class EiaDataBody(
    val data: List<EiaPriceRow> = emptyList(),
)

@Serializable
private data class EiaPriceRow(
    val period: String? = null,
    val product: String? = null,
    @SerialName("product-name") val productName: String? = null,
    val value: String? = null,
    val units: String? = null,
)
