package fr.geoking.gaston.api.transit

import fr.geoking.gaston.transit.TransitDeparture
import fr.geoking.gaston.transit.TransitMode
import fr.geoking.gaston.transit.TransitProvider
import fr.geoking.gaston.transit.TransitRegion
import fr.geoking.gaston.transit.TransitStop
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Transit provider for Paris area using RATP API (Pierre Grimaud).
 * No API key. Covers metro, RER, tramways, bus.
 * See https://api-ratp.pierre-grimaud.fr/v4/
 */
class FranceTransitProvider(
    private val client: HttpClient,
    private val baseUrl: String = "https://api-ratp.pierre-grimaud.fr/v4"
) : TransitProvider {

    override val id: String = "fr_ratp"
    override val region: TransitRegion = TransitRegion.France

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getStopsNearby(lat: Double, lon: Double, radiusMeters: Int): List<TransitStop> =
        coroutineScope {
            val allStops = mutableListOf<TransitStop>()
            val types = listOf("metros" to TransitMode.Metro, "tramways" to TransitMode.Tram)
            for ((type, mode) in types) {
                val lines = fetchLines(type)
                val deferred = lines.map { lineCode ->
                    async {
                        fetchStationsForLine(type, lineCode, mode)
                    }
                }
                val stations = deferred.awaitAll().flatten()
                allStops.addAll(stations)
            }
            filterByDistance(allStops, lat, lon, radiusMeters)
        }

    override suspend fun getDepartures(stopId: String): List<TransitDeparture> {
        val parts = stopId.split("|")
        if (parts.size < 4) return emptyList()
        val type = parts[0]
        val lineCode = parts[1]
        val stationSlug = parts[2]
        val way = parts.getOrNull(3) ?: "A"
        return fetchSchedules(type, lineCode, stationSlug, way)
    }

    private suspend fun fetchLines(type: String): List<String> {
        return runCatching {
            val response = client.get("$baseUrl/lines/$type")
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val result = root["result"] ?: return emptyList()
            val arr = result.jsonArray
            arr.mapNotNull { it.jsonObject["code"]?.jsonPrimitive?.content }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchStationsForLine(type: String, lineCode: String, mode: TransitMode): List<TransitStop> {
        return runCatching {
            val response = client.get("$baseUrl/lines/$type/$lineCode/stations")
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val result = root["result"] ?: return emptyList()
            val arr = result.jsonArray
            arr.mapNotNull { el ->
                val obj = el.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val slug = obj["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val lat = obj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                val lon = obj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                if (lat == null || lon == null) return@mapNotNull null
                val stopId = "$type|$lineCode|$slug|A"
                TransitStop(
                    id = stopId,
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    mode = mode,
                    providerId = id
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun filterByDistance(stops: List<TransitStop>, lat: Double, lon: Double, radiusMeters: Int): List<TransitStop> {
        val r = radiusMeters / 1000.0
        return stops.filter { stop ->
            haversineKm(lat, lon, stop.latitude, stop.longitude) <= r
        }
    }

    private suspend fun fetchSchedules(type: String, lineCode: String, stationSlug: String, way: String): List<TransitDeparture> {
        return runCatching {
            val response = client.get("$baseUrl/schedules/$type/$lineCode/$stationSlug/$way")
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val result = root["result"] ?: return emptyList()
            val schedules = result.jsonArray
            val stopId = "$type|$lineCode|$stationSlug|$way"
            val lineName = when (type) {
                "metros" -> "M$lineCode"
                "tramways" -> "T$lineCode"
                "rers" -> "RER $lineCode"
                else -> lineCode
            }
            schedules.mapNotNull { el ->
                val obj = el.jsonObject
                val message = obj["message"]?.jsonPrimitive?.content ?: return@mapNotNull null
                TransitDeparture(
                    stopId = stopId,
                    lineId = lineCode,
                    lineName = lineName,
                    direction = obj["destination"]?.jsonPrimitive?.content ?: "",
                    scheduledTime = message,
                    realtimeTime = message,
                    delayMinutes = null,
                    mode = when (type) {
                        "metros" -> TransitMode.Metro
                        "tramways" -> TransitMode.Tram
                        "rers" -> TransitMode.Train
                        else -> TransitMode.Other
                    },
                    providerId = id
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val toRad = PI / 180.0
        val dLat = (lat2 - lat1) * toRad
        val dLon = (lon2 - lon1) * toRad
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * toRad) * cos(lat2 * toRad) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
