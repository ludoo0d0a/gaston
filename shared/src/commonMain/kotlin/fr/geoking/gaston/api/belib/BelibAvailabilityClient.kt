package fr.geoking.gaston.api.belib

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.*

/** Safely get results as a list; API may return results as array or single object. */
private fun resultsAsList(obj: JsonObject): List<JsonElement> {
    val results = obj["results"] ?: return emptyList()
    return when (results) {
        is JsonArray -> results
        is JsonObject -> listOf(results)
        else -> emptyList()
    }
}

/**
 * Client for Belib' real-time availability (Paris Data Opendatasoft).
 * Dataset: belib-points-de-recharge-pour-vehicules-electriques-disponibilite-temps-reel
 * No API key required.
 */
class BelibAvailabilityClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://parisdata.opendatasoft.com/api/explore/v2.1/catalog/datasets/belib-points-de-recharge-pour-vehicules-electriques-disponibilite-temps-reel"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches Belib' PDC availability within [radiusKm] of (latitude, longitude).
     * Uses bounding-box query then filters by haversine distance; returns up to [limit] records.
     */
    suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 10,
        limit: Int = 200
    ): List<BelibPdcRecord> {
        val deltaLat = radiusKm / 111.0
        val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
        val latLo = latitude - deltaLat
        val latHi = latitude + deltaLat
        val lngLo = longitude - deltaLng
        val lngHi = longitude + deltaLng
        // Paris Data uses coordonneesxy.lat and coordonneesxy.lon (lat=latitude, lon=longitude)
        val where = "coordonneesxy.lat > $latLo and coordonneesxy.lat < $latHi and coordonneesxy.lon > $lngLo and coordonneesxy.lon < $lngHi"
        val encodedWhere = where.encodeURLParameter()
        val url = "$baseUrl/records?where=$encodedWhere&limit=${limit.coerceAtMost(500)}"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Belib API error: $body")
        }
        val raw = parseRecords(body)
        return raw
            .mapNotNull { record ->
                val dist = haversineKm(latitude, longitude, record.latitude, record.longitude)
                if (dist <= radiusKm) record to dist else null
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    internal fun parseRecords(body: String): List<BelibPdcRecord> {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject
        val results = resultsAsList(obj)
        val list = mutableListOf<BelibPdcRecord>()
        for (item in results) {
            val record = item as? JsonObject ?: continue
            parseRecord(record)?.let { list.add(it) }
        }
        return list
    }

    private fun parseRecord(record: JsonObject): BelibPdcRecord? {
        val id = record["id_pdc"]?.jsonPrimitive?.content?.trim() ?: return null
        val statut = record["statut_pdc"]?.jsonPrimitive?.content?.trim().orEmpty()
        val coord = record["coordonneesxy"]?.jsonObject ?: return null
        // Paris Data: lat = latitude, lon = longitude
        val lat = coord["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lon = coord["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val address = record["adresse_station"]?.jsonPrimitive?.content?.trim()
        return BelibPdcRecord(
            idPdc = id,
            statutPdc = statut,
            latitude = lat,
            longitude = lon,
            adresseStation = address
        )
    }
}

/** Single Belib' PDC record from Paris Data API. */
data class BelibPdcRecord(
    val idPdc: String,
    val statutPdc: String,
    val latitude: Double,
    val longitude: Double,
    val adresseStation: String? = null
)
