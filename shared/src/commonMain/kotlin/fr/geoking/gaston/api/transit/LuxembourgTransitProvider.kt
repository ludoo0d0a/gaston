package fr.geoking.gaston.api.transit

import fr.geoking.gaston.transit.TransitDeparture
import fr.geoking.gaston.transit.TransitMode
import fr.geoking.gaston.transit.TransitProvider
import fr.geoking.gaston.transit.TransitRegion
import fr.geoking.gaston.transit.TransitStop
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Transit provider for Luxembourg using mobiliteit.lu (HAFAS) API.
 * Requires API key: request from opendata-api@atp.etat.lu.
 * Base: https://cdt.hafas.de/opendata/apiserver/
 */
class LuxembourgTransitProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://cdt.hafas.de/opendata/apiserver"
) : TransitProvider {

    override val id: String = "lu_mobiliteit"
    override val region: TransitRegion = TransitRegion.Luxembourg

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getStopsNearby(lat: Double, lon: Double, radiusMeters: Int): List<TransitStop> {
        if (apiKey.isBlank()) return emptyList()
        return runCatching {
            val response = client.get("$baseUrl/location.nearbystops") {
                parameter("accessId", apiKey)
                parameter("originCoordLat", lat)
                parameter("originCoordLong", lon)
                parameter("r", radiusMeters.coerceIn(50, 2000))
                parameter("maxNo", 50)
                parameter("format", "json")
            }
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            parseNearbyStops(body)
        }.getOrElse { emptyList() }
    }

    override suspend fun getDepartures(stopId: String): List<TransitDeparture> {
        if (apiKey.isBlank()) return emptyList()
        return runCatching {
            val response = client.get("$baseUrl/departureBoard") {
                parameter("accessId", apiKey)
                parameter("id", stopId)
                parameter("lang", "en")
                parameter("format", "json")
            }
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            parseDepartureBoard(body, stopId)
        }.getOrElse { emptyList() }
    }

    private fun parseNearbyStops(body: String): List<TransitStop> {
        val root = json.parseToJsonElement(body).jsonObject
        val locationList = root["LocationList"] ?: return emptyList()
        val arr = locationList.jsonArray
        return arr.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: obj["extId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: obj["coordLat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: obj["coordLong"]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (lat == null || lon == null) return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.content ?: ""
            val mode = when {
                type.contains("BUS", ignoreCase = true) -> TransitMode.Bus
                type.contains("TRAM", ignoreCase = true) -> TransitMode.Tram
                type.contains("TRAIN", ignoreCase = true) -> TransitMode.Train
                else -> TransitMode.Other
            }
            TransitStop(
                id = id,
                name = name,
                latitude = lat,
                longitude = lon,
                mode = mode,
                providerId = null
            )
        }
    }

    private fun parseDepartureBoard(body: String, stopId: String): List<TransitDeparture> {
        val root = json.parseToJsonElement(body).jsonObject
        val departureList = root["Departure"] ?: root["departure"] ?: return emptyList()
        val arr = departureList.jsonArray
        return arr.mapNotNull { el ->
            val obj = el.jsonObject
            val lineName = obj["name"]?.jsonPrimitive?.content ?: obj["line"]?.jsonPrimitive?.content ?: ""
            val direction = obj["direction"]?.jsonPrimitive?.content ?: obj["dir"]?.jsonPrimitive?.content ?: ""
            val time = obj["time"]?.jsonPrimitive?.content ?: obj["rtTime"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val date = obj["date"]?.jsonPrimitive?.content ?: ""
            val scheduledTime = if (date.isNotEmpty()) "${date}T$time" else time
            TransitDeparture(
                stopId = stopId,
                lineId = lineName,
                lineName = lineName,
                direction = direction,
                scheduledTime = scheduledTime,
                realtimeTime = obj["rtTime"]?.jsonPrimitive?.content?.let { if (date.isNotEmpty()) "${date}T$it" else it },
                delayMinutes = null,
                mode = TransitMode.Bus,
                providerId = id
            )
        }
    }
}
