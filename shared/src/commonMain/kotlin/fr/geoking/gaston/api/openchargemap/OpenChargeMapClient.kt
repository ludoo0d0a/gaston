package fr.geoking.gaston.api.openchargemap

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for [Open Charge Map](https://openchargemap.org) API (EV charging stations).
 * Data is open (CC BY 4.0); attribution required.
 * API may require an API key for regular use; pass [apiKey] if you have one.
 * See https://openchargemap.org/site/develop
 */
class OpenChargeMapClient(
    private val client: HttpClient,
    private val apiKey: String? = null,
    private val baseUrl: String = "https://api.openchargemap.io/v3/poi"
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStations(
        latitude: Double,
        longitude: Double,
        distanceKm: Int = 10,
        maxResults: Int = 50
    ): List<OpenChargeMapPoi> {
        val url = URLBuilder(baseUrl).apply {
            parameters.append("latitude", latitude.toString())
            parameters.append("longitude", longitude.toString())
            parameters.append("distance", distanceKm.toString())
            parameters.append("distanceunit", "km")
            parameters.append("maxresults", maxResults.coerceIn(1, 100).toString())
            parameters.append("compact", "false")
            parameters.append("verbose", "false")
            apiKey?.takeIf { it.isNotBlank() }?.let { parameters.append("key", it) }
        }.buildString()
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "OpenChargeMap API error: $body")
        }
        val element = json.parseToJsonElement(body)
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { parsePoi(it as? JsonObject) }
    }

    private fun parsePoi(obj: JsonObject?): OpenChargeMapPoi? {
        if (obj == null) return null
        val id = obj["ID"]?.jsonPrimitive?.content ?: return null
        val addr = obj["AddressInfo"]?.jsonObject ?: return null
        val lat = addr["Latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lon = addr["Longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val title = addr["Title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        val addressLine1 = addr["AddressLine1"]?.jsonPrimitive?.content?.trim()
        val town = addr["Town"]?.jsonPrimitive?.content?.trim()
        val postcode = addr["Postcode"]?.jsonPrimitive?.content?.trim()
        val address = listOfNotNull(addressLine1, town, postcode).joinToString(", ").ifBlank { "—" }
        val name = title ?: address.take(50)
        val connections = obj["Connections"]?.jsonArray ?: emptyList()
        val connectorTypes = connections.mapNotNull { (it as? JsonObject)?.let { conn ->
            conn["ConnectionType"]?.jsonObject?.let { ct ->
                ct["Title"]?.jsonPrimitive?.content?.let { mapOcmConnectorToId(it) }
            }
        } }.toSet()
        val maxKw = connections.mapNotNull { (it as? JsonObject)?.let { c ->
            c["PowerKW"]?.jsonPrimitive?.content?.toDoubleOrNull()
        } }.maxOrNull()
        val operatorInfo = obj["OperatorInfo"]?.jsonObject
        val operator = operatorInfo?.get("Title")?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        return OpenChargeMapPoi(
            id = "ocm-$id",
            name = name,
            address = address,
            latitude = lat,
            longitude = lon,
            operator = operator,
            powerKw = maxKw,
            connectorTypes = connectorTypes
        )
    }

    /** Map OCM ConnectionType title to our connector id set. */
    private fun mapOcmConnectorToId(title: String): String? {
        val t = title.lowercase()
        return when {
            t.contains("type 2") -> "type_2"
            t.contains("ccs") || t.contains("combo") -> "combo_ccs"
            t.contains("chademo") -> "chademo"
            t.contains("type 1") || t.contains("ef") || t.contains("e/f") -> "ef"
            else -> "autre"
        }
    }
}

data class OpenChargeMapPoi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val operator: String?,
    val powerKw: Double?,
    val connectorTypes: Set<String>
)
