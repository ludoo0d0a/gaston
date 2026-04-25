package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Client for camping-car aires (and similar) from Opendatasoft-style APIs linked from data.gouv.fr.
 * Example: [Hérault Data - Aires de Camping car](https://www.herault-data.fr/explore/dataset/aires-de-camping-car/).
 * No API key. Licence: Licence Ouverte 2.0 (Etalab).
 */
class DataGouvCampingClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://www.herault-data.fr/api/explore/v2.1/catalog/datasets/aires-de-camping-car"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches aires (camping-car spots) within [radiusKm] of (latitude, longitude).
     * Uses bbox when the API supports it; otherwise fetches and filters by distance.
     */
    suspend fun getAires(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 15,
        limit: Int = 50
    ): List<DataGouvCampingRecord> {
        val url = "$baseUrl/records?limit=${limit.coerceIn(1, 200)}"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "DataGouvCamping API error: ${body.take(500)}")
        }
        val records = parseRecords(body)
        return records
            .mapNotNull { r ->
                val dist = haversineKm(latitude, longitude, r.latitude, r.longitude)
                if (dist <= radiusKm) r to dist else null
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun parseRecords(body: String): List<DataGouvCampingRecord> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"] ?: return emptyList()
        val list = when (results) {
            is JsonArray -> results
            is JsonObject -> listOf(results)
            else -> return emptyList()
        }
        return list.mapNotNull { item ->
            val record = item as? JsonObject ?: return@mapNotNull null
            parseRecord(record)
        }
    }

    private fun parseRecord(record: JsonObject): DataGouvCampingRecord? {
        val geopoint = record["geopoint"]?.jsonObject
            ?: record["coordonnees_geographiques"]?.jsonObject
            ?: record["geom"]?.jsonObject
        val lat = geopoint?.get("lat")?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: record["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: record["consolidated_latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val lon = geopoint?.get("lon")?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: record["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: record["consolidated_longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (lat == null || lon == null) return null
        val name = record["titre"]?.jsonPrimitive?.content?.trim()
            ?: record["name"]?.jsonPrimitive?.content?.trim()
            ?: record["nom"]?.jsonPrimitive?.content?.trim()
            ?: "Aire camping-car"
        val address = record["adresse"]?.jsonPrimitive?.content?.trim()
            ?: record["address"]?.jsonPrimitive?.content?.trim()
        val cp = record["code_postal"]?.jsonPrimitive?.content?.trim()
        val commune = record["commune"]?.jsonPrimitive?.content?.trim()
        val addressLine = listOfNotNull(address, cp, commune).filter { it.isNotBlank() }.joinToString(", ")
        val id = record["recordid"]?.jsonPrimitive?.content
            ?: record["id"]?.jsonPrimitive?.content
            ?: "dgouv:$lat:$lon:${name.hashCode()}"
        return DataGouvCampingRecord(
            id = id,
            name = name,
            address = addressLine.ifBlank { commune ?: "" },
            latitude = lat,
            longitude = lon,
            typeAire = record["type_daire"]?.jsonPrimitive?.content?.trim()
        )
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

data class DataGouvCampingRecord(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val typeAire: String? = null
)
