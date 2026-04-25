package fr.geoking.gaston.api.belgium

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

/**
 * Client for the official Belgian fuel price information from FPS Economy.
 * Scrapes prices from https://petrolprices.economie.fgov.be/petrolprices?locale=en
 */
class BelgiumPetrolPricesClient(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    suspend fun getFuelPrices(): List<FuelPrice> {
        val url = "$baseUrl/petrolprices/?locale=en"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Belgium Petrol Prices API error")
        }
        return parseFuelPrices(body)
    }

    private fun parseFuelPrices(html: String): List<FuelPrice> {
        val prices = mutableListOf<FuelPrice>()

        // Extract the date
        val dateRegex = """<span class="mylabel">(\d{2}\.\d{2}\.\d{4})</span>""".toRegex()
        val updatedAt = dateRegex.find(html)?.groupValues?.get(1)

        // Regex to match the rows in the table
        // Example: <td role="gridcell" ...>Euro Super 95 E10</td><td role="gridcell" ...>1.8480  euro/l</td>
        val rowRegex = """<td role="gridcell"[^>]*>([^<]+)</td><td role="gridcell"[^>]*>([\d\.]+)\s+euro/l</td>""".toRegex()

        rowRegex.findAll(html).forEach { match ->
            val name = match.groupValues[1].trim()
            val priceStr = match.groupValues[2]
            val price = priceStr.toDoubleOrNull() ?: return@forEach

            val normalizedName = when {
                name.contains("Euro Super 95 E10", ignoreCase = true) -> "SP95 E10"
                name.contains("Super Plus 98 E5", ignoreCase = true) -> "SP98"
                name.contains("Road Diesel B7", ignoreCase = true) -> "Gazole"
                else -> null
            }

            if (normalizedName != null) {
                prices.add(
                    FuelPrice(
                        fuelName = normalizedName,
                        price = price,
                        updatedAt = updatedAt
                    )
                )
            }
        }

        return prices
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://petrolprices.economie.fgov.be"
    }
}
