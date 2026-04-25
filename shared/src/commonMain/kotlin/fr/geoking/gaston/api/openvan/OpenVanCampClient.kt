package fr.geoking.gaston.api.openvan

import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for [OpenVan.camp](https://openvan.camp) public fuel price API (CC BY 4.0).
 * Weekly retail averages from official sources (e.g. EU Weekly Oil Bulletin for Luxembourg).
 */
class OpenVanCampClient(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchPricesResponse(): OpenVanFuelPricesResponse {
        val response = client.get("$baseUrl/api/fuel/prices")
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "OpenVan.camp API error: ${body.take(500)}")
        }
        return json.decodeFromString<OpenVanFuelPricesResponse>(body)
    }

    /** Country entry from the API, or null if missing / unsuccessful. */
    suspend fun getCountryEntry(isoCode: String): OpenVanCountryEntry? {
        val root = fetchPricesResponse()
        if (!root.success) return null
        val key = isoCode.uppercase()
        return root.data?.get(key)
    }

    /**
     * Reference [FuelPrice] list for the given country, using names aligned with [fr.geoking.gaston.poi.MapPoiFilter].
     */
    suspend fun getReferenceFuelPrices(isoCode: String): List<FuelPrice>? =
        getCountryEntry(isoCode)?.toFuelPrices()

    /**
     * Reference [FuelPrice] list for Luxembourg (ISO LU), using names aligned with [fr.geoking.gaston.poi.MapPoiFilter].
     */
    @Deprecated("Use getReferenceFuelPrices instead", ReplaceWith("getReferenceFuelPrices(\"LU\")"))
    suspend fun getLuxembourgFuelPrices(): List<FuelPrice>? =
        getReferenceFuelPrices("LU")

    companion object {
        const val DEFAULT_BASE_URL = "https://openvan.camp"
    }
}

@Serializable
data class OpenVanFuelPricesResponse(
    val success: Boolean = false,
    val data: Map<String, OpenVanCountryEntry>? = null
)

@Serializable
data class OpenVanCountryEntry(
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val currency: String? = null,
    val prices: OpenVanPriceBlock? = null,
    @SerialName("fetched_at") val fetchedAt: String? = null,
    val source: String? = null
)

@Serializable
data class OpenVanPriceBlock(
    val gasoline: Double? = null,
    val diesel: Double? = null,
    val lpg: Double? = null,
    val e85: Double? = null,
    val premium: Double? = null,
    @SerialName("gasoline_premium") val gasolinePremium: Double? = null
)

/** Map API block to in-app fuel rows (EUR/l). */
fun OpenVanCountryEntry.toFuelPrices(): List<FuelPrice> {
    val p = prices ?: return emptyList()
    val updated = fetchedAt
    return buildList {
        p.gasoline?.let {
            add(FuelPrice(fuelName = "SP95 E10", price = it, updatedAt = updated, outOfStock = false))
        }
        p.diesel?.let {
            add(FuelPrice(fuelName = "Gazole", price = it, updatedAt = updated, outOfStock = false))
        }
        p.lpg?.let {
            add(FuelPrice(fuelName = "GPLc", price = it, updatedAt = updated, outOfStock = false))
        }
        p.e85?.let {
            add(FuelPrice(fuelName = "E85", price = it, updatedAt = updated, outOfStock = false))
        }
        (p.gasolinePremium ?: p.premium)?.let {
            add(FuelPrice(fuelName = "SP98", price = it, updatedAt = updated, outOfStock = false))
        }
    }
}
