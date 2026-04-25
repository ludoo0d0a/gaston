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
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Transit provider for Brussels (STIB-MIVB) using Open Data API.
 * No API key. Stops from GTFS dataset; real-time waiting times from waiting-time-rt.
 * https://data.stib-mivb.brussels/api/explore/v2.1/
 */
class BelgiumTransitProvider(
    private val client: HttpClient,
    private val baseUrl: String = "https://data.stib-mivb.brussels/api/explore/v2.1"
) : TransitProvider {

    override val id: String = "be_stib"
    override val region: TransitRegion = TransitRegion.Belgium

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getStopsNearby(lat: Double, lon: Double, radiusMeters: Int): List<TransitStop> {
        val radiusKm = (radiusMeters / 1000.0).coerceIn(0.1, 5.0)
        val where = "within_distance(stop_coordinates, geom'POINT($lon $lat)', ${radiusKm}km)"
        val encodedWhere = where.encodeURLParameter()
        return runCatching {
            val response = client.get("$baseUrl/catalog/datasets/gtfs-stops-production/records") {
                parameter("where", encodedWhere)
                parameter("limit", 50)
            }
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            parseStops(body)
        }.getOrElse { emptyList() }
    }

    override suspend fun getDepartures(stopId: String): List<TransitDeparture> {
        return runCatching {
            val response = client.get("$baseUrl/catalog/datasets/waiting-time-rt-production/records") {
                parameter("where", "pointid = '$stopId'")
                parameter("limit", 20)
            }
            val body = response.bodyAsText()
            if (response.status.value != 200) return emptyList()
            parseWaitingTimes(body, stopId)
        }.getOrElse { emptyList() }
    }

    private fun parseStops(body: String): List<TransitStop> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val obj = el.jsonObject
            val stopId = obj["stop_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["stop_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val coords = obj["stop_coordinates"]?.jsonObject ?: return@mapNotNull null
            val lat = coords["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = coords["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            TransitStop(
                id = stopId,
                name = name,
                latitude = lat,
                longitude = lon,
                mode = TransitMode.Other,
                providerId = null
            )
        }
    }

    private fun parseWaitingTimes(body: String, stopId: String): List<TransitDeparture> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()
        val list = mutableListOf<TransitDeparture>()
        for (el in results) {
            val obj = el.jsonObject
            val lineId = obj["lineid"]?.jsonPrimitive?.content ?: continue
            val passingTimesStr = obj["passingtimes"]?.jsonPrimitive?.content ?: continue
            val passingArr = runCatching { json.parseToJsonElement(passingTimesStr).jsonArray }.getOrNull() ?: continue
            for (pt in passingArr) {
                val ptObj = pt.jsonObject
                val expectedTime = ptObj["expectedArrivalTime"]?.jsonPrimitive?.content ?: continue
                val dest = ptObj["destination"]?.jsonObject
                val direction = dest?.get("fr")?.jsonPrimitive?.content ?: dest?.get("nl")?.jsonPrimitive?.content ?: ""
                list.add(
                    TransitDeparture(
                        stopId = stopId,
                        lineId = lineId,
                        lineName = lineId,
                        direction = direction,
                        scheduledTime = expectedTime,
                        realtimeTime = expectedTime,
                        delayMinutes = null,
                        mode = TransitMode.Metro,
                        providerId = null
                    )
                )
            }
        }
        return list
    }
}
