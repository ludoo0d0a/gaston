package fr.geoking.gaston.api.gas

import fr.geoking.gaston.api.datagouv.DataGouvPrice
import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the Gas API (gas-api.ovh), which wraps French government open data
 * from [prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/) / [data.gouv.fr](https://www.data.gouv.fr/fr/datasets/prix-des-carburants-en-france-flux-instantane-v2-amelioree/).
 *
 * No authentication required. Base URL: https://gas-api.ovh
 */
class GasApiClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://gas-api.ovh"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Request body for POST /api/station-search.
     */
    @Serializable
    data class StationSearchRequest(
        val latitude: Double,
        val longitude: Double,
        val radius: Int,
        val limit: Int = 20,
        val offset: Int = 0,
        val brandId: String? = null,
        val serviceId: String? = null
    ) {
        init {
            require(radius in 1..100) { "radius must be between 1 and 100 km" }
            require(limit in 5..100) { "limit must be between 5 and 100" }
        }
    }

    /**
     * Search for gas stations near a location. Returns stations with fuel prices.
     */
    suspend fun searchStations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 10,
        limit: Int = 20,
        offset: Int = 0
    ): List<GasApiStation> {
        val request = StationSearchRequest(
            latitude = latitude,
            longitude = longitude,
            radius = radiusKm.coerceIn(1, 100),
            limit = limit.coerceIn(5, 100),
            offset = offset.coerceAtLeast(0)
        )

        val response = client.post("$baseUrl/api/station-search") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Gas API error: $body")
        }

        return parseStationsResponse(body)
    }

    /**
     * Parse API response: accepts root array, single station object, or "hydra:member" / "data" array.
     */
    private fun parseStationsResponse(body: String): List<GasApiStation> {
        val element = json.parseToJsonElement(body)

        when (element) {
            is JsonArray -> return element.mapNotNull { parseStationElement(it) }
            is JsonObject -> {
                val array: JsonArray? = when {
                    element["hydra:member"] != null -> element["hydra:member"]?.jsonArray
                    element["data"] != null -> element["data"]?.jsonArray
                    element["stations"] != null -> element["stations"]?.jsonArray
                    element["@graph"] != null -> element["@graph"]?.jsonArray
                    else -> null
                }
                if (array != null) {
                    return array.mapNotNull { parseStationElement(it) }
                }
                parseStationElement(element)?.let { return listOf(it) }
            }
            else -> { }
        }
        return emptyList()
    }

    private fun parseStationElement(element: JsonElement): GasApiStation? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val latStr = obj["latitude"]?.jsonPrimitive?.content ?: return null
        val lngStr = obj["longitude"]?.jsonPrimitive?.content ?: return null
        val lat = latStr.toDoubleOrNull() ?: return null
        val lng = lngStr.toDoubleOrNull() ?: return null

        val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Station"
        val address = obj["address"]?.jsonPrimitive?.content
        val city = obj["city"]?.jsonPrimitive?.content
        val postCode = obj["postCode"]?.jsonPrimitive?.content
        val fullAddress = listOfNotNull(address, postCode, city).filter { it.isNotBlank() }.joinToString(", ")

        val brandName = when (val b = obj["brand"]) {
            is JsonObject -> b["name"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> b.content.takeIf { it.isNotBlank() && it != "null" }
            else -> null
        }

        val prices = mutableListOf<DataGouvPrice>()
        obj["prices"]?.jsonArray?.forEach { priceEl ->
            val priceObj = priceEl.jsonObject
            val gas = priceObj["gas"]?.jsonObject
            val gasName = gas?.get("name")?.jsonPrimitive?.content ?: "Fuel"
            val price = priceObj["price"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val outOfStock = priceObj["outOfStock"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val updatedAt = priceObj["updatedAt"]?.jsonPrimitive?.content
            if (price != null && !outOfStock) {
                prices.add(DataGouvPrice(gasName, price, updatedAt, outOfStock))
            }
        }

        return GasApiStation(
            id = id,
            name = name,
            address = fullAddress.ifBlank { address ?: "" },
            latitude = lat,
            longitude = lng,
            brand = brandName,
            prices = prices
        )
    }
}

/**
 * Station as returned by the Gas API (gas-api.ovh).
 */
@Serializable
data class GasApiStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val prices: List<DataGouvPrice> = emptyList()
)
